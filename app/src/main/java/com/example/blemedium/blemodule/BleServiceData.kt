package com.example.blemedium.blemodule

import java.util.*

data class BleServiceData(
    var serviceUUID: UUID,
    var characteristicsList: List<BleCharacteristicsData>
)

data class BleCharacteristicsData(
    var charUUID: UUID,
    var charProperties: String
)
