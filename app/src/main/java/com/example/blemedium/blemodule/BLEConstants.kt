package com.example.blemedium.blemodule

class BLEConstants {

    companion object {
        const val ACTION_GATT_CONNECTED =
            "ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED =
            "ACTION_GATT_DISCONNECTED"
        const val ACTION_GATT_SERVICES_DISCOVERED =
            "ACTION_GATT_SERVICES_DISCOVERED"
        const val ACTION_DATA_AVAILABLE = "ACTION_DATA_AVAILABLE"
        const val ACTION_DATA_WRITTEN = "ACTION_DATA_WRITTEN"
        const val EXTRA_DATA = "EXTRA_DATA"
        const val EXTRA_UUID = "EXTRA_UUID"

        const val ACCESS_COARSE_LOCATION_REQUEST = 100
        const val REQUEST_ENABLE_BT = 101
        const val REQUEST_LOCATION_PERMISSION = 102

        const val GATT_MIN_MTU_SIZE = 23

        /** Maximum BLE MTU size as defined in gatt_api.h. */
        const val GATT_MAX_MTU_SIZE = 517
    }
}