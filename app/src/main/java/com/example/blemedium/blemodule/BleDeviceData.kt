package com.example.blemedium.blemodule

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class BleDeviceData(
    var deviceName: String = "",
    var deviceAddress: String = "",
    var isDeviceConnected: Boolean = false
) : Parcelable