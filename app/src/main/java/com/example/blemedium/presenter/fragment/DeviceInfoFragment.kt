package com.example.blemedium.presenter.fragment

import android.R.attr
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.BluetoothDevice.*
import android.bluetooth.BluetoothGatt.GATT_READ_NOT_PERMITTED
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.blemedium.blemodule.*
import com.example.blemedium.databinding.FragmentDeviceInfoBinding
import com.example.blemedium.presenter.adapter.BleCharacteristicPropertyAdapter
import com.example.blemedium.presenter.adapter.BleDeviceServiceAdapter
import com.example.blemedium.utils.setSafeOnClickListener
import com.example.blemedium.utils.toast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.lang.String
import java.util.*
import javax.inject.Inject

@SuppressLint("MissingPermission")
@AndroidEntryPoint
class DeviceInfoFragment : Fragment(),
    BleCharacteristicPropertyAdapter.PropertyListener {
    companion object {
        private const val TAG = "DeviceInfoFragment"
    }

    @Inject
    lateinit var bluetoothAdapter: BluetoothAdapter

    private var _binding: FragmentDeviceInfoBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private val args: DeviceInfoFragmentArgs by navArgs()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var bleDeviceData: BleDeviceData
    private lateinit var device: BluetoothDevice

    private lateinit var bluetoothGatt: BluetoothGatt
    private var discoverServicesRunnable: Runnable? = null

    private lateinit var bleDeviceServiceAdapter: BleDeviceServiceAdapter

    private var coroutineScope = CoroutineScope(Dispatchers.IO)

    private var isRetrying = false
    private var nrTries = 0
    private val MAX_TRIES = 3

    private var commandQueueBusy = false

    private var readQueueIndex = 0
    private var readQueueList = arrayListOf<BluetoothGattCharacteristic>()

    private var serviceUUID: UUID? = null
    private var characteristicUUID: UUID? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentDeviceInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (arguments != null) {
            bleDeviceData = args.deviceData
            device = bluetoothAdapter.getRemoteDevice(bleDeviceData.deviceAddress)
        }

        /**
         *Connecting to a device
        After you have found your device by scanning for it, you must connect to it by calling connectGatt().
        It returns a BluetoothGatt object that you will then use for all GATT related operations like reading and writing characteristics.

        The internal implementation of the first call is that Android calls the second one with the value TRANSPORT_AUTO for the transport parameter.
        If you want to connect over BLE this is not the right value. TRANSPORT_AUTO is for devices that support both BLE and classic Bluetooth.
        It means you have ‘no preference’ for the type of connection that is made and want Android to choose.
        It is totally unclear how Android chooses so this may lead to rather unpredictable results and many people have reported issues.
        That is why you should always use the second version and pass TRANSPORT_LE for the transport parameter:

        BluetoothGatt gatt = device.connectGatt(context, false, bluetoothGattCallback, TRANSPORT_LE);

        The second parameter is the autoconnect parameter and indicates whether you want to connect immediately or not.
        So using false means ‘connect immediately’ and Android will try to connect for 30 seconds on most phones and then it times out.
        When a connection times out, you will receive a connection update with status code 133.
        This is not the official error code for a connection timeout though . It is defined in the google source code as GATT_ERROR.
        You’ll get this error on other occasions as well unfortunately. Also keep in mind that you can issue only one connect at a time using false,
        because Android will cancel any other connect with value false, if there is one.

        The next parameter is the BluetoothGattCallback callback you want to use for this device.
        This is not the same callback as we used for scanning. This callback will be used for all device specific operations like reading and writing.
         */

        /**
         * Autoconnect = true
        If you set autoconnect to true, Android will connect whenever it sees the device and this call will never time out.
        So internally the stack will scan itself and when it sees the device it will connect to it. This is handy if you want to
        reconnect to a known device whenever it becomes available. In fact, this is the preferred way to reconnect to do so!
        You simply create a BluetoothDevice object and call connectGattwith the autoconnect set to true.

        BluetoothDevice device = bluetoothAdapter.getRemoteDevice("12:34:56:AA:BB:CC");
        BluetoothGatt gatt = device.connectGatt(context, true, bluetoothGattCallback, TRANSPORT_LE);

        Keep in mind that this only works if the device is in the Bluetooth cache or it is has been bonded before!
        toggling Bluetooth on/off will clear the cache so you must perform necessary checks before using autoconnect!
         */

        binding.apply {
            deviceData = bleDeviceData

            btnDeviceConnection.setSafeOnClickListener {
                /**
                When you call connectGatt the stack internally registers a new ‘client interface’ (clientIf).
                You may have noticed a line like this in the logcat:

                D/BluetoothGatt: connect() - device: B0:49:5F:01:20:XX, auto: false
                D/BluetoothGatt: registerApp()
                D/BluetoothGatt: registerApp() — UUID=0e47c0cf-ef13–4afb-9f54–8cf3e9e808d5
                D/BluetoothGatt: onClientRegistered() — status=0 clientIf=6

                it shows that client ‘6’ got registered after I called connectGatt.
                Android has a limit of 30 clients (as defined by the GATT_MAX_APPS constant in the stack’s source code)
                and if you reach it it will not connect to devices anymore and you’ll get an error!
                Strangely enough, directly after booting your phone will be already on 5 or 6 so I guess Android itself uses the first ones.

                So if you never call close() you will see this number go up every time you call connectGatt.
                When you call close(), the stack will unregister your callback, free up the client internally and you will see the number go down again.

                So remember that whatever you do, you always call close() after you get disconnected!
                 */

                /**
                 * Disconnecting on your request
                If you want to disconnect yourself you need to do the following:

                Call disconnect()
                Wait for the callback on onConnectionStateChange to come in
                Call close()
                Dispose the gatt object
                The disconnect() command will actually do the disconnect and will also update the internal connection state of the Bluetooth stack.
                It will then trigger a callback to onConnectionStateChange to notify you that the new state is now ‘disconnected’.

                The close() call will unregister your BluetoothGattCallback and free up the ‘client interface’ as we already discussed.

                Lastly disposing of the BluetoothGatt object will free up other resources related to the connection.
                 */

                /**
                 * Disconnecting ‘the wrong way’
                If you look at examples you can find on the Internet you’ll see that some people disconnect a bit differently.

                Sometimes you see this:

                Call disconnect()
                Call close() directly after
                This will work ‘more or less’. The device will disconnect, but you may never receive the callback with the ‘disconnected’ state.
                This is because disconnect() is asynchronous and close() unregisters the callback immediately! So by the time Android is ready to
                trigger the callback, it can’t call the callback because you already unregistered it!

                Sometimes people don’t call disconnect() and only call close(). This will ultimately disconnect the device
                but is not the right way since disconnect() is where the Android stack actually updates the state internally.
                It not only disconnects an active connection but will also cancel an autoconnect that is pending. So if you only call close(),
                any autoconnect that is pending may still lead to a new connection!
                 */

                /**
                 * Cancelling a connection attempt
                If you have called connectGatt() and want to cancel the connection attempt, you need to call disconnect().
                Since you are not connected yet, you will not get a callback on onConnectionStateChange!
                So wait a couple of milliseconds for disconnect() to finish and then call close() to clean up.

                When you cancel a connection successfully you’ll see this in your log:

                D/BluetoothGatt: cancelOpen() — device: CF:A9:BA:D9:62:9E

                You will probably never cancel a connection with autoconnect set to false. But it is very common to do
                this for a connection with autoconnect set to true. For example, you typically want to connect to devices
                when your app is in the foreground but when your apps goes to the background you may want to stop connecting
                and hence cancel the pending connections.
                 */

                if (bleDeviceData.isDeviceConnected) {
                    bluetoothGatt.disconnect()
                    requireActivity().toast("Disconnecting...")
                } else {
                    bluetoothGatt = device.connectGatt(context, false, gattCallback, TRANSPORT_LE)
                    bluetoothGatt.connect()

                    requireActivity().toast("Connecting...")
                }

                bleDeviceServiceAdapter = BleDeviceServiceAdapter(requireActivity())
                rvServicesCharacteristics.adapter = bleDeviceServiceAdapter
                rvServicesCharacteristics.layoutManager = LinearLayoutManager(requireActivity())
            }
        }
    }

    private val gattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {

        /**
         * Connection state changes
        After calling connectGatt() the stack will let you know the result on the onConnectionStateChange callback. This callback is called for every connection state change.
         */

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {

            /**
             * As you can see, the code only looks at the newState parameter and totally ignores the status parameter.
             * However, in many cases this code may get you a long way and is not entirely wrong!
             * When you are connected the next thing to do is indeed to call discoverServices().
             * And if you are disconnected you indeed need to call close() to release resources in the Android stack.
             * This is actually super important for making BLE work on Android so let’s discuss it immediately!
             */

            if (status == GATT_SUCCESS) {

                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.e(TAG, "onConnectionStateChange: STATE_CONNECTED")

                        binding.isDeviceConnected = true
                        bleDeviceData.isDeviceConnected = true

                        binding.isLoading = true
                        // requireActivity().toast("Connected")
                        /**
                         * Discovering services
                        Once you are connected to a device you must discover it’s services by calling discoverServices().
                        This will cause the stack to issues a series of low-level commands to retrieves the services,characteristics and descriptors.
                        This may take a bit, typically a full second or so depending on how many services,
                        characteristics and descriptors your device has. When Android is done it will call onServicesDiscovered.
                         */

                        gatt.discoverServices()

                        /**
                         * When service discovery is done, the first thing you must check is to see if there was an error:

                        // Check if the service discovery succeeded. If not disconnect
                        if (status == GATT_INTERNAL_ERROR) {
                        Log.e(TAG, "Service discovery failed");
                        disconnect();
                        return;
                        }

                        If there was an error (typically GATT_INTERNAL_ERROR which has value 129), you must disconnect
                        since there is no way that you’ll be able to do something meaningful. You can’t turn on notifications nor read/write characteristics.
                        So just disconnect and retry the connection.

                        If everything went fine you can the proceed to grab the list of services and do your own processing:

                        final List<BluetoothGattService> services = gatt.getServices();
                        Log.i(TAG, String.format(Locale.ENGLISH,"discovered %d services for '%s'", services.size(), getName()));
                        // Do additional processing of the services
                         */

                        /**
                         *  bond state
                        The last parameter also needs to be taken into account in onConnectionStateChange is the bond state.
                        As it is not passed as a parameter we need to get it like this:

                        val bondState: Int = device.bondState

                        The bond state can be BOND_NONE, BOND_BONDING or BOND_BONDED. Each of these states have an impact on how you should deal with becoming connected:

                        If BOND_NONE: no problem, you can call discoverServices()
                        If BOND_BONDING: bonding is in progress, don’t call discoverServices() since the stack is busy and this may cause a loss of connection
                        and discoverServices() will fail! After bonding is complete you should call discoverServices().

                        if BOND_BONDED: if you are on Android 8 or higher, you can call discoverServices() immediately but if not you may have to add a delay.
                        On Android 7 or lower, and if your device has the Service Changed Characteristic,
                        the Android stack is still busy handling it and calling discoverServices() without a delay would make it fail.

                        So you have to add a 1000–1500 ms delay. The exact delay time needed depends on the number of characteristics of your device.
                        Since at this point you don’t know yet if the device has the Service Changed Characteristic it is recommendable to simply always to a delay.
                         */
                        val bondState: Int = device.bondState
                        // Take action depending on the bond state
                        if (bondState == BOND_NONE || bondState == BOND_BONDED) {

                            // Connected to device, now proceed to discover it's services but delay a bit if needed
                            var delayWhenBonded = 0
                            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
                                delayWhenBonded = 1000
                            }

                            val delay = if (bondState === BOND_BONDED) delayWhenBonded else 0

                            discoverServicesRunnable = Runnable {
                                Log.d(
                                    TAG, String.format(
                                        Locale.ENGLISH,
                                        "discovering services of '%s' with delay of %d ms",
                                        "getName()",
                                        attr.delay
                                    )
                                )
                                val result = gatt.discoverServices()
                                if (!result) {
                                    Log.e(TAG, "discoverServices failed to start")
                                }
                                discoverServicesRunnable = null
                            }
                            handler.postDelayed(discoverServicesRunnable!!, delay.toLong())
                        } else if (bondState == BOND_BONDING) {
                            // Bonding process in progress, let it complete
                            Log.i(TAG, "waiting for bonding to complete");
                        }
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.e(TAG, "onConnectionStateChange: STATE_DISCONNECTED")
                        binding.isLoading = false
                        binding.isDeviceConnected = false
                        bleDeviceData.isDeviceConnected = false
                        gatt.close()
                        //    requireActivity().toast("Disconnected")
                    }

                    BluetoothProfile.STATE_CONNECTING -> {
                        binding.tvConnectionStatus.text = "Connecting..."
                    }

                    BluetoothProfile.STATE_DISCONNECTING -> {
                        binding.tvConnectionStatus.text = "Disconnecting..."
                    }
                }
            } else {
                /**
                 * This field is essentially an error code. If you receive GATT_SUCCESS it means the
                connection state change was the result of a successful operation like connecting
                but it could also be because you wanted to disconnect.

                In most cases you’ll want to handle an unexpected disconnect differently from a desired disconnect you issued yourself!

                If you receive any other value than GATT_SUCCESS, something went wrong and the status field will tell you the reason for it.
                Unfortunately very few error codes are exposed by the BluetoothGatt object.

                The most common one you’ll encounter in practice is status code 133 which is GATT_ERROR.
                That just means ‘something went wrong’….not very helpful.
                 */
                gatt.disconnect()
                Log.d(TAG, "onConnectionStateChange: GATT_FAILED")
                gatt.close()

                /**
                 * Dealing with errors
                Now that we dealt with successful operations we need to look at the errors. There are a bunch of cases that are actually
                very normal that will present themselves as ‘errors’:

                The device disconnected itself on purpose. For example, because all data has been transferred and there is nothing else to to.
                You will receive status 19 (GATT_CONN_TERMINATE_PEER_USER).

                The connection timed out and the device disconnected itself. In this case you’ll get a status 8 (GATT_CONN_TIMEOUT)

                There was an low-level error in the communication which led to the loss of the connection.
                Typically you would receive a status 133 (GATT_ERROR) or a more specific error code if you are lucky!

                The stack never managed to connect in the first place. In this case you will also receive a status 133 (GATT_ERROR)

                The connection was lost during service discovery or bonding. In this case you will want to investigate why this happened
                and perhaps retry the connection.
                The first two cases are totally normal and there is nothing else to do than call close() and perhaps do some internal
                cleanup like disposing of the BluetoothGatt object.
                 */

                /**
                 * Status 133 when connecting
                It is very common to see a status 133 when trying to connect to a device, especially while you are developing your code. The status 133 can have many causes and some of them you can control:

                Make sure you always call close() when there is a disconnection. If you don’t do this you’ll get a 133 for sure next time you try.
                Make sure you always use TRANSPORT_LE when calling connectGatt()

                Restart your phone if you see this while developing. You may have corrupted the stack by debugging and it is in a state
                where it doesn’t behave normal again. Restarting your phone may fix things.
                Make sure your device is advertising. The connectGatt with autoconnect set to false times out after 30 seconds and you will receive a 133.
                Change the batteries of your device. Devices typically start behaving erratically when they battery level is very low.
                If you have tried all of the above and still see status 133 you need to simply retry the connection! This is one of the Android bugs I never managed to understand or find a workaround for. For some reason, you sometimes get a 133 when connecting to a device but if you call close() and retry it works without a problem! I suspect there is an issue with the Android cache that causes all this and the close() call puts it back in a proper state. But I am really just guessing here…If anybody figures out how to solve this one, let me know!
                 */
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            with(gatt) {
                Log.e(TAG, "onServicesDiscovered_allAvailableServices: ${printGattTable()}")

                var readOnlyOnce = false

                requireActivity().runOnUiThread {
                    bleDeviceServiceAdapter.setCharacterListener(
                        this@DeviceInfoFragment
                    )
                    bleDeviceServiceAdapter.setServices(printGattTable())

                    gatt.services.forEach { services ->
                        run {
                            services.characteristics.forEach { characteristic ->
                                if (characteristic.isReadable() && characteristic.isNotifiable()) {
                                    if (!readOnlyOnce) {
                                        bluetoothGatt.readCharacteristic(characteristic)
                                        Log.d(
                                            TAG, "onServicesDiscovered: readCharacteristic=${
                                                bluetoothGatt.readCharacteristic(characteristic)
                                            }"
                                        )
                                        Log.d(
                                            TAG,
                                            "onServicesDiscoveredReading: ${services.uuid} : ${characteristic.uuid} = ${characteristic.value}"
                                        )
                                        readOnlyOnce = true
                                    }
                                }
                            }
                        }
                    }

                    binding.isLoading = false
                }

                gatt.requestMtu(517) //default its 20 bytes, max is 517
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {

            Log.d(TAG, "onMtuChanged: ")
            val characteristic = findCharacteristics(
                serviceUUID.toString(), characteristicUUID.toString()
            ) // to find it wee need chars UUID and service UUID
            if (characteristic != null) {
                enableNotification(characteristic)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, value, status)
            Log.e(TAG, "onCharacteristicRead: $value")

            with(characteristic) {
                when (status) {
                    GATT_SUCCESS -> {
                        Log.e(
                            TAG,
                            "onCharacteristicRead Read characteristic $uuid:\n${value.toHexString()}"
                        )
                        //completedCommand()
                    }
                    GATT_READ_NOT_PERMITTED -> {
                        Log.e(TAG, "onCharacteristicRead Read not permitted for $uuid!")
                    }
                    else -> {
                        Log.e(
                            TAG,
                            "onCharacteristicRead Characteristic read failed for $uuid, error: $status"
                        )
                    }
                }
            }

            Log.e(TAG, "onCharacteristicRead : ${Arrays.toString(characteristic.value)}")

            /* readQueueList.remove(readQueueList[readQueueIndex])
             if (readQueueList.size >= 0) {
                 readQueueIndex--
                 if (readQueueIndex === -1) {
                     Log.e(TAG, "Read Queue Complete ")
                 } else {
                     readCharacteristics()//readQueueIndex
                 }
             }*/
            //readCharacteristic(characteristic)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            Log.e(
                TAG, "onCharacteristicChanged: ${characteristic.value.toHexString()}"
            )
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            Log.d(TAG, "onCharacteristicWrite ${characteristic?.uuid} written")
        }
    }

    /**
     * Caching by the Android Bluetooth stack
    Scanning not only gives you a list of BLE devices, but it also allows the bluetooth stack to ‘cache’ devices.
    The stack will store important information about the devices that were found like the name, mac address, address type (public/random),
    device type (Classic, Dual, BLE) and so on. Android needs this information in order to connect successfully to a device.
    It caches all devices it sees in a scan and not only the one you intend to connect to. Per device, a small file is written
    with the information about each device. When you want to connect to a device the Android stack will look for this file,
    read the contents and use it to connect. The key message here is that a mac address alone is not enough to connect successfully to a device!
     */

    /**
     * Clearing the cache
    Like any cache, it doesn’t live forever and there are certain moments when the cache is being cleared. On Android there are
    at least 3 moments when the cache is cleared:

    When you switch Bluetooth off and back on
    When you reboot/start your phone
    When you manually clear your cache in your settings menu
    This is actually quite a pain for you as a developer. Rebooting a phone happens quite a lot and also turning Bluetooth on/off
    happens regularly for good reasons like putting your phone in flight-mode. On top of that, there are some differences per device manufacturer to note.
    On some Samsung phones I tried the cache was not cleared by toggling Bluetooth on off.

    This means that you cannot rely on device information being cached by Android. Luckily,
    it is possible to find out if your device is cached by Android or not! Suppose you try to get a BluetoothDevice object using
    it’s mac address because you want to reconnect to that device, you can then check the device type to see if it is DEVICE_TYPE_UNKNOWN.
    If so, it is not cached by Android.
     */

    /*private fun checkDeviceCache() {
        // Get device object for a mac address
        val device: BluetoothDevice =
            bluetoothAdapter.getRemoteDevice(bleDeviceData.deviceAddress)!!

        // Check if the peripheral is cached or not
        val deviceType = device.type
        if (deviceType == BluetoothDevice.DEVICE_TYPE_UNKNOWN) {
            // The peripheral is not cached
        } else {
            // The peripheral is cached
        }
    }*/

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }


//    private fun nextCommand() {
//        // If there is still a command being executed then bail out
//        if (commandQueueBusy) {
//            return
//        }
//
//        // Check if we still have a valid gatt object
//        if (bluetoothGatt == null) {
//            Log.e(
//                TAG,
//                String.format(
//                    "ERROR: GATT is 'null' for peripheral '%s', clearing command queue",
//                    "getAddress()"
//                )
//            )
//            commandQueue!!.clear()
//            commandQueueBusy = false
//            return
//        }
//
//        // Execute the next command in the queue
//        if (commandQueue!!.size > 0) {
//            val bluetoothCommand = commandQueue.peek()
//            commandQueueBusy = true
//            // nrTries = 0
//            handler.post {
//                try {
//                    if (bluetoothCommand != null) {
//                        bluetoothCommand.run()
//                    }
//                } catch (ex: Exception) {
//                    Log.e(
//                        TAG,
//                        String.format("ERROR: Command exception for device '%s'", " getName()"),
//                        ex
//                    )
//                }
//            }
//        }
//    }

//    private fun completedCommand() {
//        commandQueueBusy = false
//        isRetrying = false
//        commandQueue!!.poll()
//        nextCommand()
//    }

//    private fun retryCommand() {
//        commandQueueBusy = false
//        val currentCommand = commandQueue!!.peek()
//        if (currentCommand != null) {
//            if (nrTries >= MAX_TRIES) {
//                // Max retries reached, give up on this one and proceed
//                Log.v(TAG, "Max number of tries reached")
//                commandQueue.poll()
//            } else {
//                isRetrying = true
//            }
//        }
//        nextCommand()
//    }

    private fun readCharacteristics() { //index: Int
        bluetoothGatt.readCharacteristic(readQueueList[0])
    }

    private fun readCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
        Log.e(TAG, "readCharacteristicCharacteristicUUID: $characteristic")

        if (characteristic.isReadable()) {
            bluetoothGatt.readCharacteristic(characteristic)
        } else if (characteristic.isWritable()) {
            //  bluetoothGatt.writeCharacteristic(characteristic)
        }
        return bluetoothGatt.readCharacteristic(characteristic)
    }

    private fun findCharacteristics(
        serviceUUID: kotlin.String, characteristicUUID: kotlin.String
    ): BluetoothGattCharacteristic? {
        return bluetoothGatt.services?.find { service ->
            service.uuid.toString() == serviceUUID
        }?.characteristics?.find { characteristics ->
            characteristics.uuid.toString() == characteristicUUID
        }
    }

    private fun enableNotification(characteristic: BluetoothGattCharacteristic) {
        val cccdUUID = UUID.fromString(characteristic.uuid.toString())
        val payload = when {
            characteristic.isIndicatable() -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            characteristic.isNotifiable() -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else -> return
        }

        characteristic.getDescriptor(cccdUUID)?.let { cccdDescriptor ->
            if (!bluetoothGatt.setCharacteristicNotification(characteristic, true)) {
                Log.d("BleReceiveManager: ", "setCharacteristicsNotiFailed")
                return
            }
            writeDescriptor(cccdDescriptor, payload)
        }
    }

    private fun writeDescriptor(descriptor: BluetoothGattDescriptor, payload: ByteArray) {
        bluetoothGatt.let { gatt ->
            descriptor.value = payload
            gatt.writeDescriptor(descriptor)
        }
    }

    override fun propertyRead(serviceData: BleServiceData, characterPosition: Int) {
        Log.d(TAG, "propertyRead: $serviceData")

        Log.d(TAG, "propertyRead: serviceDataUUID ${serviceData.serviceUUID}")
        Log.d(
            TAG,
            "propertyRead: CharacteristicUUID ${serviceData.characteristicsList[characterPosition].charUUID}"
        )
        //00002a19-0000-1000-8000-00805f9b34fb

        serviceUUID = serviceData.serviceUUID
        characteristicUUID = serviceData.characteristicsList[characterPosition].charUUID

        val bluetoothLeService =
            BluetoothGattService(serviceData.serviceUUID, serviceData.serviceType)

        val bluetoothGattCharacteristic = BluetoothGattCharacteristic(
            serviceData.characteristicsList[characterPosition].charUUID,
            serviceData.characteristicsList[characterPosition].charPropertiesInt,
            serviceData.characteristicsList[characterPosition].charPermission
        )

        val batteryServiceUuid =
            UUID.fromString(serviceData.serviceUUID.toString()) //serviceData . serviceUUID  "00001801-0000-1000-8000-00805f9b34fb"
        val batteryLevelCharUuid =
            UUID.fromString(serviceData.characteristicsList[characterPosition].charUUID.toString()) //"0000180F-0000-1000-8000-00805f9b34fb"

        val batteryLevelChar =
            bluetoothGatt.getService(batteryServiceUuid)?.getCharacteristic(batteryLevelCharUuid)
        if (batteryLevelChar?.isReadable() == true) {
            bluetoothGatt.readCharacteristic(batteryLevelChar)
            Log.d(TAG, "propertyRead: ${bluetoothGatt.readCharacteristic(batteryLevelChar)}")
        } else {
            Log.d(TAG, "propertyRead: Else")
        }


        Log.d(
            TAG, "propertyRead: readCharacteristic ${
                readCharacteristic(bluetoothGattCharacteristic)
            }"
        )

        if (bluetoothAdapter == null || bluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized")
            return
        }


        //   bluetoothGatt.readCharacteristic(bluetoothGattCharacteristic)

        /*val batteryServiceUUID = UUID.fromString("00001801-0000-1000-8000-00805f9b34fb")
        val batteryCharacteristicUUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")

        val batteryService: BluetoothGattService = bluetoothGatt.getService(batteryServiceUUID)
        if (batteryService == null) {
            Log.d(TAG, "Battery service not found!")
            return
        }

        val batteryLevel = batteryService.getCharacteristic(batteryCharacteristicUUID)
        if (batteryLevel == null) {
            Log.d(TAG, "Battery level not found!")
            //return
        }
        bluetoothGatt.readCharacteristic(batteryLevel)
        Log.v(TAG, "batteryLevel = " + bluetoothGatt.readCharacteristic(batteryLevel))*/

        /*  val batteryServiceUuid = bluetoothLeService.uuid
          val batteryLevelCharUuid = bluetoothGattCharacteristic.uuid
          val normalChar =
              bluetoothGatt.getService(batteryServiceUuid)?.getCharacteristic(batteryLevelCharUuid)
  */
//        //readingCharacteristic
//        readQueueList.add(bluetoothGattCharacteristic)
//        readQueueIndex = readQueueList.size
//        readCharacteristics() //readQueueIndex

        // Enqueue the read command now that all checks have been passed
        val commandQueue: Queue<Runnable>? = null

//        val result: Boolean? = commandQueue?.add(Runnable {
//            if (!bluetoothGatt.readCharacteristic(bluetoothGattCharacteristic)) {
//                Log.e(
//                    TAG,
//                    String.format(
//                        "ERROR: readCharacteristic failed for characteristic: %s",
//                        bluetoothGattCharacteristic.uuid
//                    )
//                )
//              //  completedCommand()
//            } else {
//                Log.d(
//                    TAG,
//                    String.format("reading characteristic <%s>", bluetoothGattCharacteristic.uuid)
//                )
//                  nrTries++
//            }
//        })

//        Log.d(TAG, "characteristicClick: $result")
//
//        if (result == true) {
//         //   nextCommand()
//        } else {
//            Log.e(TAG, "ERROR: Could not enqueue read characteristic command");
//        }

        /*  if (normalChar?.isReadable() != true) {
              //    bluetoothGatt.readCharacteristic(batteryLevelChar)

              Log.d(
                  TAG,
                  "readingCharacteristic: ${
                      bluetoothGatt.readCharacteristic(
                          bluetoothGattCharacteristic
                      )
                  }"
              )
          }*/
    }

    override fun propertyWrite(serviceData: BleServiceData, characterPosition: Int) {
        Log.d(TAG, "characteristicWrite: $serviceData")
    }

    override fun propertyNotify(serviceData: BleServiceData, characterPosition: Int) {
        Log.d(TAG, "characteristicNotify: $serviceData")
    }

    override fun propertyIndicate(serviceData: BleServiceData, characterPosition: Int) {
        Log.d(TAG, "propertyIndicate: $serviceData")
    }

    override fun propertyWithoutResponse(serviceData: BleServiceData, characterPosition: Int) {
        Log.d(TAG, "propertyWriteNotResponse: $serviceData")
    }

    override fun propertyUnknown(serviceData: BleServiceData, characterPosition: Int) {
        Log.d(TAG, "propertyUnknown: $serviceData")
    }

}