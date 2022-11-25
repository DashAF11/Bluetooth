package com.example.blemedium.blemodule

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.util.Log
import java.util.*

const val CCCD_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805F9B34FB"

fun BluetoothGatt.printGattTable(): List<BleServiceData> {
    val serviceList = arrayListOf<BleServiceData>()
    val characteristicsList = arrayListOf<BleCharacteristicsData>()
    val descriptorList = arrayListOf<BleDescriptorData>()

    if (services.isNotEmpty()) {

        services.forEach { service ->
            val characteristicsTable =
                service.characteristics.joinToString(separator = "\n|--", prefix = "|--") { char ->
                    var description = "${char.uuid}: ${char.printProperties()}"
                    Log.d("description ", description)

                    if (char.descriptors.isNotEmpty()) {
                        char.descriptors.forEach { descriptor ->
                            descriptorList.add(
                                BleDescriptorData(
                                    descriptor.uuid,
                                    descriptor.printProperties()
                                )
                            )
                        }
                    }

                    characteristicsList.add(
                        BleCharacteristicsData(
                            char.uuid,
                            char.printProperties(),
                            char.properties,
                            char.permissions,
                            descriptorList
                        )
                    )

                    if (char.descriptors.isNotEmpty()) {
                        description += "\n" + char.descriptors.joinToString(
                            separator = "\n|------", prefix = "|------"
                        ) { descriptor ->
                            "${descriptor.uuid}: ${descriptor.printProperties()}"
                            descriptorList.add(
                                BleDescriptorData(
                                    descriptor.uuid,
                                    descriptor.printProperties()
                                )
                            )
                            Log.d("description_other", description).toString()
                        }
                    }
                    description

                }
            Log.d("BLEGattService ", "${service.uuid}\nCharacteristics:\n$characteristicsTable")
            serviceList.add(BleServiceData(service.uuid, characteristicsList))
        }
    } else {
        Log.d(
            "BluetoothGatt",
            "No service and characteristic available, call discoverServices() first?"
        )
    }
    return serviceList
}

fun BluetoothGattCharacteristic.printProperties(): String = mutableListOf<String>().apply {
    if (isReadable()) add("READABLE")
    if (isWritable()) add("WRITABLE")
    if (isWritableWithoutResponse()) add("WRITABLE WITHOUT RESPONSE")
    if (isIndicatable()) add("INDICATABLE")
    if (isNotifiable()) add("NOTIFIABLE")
    if (isEmpty()) add("EMPTY")
}.joinToString()

fun BluetoothGattCharacteristic.isReadable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

fun BluetoothGattCharacteristic.isWritable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

fun BluetoothGattCharacteristic.isWritableWithoutResponse(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

fun BluetoothGattCharacteristic.isIndicatable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_INDICATE)

fun BluetoothGattCharacteristic.isNotifiable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_NOTIFY)

fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean =
    properties and property != 0

fun BluetoothGattDescriptor.printProperties(): String = mutableListOf<String>().apply {
    if (isReadable()) add("READABLE")
    if (isWritable()) add("WRITABLE")
    if (isEmpty()) add("EMPTY")
}.joinToString()

fun BluetoothGattDescriptor.isReadable(): Boolean =
    containsPermission(BluetoothGattDescriptor.PERMISSION_READ)

fun BluetoothGattDescriptor.isWritable(): Boolean =
    containsPermission(BluetoothGattDescriptor.PERMISSION_WRITE)

fun BluetoothGattDescriptor.containsPermission(permission: Int): Boolean =
    permissions and permission != 0

fun BluetoothGattDescriptor.isCccd() =
    uuid.toString().uppercase(Locale.US) == CCCD_DESCRIPTOR_UUID.uppercase(Locale.US)

fun ByteArray.toHexString(): String =
    joinToString(separator = " ", prefix = "0x") { String.format("%02X", it) }