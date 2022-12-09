package com.example.blemedium.blemodule

import android.bluetooth.BluetoothDevice
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class BleDeviceData(
    var deviceName: String = "",
    var deviceAddress: String = "",
    var isDeviceConnected: Boolean = false,
    var device: BluetoothDevice
) : Parcelable
