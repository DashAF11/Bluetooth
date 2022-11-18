package com.example.blemedium.presenter.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.blemedium.blemodule.BleDeviceData
import com.example.blemedium.databinding.FragmentScanningBinding
import com.example.blemedium.presenter.adapter.BleDeviceAdapter
import dagger.hilt.android.AndroidEntryPoint
import java.util.*
import javax.inject.Inject

@SuppressLint("MissingPermission")
@AndroidEntryPoint
class ScanningFragment : Fragment(), BleDeviceAdapter.ConnectDeviceListener {

    private var _binding: FragmentScanningBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentScanningBinding.inflate(inflater, container, false)
        return binding.root
    }

    companion object {
        private const val TAG = "ScanningFragment"
    }

    @Inject
    lateinit var bluetoothAdapter: BluetoothAdapter

    private var peripheralAddress = ""
    private var isBleDialogAlreadyShown = false

    private lateinit var bluetoothManager: BluetoothManager
    private var scanning = false
    private val handler = Handler(Looper.getMainLooper())

    private var bleDeviceDataList = arrayListOf<BleDeviceData>()
    private val bleDeviceAdapter = BleDeviceAdapter(this)
    private val filters: MutableList<ScanFilter?> = ArrayList()
    private var filterLetter = ""
    private var scanSettings: ScanSettings? = null

    private var connectedDevice: BleDeviceData? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

/*
        if (Build.VERSION.SDK_INT >= 23) {
            if (ContextCompat.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
            ) {
                val builder: AlertDialog.Builder = AlertDialog.Builder(requireActivity())
                builder.setTitle("This app needs location access")
                builder.setMessage("Please grant location access so this app can detect peripherals.")
                builder.setPositiveButton(android.R.string.ok, null)
                builder.setOnDismissListener {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        requestPermissions(
                            arrayOf(
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ), BLEConstants.ACCESS_COARSE_LOCATION_REQUEST
                        )
                    }
                }
                builder.show()
            }
        }*/

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestMultiplePermissions.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT
                )
            )
        } else {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            requestBluetooth.launch(enableBtIntent)
        }

        //region ScanSetting
        scanSettings = ScanSettings.Builder()
            /**
             *ScanModeInfo
             *This is by far the most important setting.
             *This controls when and how long the Bluetooth stack is actually searching for devices.
             *Since scanning is a very power consuming feature you definitively want to have some control
             *in order not to drain your phone’s battery too quickly.

             * There are 4 modes and this is what they mean according to Nordics guide:

            SCAN_MODE_LOW_POWER. In this mode, Android scans for 0.5 sec and then pauses 4.5 sec.
            If you choose this mode, it may take relatively long before a device is found,
            depending on how often a device sends an advertisement packet.
            But the good thing of this mode is that is uses very low power so it is ideal for long scanning times.

            SCAN_MODE_BALANCED. In this mode, Android scans for 2 sec and waits 3 seconds.
            This is the ‘compromise’ mode. Not sure how useful this is though…

            SCAN_MODE_LOW_LATENCY. In this mode, Android scans continuously.
            Obviously this uses the most power, but it will also guarantee the best scanning results.
            So if you want to find a device very quickly you must use this mode. But don’t use it for long running scans.

            SCAN_MODE_OPPORTUNISTIC. In this mode, you only get scan results if other apps are scanning! Basically,
            this means it is totally unpredictable when Android will find your device.
            It may even not find your device at all…Again, you probably never want to use this in your own apps.
            The Android stack uses this mode to ‘downgrade’ your scan if you scan too long. More about that later.
             *
             */
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            /**
             * CallbackTypeInfo
             * This setting allows you to control how many times Android will tell you about a device that matches the filters.
             * There are 3 possible settings:

            CALLBACK_TYPE_ALL_MATCHES. With this setting you will get a callback every time an advertisement packet that matches is received.
            So in practice, you will get callbacks every 200–500ms depending on how often your device advertises.

            CALLBACK_TYPE_FIRST_MATCH. With this setting you only get a callback once for device even though subsequent advertisements may be received.

            CALLBACK_TYPE_MATCH_LOST. This one may sound a bit odd but with this setting you get a callback when no more
            advertisement packets are found after a first packet was found.

            In practice you will typically use either CALLBACK_TYPE_ALL_MATCHES or CALLBACK_TYPE_FIRST_MATCH.
            There is no right one, it depends on your use case. If you don’t know what to choose take CALLBACK_TYPE_ALL_MATCHES
            since that will give you more control when receiving callbacks. If you stop the scan after receiving a result you are
            effectively mimicking CALLBACK_TYPE_FIRST_MATCH.
             */
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            /**
             * MatchModeInfo
             * This mode is about how Android will determine there is a ‘match’. Again there are several options to choose from:

            MATCH_MODE_AGGRESSIVE. Rather than talking about the number of advertisements,
            this mode tells Android to be ‘aggressive’. This comes down to very few advertisements and even reporting devices with feeble signal strength.

            MATCH_MODE_STICKY. This is the counterpart of ‘aggressive’ and tells Android to be ‘conservative’ and need more advertisements
            and higher signal strength.

            mainly use MATCH_MODE_AGGRESSIVE. This will help find devices quickly and I never experience connection issues.
             */
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            /**
             * NumberOfMatchesInfo
             * This controls how many advertisements are needed for a match.

            MATCH_NUM_ONE_ADVERTISEMENT. One advertisement is enough for a match
            MATCH_NUM_FEW_ADVERTISEMENT. A few advertisements are needed for a match
            MATCH_NUM_MAX_ADVERTISEMENT. The maximum number of advertisements the hardware can handle per timeframe is needed for a match.
             */
            .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT).setReportDelay(0L).build()
        //endregion

        binding.apply {
            btnScan.setOnClickListener {
                checkScanningDevice()
            }

            btnFilterApply.setOnClickListener {
                checkScanningDevice()
            }
        }
    }

    private val startBleIntentForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            isBleDialogAlreadyShown = false
            Log.d(TAG, "startBleIntentForResult: ${result.resultCode}")

            if (result.resultCode != Activity.RESULT_OK) {
                showBleDialog()
            }
        }

    private fun showBleDialog() {
        if (!bluetoothAdapter.isEnabled && !isBleDialogAlreadyShown) {
            val enableBleIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startBleIntentForResult.launch(enableBleIntent)
            isBleDialogAlreadyShown = true
        }
    }

    private val bluetoothBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action: String? = intent?.action
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                val state: Int? = intent?.getIntExtra(
                    BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR
                )
                when (state) {
                    BluetoothAdapter.STATE_OFF -> showBleDialog()
                    BluetoothAdapter.STATE_TURNING_OFF -> showBleDialog()
                }
            }
        }
    }

    private fun FragmentScanningBinding.checkScanningDevice() {
        isDeviceEmpty = false
        scanUIChange()
        scanLeDevice()
    }

    private fun FragmentScanningBinding.scanUIChange() {
        filterLetter = etFilterBy.text.toString().trim()

        sfLayout.startShimmer()
        sfLayout.visibility = View.VISIBLE

        btnScan.text = "Scanning..."
    }

    private var requestBluetooth =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                //granted
            } else {
                //deny
            }
        }

    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                Log.d("requestMultiplePermissions", "${it.key} = ${it.value}")
            }
        }

    /**
     * Report delay
    You can specify a delay in milliseconds. If the delay is >0, Android will collect all the scan results it finds
    and send them in a batch after the delay. So in this case you will not get a callback on onScanResult,
    but instead Android will call onBatchScanResults. The main use case here is a situation
    where you expect multiple devices of the same type and want to let the user choose the right one.
    The only issue there is what information you can supply the end user to make the right decision.
    It should be more than a mac address because otherwise the user still won’t know which device to choose!

    Note that there is a known bug on the Samsung S6 and Samsung S6 Edge where all scan results have the same RSSI value when you use a delay >0.
     */

    private val scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            // ...do whatever you want with this found device

            if (device.name != null) {
                Log.d(TAG, "onScanResult: ${device.name}")
                val bleDeviceData = BleDeviceData(device.name, device.address, false)

                if (bleDeviceData != null) {
                    if (!bleDeviceDataList.contains(bleDeviceData)) {
                        bleDeviceDataList.add(bleDeviceData)
                    }
                }
            }
        }

        override fun onBatchScanResults(results: List<ScanResult?>?) {
            // Ignore for now
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "onScanFailed: $errorCode")
        }
    }

    override fun onStart() {
        super.onStart()

        showBleDialog()

        /**
         * Intent Filter
         * Intent Filter is useful to determine which apps wants to receive
         * which intents,since here we want to respond to change of bluetooth state
         */

        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        try {
            requireActivity().registerReceiver(bluetoothBroadcastReceiver, filter)
        } catch (e: Exception) {
            Log.e(TAG, "bluetoothBroadcastReceiver: ${e.message}")
        }
    }

    private fun isLocationPermissionEnabled(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireActivity(), Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun scanLeDevice() {
        binding.root.hideKeyboard()

        //regionFilterByUUID //Scanning for devices with a specific service UUID
        /* if (serviceUUIDs != null) {
             filtersUUID = ArrayList()
             for (serviceUUID in serviceUUIDs) {
                 val filter = ScanFilter.Builder()
                     .setServiceUuid(ParcelUuid(serviceUUID))
                     .build()
                 (filtersUUID as ArrayList<ScanFilter?>).add(filter)
             }
         }*/
        //endregion

        //region FilterByMacAddress //Scanning by mac address

        /* val peripheralAddresses = arrayOf("CB:56:00:00:FD:E4")
         // Build filters list
         if (peripheralAddresses != null) {
             filters = ArrayList()
             for (address in peripheralAddresses) {
                 val filter = ScanFilter.Builder()
                     .setDeviceAddress(address)
                     .build()
                 filters.add(filter)
             }
         }
         scanner.startScan(filters, scanSettings, scanByServiceUUIDCallback)*/
        //endregion

        //region FilterByDeviceName //Scanning by device name
        val names = arrayOf(filterLetter) //XTENDPRO
        if (names != null) {
            for (name in names) {
                val filter = ScanFilter.Builder().setDeviceName(name).build()
                filters.add(filter)
            }
        }
        //endregion

        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
            binding.rvConnections.visibility = View.GONE

            if (!scanning) { // Stops scanning after a pre-defined scan period.
                handler.postDelayed({
                    binding.displayBleDeviceData()
                }, 15000)

                bleDeviceDataList.clear()
                scanning = true

                bluetoothAdapter.bluetoothLeScanner?.startScan(scanCallback)

                /* bluetoothAdapter.bluetoothLeScanner?.startScan(
                     filters,
                     scanSettings,
                     scanCallback
                 ) // with filters*/

                Log.d(TAG, "scan started")
            } else {
                binding.displayBleDeviceData()
            }
        } else {
            Log.e(TAG, "could not get scanner object")
        }
    }

    private fun FragmentScanningBinding.displayBleDeviceData() {
        Log.d(TAG, "scan stopped")

        scanning = false
        bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)

        sfLayout.stopShimmer()
        sfLayout.visibility = View.GONE
        rvConnections.visibility = View.VISIBLE

        Log.d(TAG, "bleDeviceDataList $bleDeviceDataList")

        rvConnections.adapter = bleDeviceAdapter
        rvConnections.layoutManager = LinearLayoutManager(requireActivity())
        bleDeviceAdapter.setBleDevices(bleDeviceDataList)
        btnScan.text = "Scan"

        isDeviceEmpty = bleDeviceDataList.size == 0
    }

    private fun View.hideKeyboard() {
        val inputMethodManager: InputMethodManager? =
            requireActivity().getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager?
        inputMethodManager?.hideSoftInputFromWindow(this.applicationWindowToken, 0)
    }

    /**
     * BleChangeReceiver
     * since BleChangeReceiver class holds a instance of Context and that context is actually the activity context in which
    the receiver has been created
     */
    override fun onStop() {
        requireActivity().unregisterReceiver(bluetoothBroadcastReceiver)
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    override fun connectDevice(deviceData: BleDeviceData) {
        Log.d(TAG, "connectDevice: $deviceData")

        connectedDevice = deviceData

        val action =
            ScanningFragmentDirections.actionScanningFragmentToDeviceInfoFragment(deviceData)
        findNavController().navigate(action)
    }
}