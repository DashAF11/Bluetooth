package com.example.blemedium.presenter.fragment

import android.R.attr
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.BluetoothDevice.*
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
import com.example.blemedium.blemodule.BleCharacteristicsData
import com.example.blemedium.blemodule.BleDeviceData
import com.example.blemedium.blemodule.printGattTable
import com.example.blemedium.databinding.FragmentDeviceInfoBinding
import com.example.blemedium.presenter.adapter.BleDeviceCharacteristicsAdapter
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
class DeviceInfoFragment : Fragment(), BleDeviceCharacteristicsAdapter.CharacteristicsListener {
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

    private lateinit var gatt: BluetoothGatt
    private var discoverServicesRunnable: Runnable? = null

    private lateinit var bleDeviceServiceAdapter: BleDeviceServiceAdapter

    private var uuid = ""

    private var coroutineScope = CoroutineScope(Dispatchers.IO)

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
                    gatt.disconnect()
                    requireActivity().toast("Disconnecting...")
                } else {
                    gatt = device.connectGatt(context, false, gattCallback, TRANSPORT_LE)
                    gatt.connect()

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
                        Log.d(TAG, "onConnectionStateChange: STATE_CONNECTED")

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
                        Log.d(TAG, "onConnectionStateChange: STATE_DISCONNECTED")
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
            // Log.d(TAG, "onServicesDiscovered: ${gatt.services[0].characteristics}")

            /*      gatt.services.forEach { service ->
                      service.characteristics.forEach { characteristic ->
                          Log.d(
                              TAG, "findCharacteristics: ${
                                  findCharacteristics(
                                      service.uuid.toString(), characteristic.uuid.toString()
                                  ).toString()
                              }"
                          )
                      }
                  }

                  val serviceList: List<BluetoothGattService> = gatt.services
                  Log.d(TAG, "gattServices: ${
                      serviceList.forEach {
                          it.characteristics
                      }
                  }")*/

            /*   if (serviceList != null) {
                   for (gattService in serviceList) {

                       //if (gattService.uuid.toString().equals(mContext.getString(R.string.char_uuid_emergency), ignoreCase = true)) {
                       val gattCharacteristics = gattService.characteristics
                       for (gattCharacteristic in gattCharacteristics) {

                           uuid =
                               if (gattCharacteristic.uuid != null) gattCharacteristic.uuid.toString() else ""

                           //   if (uuid.equals(mContext.resources.getString(R.string.char_uuid_emergency_call), ignoreCase = true)) {
                           var newChar = gattCharacteristic

                           newChar = setProperties(newChar)
                           Log.d(TAG, "newChar: $newChar")

   //                        mDataBLEForEmergency = newChar
                           // }
                       }
                       //    }
                   }
               }*/

            with(gatt) {
                Log.d(TAG, "onServicesDiscovered_allAvailableServices: ${printGattTable()}")

                requireActivity().runOnUiThread {
                    bleDeviceServiceAdapter.setCharacterListener(this@DeviceInfoFragment)
                    bleDeviceServiceAdapter.setServices(printGattTable())
                    binding.isLoading = false
                }

                gatt.requestMtu(517) //default its 20 bytes, max is 517
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            /*val characteristic =
                findCharacteristics(
                    "adsad",
                    "adsasd"
                ) // to find it wee need chars UUID and service UUID
            if (characteristic == null) {
                coroutineScope.launch {
                    sWatchData.emit(Resource.Error(errorMessage = "Could not find data!"))

                }
                return
            }
            enableNotification(characteristic)*/
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, value, status)
            Log.d(TAG, "onCharacteristicRead: ${gatt.readCharacteristic(characteristic)}")
            //readCharacteristic(characteristic)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {

            Log.d(TAG, "onCharacteristicChanged: $characteristic")
            // readCharacteristic(characteristic)
//            with(characteristic) {
//                when (uuid) {
//                    UUID.fromString("sada") -> {
//                        val multiplicator = if (value.first().toInt() > 0) -1 else 1
//                    }
//                    else -> Unit
//                }
//            }
        }
    }

//    fun readCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
//        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ == 0) {
//            return false
//        }
//        if (VDBG) Log.d(TAG, "readCharacteristic() - uuid: " + characteristic.uuid)
//        if (mService == null || mClientIf === 0) return false
//        val service = characteristic.service ?: return false
//        val device: BluetoothDevice = service.getDevice() ?: return false
//        synchronized(mDeviceBusy) {
//            if (mDeviceBusy) return false
//            mDeviceBusy = true
//        }
//        try {
//            mService.readCharacteristic(
//                mClientIf, device.address,
//                characteristic.instanceId, AUTHENTICATION_NONE
//            )
//        } catch (e: RemoteException) {
//            Log.e(TAG, "", e)
//            mDeviceBusy = false
//            return false
//        }
//        return true
//    }

    // Request a read of a given BluetoothGattCharacteristic. The Read result is reported asynchronously through the
    // BluetoothGattCallback onCharacteristicRead callback method.
    // For information only. This application uses Indication to receive updated characteristic data, not Read
    fun readCharacteristic(characteristic: BluetoothGattCharacteristic) {
        if (bluetoothAdapter == null || gatt == null) {                      //Check that we have access to a Bluetooth radio
            return
        }
        val status =
            gatt.readCharacteristic(characteristic)                         //Request the BluetoothGatt to Read the characteristic
        Log.i(TAG, "READ STATUS $status")
    }

    private fun setProperties(gattCharacteristic: BluetoothGattCharacteristic): BluetoothGattCharacteristic {
        val characteristicProperties = gattCharacteristic.properties

        if (characteristicProperties and BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0) {
            setCharacteristicNotification(gattCharacteristic, true)
        }

        if (characteristicProperties and BluetoothGattCharacteristic.PROPERTY_INDICATE > 0) {
            setCharacteristicIndication(gattCharacteristic, true)
        }

        if (characteristicProperties and BluetoothGattCharacteristic.PROPERTY_WRITE > 0) {
            gattCharacteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }

        if (characteristicProperties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE > 0) {
            gattCharacteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        if (characteristicProperties and BluetoothGattCharacteristic.PROPERTY_READ > 0) {
            gattCharacteristic.writeType = BluetoothGattCharacteristic.PROPERTY_READ
        }
        return gattCharacteristic
    }

    // Enable indication on a characteristic
    private fun setCharacteristicIndication(
        characteristic: BluetoothGattCharacteristic, enabled: Boolean
    ) {
        try {
            if (bluetoothAdapter == null || gatt == null) { //Check that we have a GATT connection
                Log.i(TAG, "BluetoothAdapter not initialized")
                return
            }

            //Enable notification and indication for the characteristic
            gatt.setCharacteristicNotification(characteristic, enabled)

            for (des in characteristic.descriptors) {
                if (null != des) {
                    des.value =
                        BluetoothGattDescriptor.ENABLE_INDICATION_VALUE//Set the value of the descriptor to enable indication
                    gatt.writeDescriptor(des)
                    Log.i(
                        TAG, "***********************SET CHARACTERISTIC INDICATION SUCCESS**"
                    )//Write the descriptor
                }
            }
        } catch (e: Exception) {
            Log.i(TAG, e.message.toString())
        }

    }

    // Enable notification on a characteristic
    // For information only. This application uses Indication, not Notification
    private fun setCharacteristicNotification(
        characteristic: BluetoothGattCharacteristic, enabled: Boolean
    ) {
        try {
            //Check that we have a GATT connection
            if (bluetoothAdapter == null || gatt == null) {
                Log.i(TAG, "BluetoothAdapter not initialized")
                return
            }

            //Enable notification and indication for the characteristic
            gatt.setCharacteristicNotification(
                characteristic, enabled
            )
            for (des in characteristic.descriptors) {
                if (null != des) {
                    //Set the value of the descriptor to enable notification
                    des.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(des)
                }

            }
        } catch (e: Exception) {
            Log.i(TAG, e.message.toString())
        }
    }

    private fun findCharacteristics(
        serviceUUID: kotlin.String, characteristicUUID: kotlin.String
    ): BluetoothGattCharacteristic? {
        return gatt.services?.find { service ->
            service.uuid.toString() == serviceUUID
        }?.characteristics?.find { characteristics ->
            characteristics.uuid.toString() == characteristicUUID
        }
    }

//    fun readCharacteristic(characteristic: BluetoothGattCharacteristic?): Boolean {
//        if (bluetoothGatt == null) {
//            Log.e(TAG, "ERROR: Gatt is 'null', ignoring read request")
//            return false
//        }
//
//        // Check if characteristic is valid
//        if (characteristic == null) {
//            Log.e(TAG, "ERROR: Characteristic is 'null', ignoring read request")
//            return false
//        }
//
//        // Check if this characteristic actually has READ property
//        if (characteristic.properties and PROPERTY_READ == 0) {
//            Log.e(TAG, "ERROR: Characteristic cannot be read")
//            return false
//        }
//
//        // Enqueue the read command now that all checks have been passed
//        val result: Boolean = commandQueue.add(Runnable {
//            if (!bluetoothGatt.readCharacteristic(characteristic)) {
//                Log.e(
//                    TAG,
//                    kotlin.String.format(
//                        "ERROR: readCharacteristic failed for characteristic: %s",
//                        characteristic.uuid
//                    )
//                )
//                completedCommand()
//            } else {
//                Log.d(TAG, kotlin.String.format("reading characteristic <%s>", characteristic.uuid))
//                nrTries++
//            }
//        })
//        if (result) {
//            nextCommand()
//        } else {
//            Log.e(TAG, "ERROR: Could not enqueue read characteristic command")
//        }
//        return result
//    }

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

    override fun characteristicClick(characteristic: BleCharacteristicsData) {
        Log.d(TAG, "characteristicClick: $characteristic")
        val bluetoothGattCharacteristic = BluetoothGattCharacteristic(
            characteristic.charUUID, characteristic.charPropertiesInt, characteristic.charPermission
        )
        gatt.readCharacteristic(bluetoothGattCharacteristic)
    }
}