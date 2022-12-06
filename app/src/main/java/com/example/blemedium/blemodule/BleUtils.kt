package com.example.blemedium.blemodule

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.util.Log
import com.example.blemedium.utils.Constants.Companion.PROPERTY_INDICATE
import com.example.blemedium.utils.Constants.Companion.PROPERTY_NOTIFY
import com.example.blemedium.utils.Constants.Companion.PROPERTY_READ
import com.example.blemedium.utils.Constants.Companion.PROPERTY_WITHOUT_RESPONSE
import com.example.blemedium.utils.Constants.Companion.PROPERTY_WRITE
import com.example.blemedium.utils.convertToList
import java.util.*

/** UUID of the Client Characteristic Configuration Descriptor (0x2902). */
const val CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805F9B34FB"

fun BluetoothGatt.printGattTable(): List<BleServiceData> {

    val serviceList = arrayListOf<BleServiceData>()
    val characteristicsList = arrayListOf<BleCharacteristicsData>()
    val descriptorList = arrayListOf<BleDescriptorData>()

    if (services.isNotEmpty()) {

        services.forEach { service ->
            val characteristicsTable =
                service.characteristics.joinToString(separator = "\n|--", prefix = "|--") { char ->
                    var description = "${char.uuid}: ${char.printProperties()}"
                    //    Log.d("description ", description)

                    if (char.descriptors.isNotEmpty()) {
                        description += "\n" + char.descriptors.joinToString(
                            separator = "\n|------", prefix = "|------"
                        ) { descriptor ->
                            "${descriptor.uuid}: ${descriptor.printProperties()}"

                            Log.d("Descriptor_other", "${descriptor.uuid}").toString()
                        }
                    }
                    description

                }

            service.characteristics.forEach { characteristic ->

                if (characteristic.descriptors.isNotEmpty()) {
                    characteristic.descriptors.forEach { descriptor ->
                        val descriptorData = BleDescriptorData(
                            descriptor.uuid, descriptor.printProperties().convertToList(),
                            descriptor.permissions
                        )

                        if (!descriptorList.contains(descriptorData)) {
                            descriptorList.add(descriptorData)
                        }

                        Log.d(
                            "DescriptorCheck:",
                            " ${characteristic.uuid} => ${descriptor.uuid}  ${descriptor.permissions}  "
                        )
                    }
                }

                Log.d("printGattTable:", "${characteristic.uuid} => ${characteristic.properties}")

                characteristicsList.add(
                    BleCharacteristicsData(
                        characteristic.uuid,
                        characteristic.printProperties().convertToList(),
                        characteristic.properties,
                        characteristic.permissions, descriptorList
                    )
                )
            }

            Log.d("BLEGattService ", "${service.uuid}\nCharacteristics:\n$characteristicsTable")
            serviceList.add(BleServiceData(service.uuid, service.type, characteristicsList))
        }
    } else {
        Log.d(
            "BluetoothGatt",
            "No service and characteristic available, call discoverServices() first?"
        )
    }
    return serviceList
}


fun BluetoothGatt.findCharacteristic(uuid: UUID): BluetoothGattCharacteristic? {
    services?.forEach { service ->
        service.characteristics?.firstOrNull { characteristic ->
            characteristic.uuid == uuid
        }?.let { matchingCharacteristic ->
            return matchingCharacteristic
        }
    }
    return null
}

fun BluetoothGatt.findDescriptor(uuid: UUID): BluetoothGattDescriptor? {
    services?.forEach { service ->
        service.characteristics.forEach { characteristic ->
            characteristic.descriptors?.firstOrNull { descriptor ->
                descriptor.uuid == uuid
            }?.let { matchingDescriptor ->
                return matchingDescriptor
            }
        }
    }
    return null
}


//region Characteristics

fun BluetoothGattCharacteristic.printProperties(): String = mutableListOf<String>().apply {
    if (isReadable()) add(PROPERTY_READ)
    if (isWritable()) add(PROPERTY_WRITE)
    if (isWritableWithoutResponse()) add(PROPERTY_WITHOUT_RESPONSE)
    if (isIndicatable()) add(PROPERTY_INDICATE)
    if (isNotifiable()) add(PROPERTY_NOTIFY)
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

//endregion

//region Descriptor

fun BluetoothGattDescriptor.printProperties(): String = mutableListOf<String>().apply {
    if (isReadable()) add(PROPERTY_READ)
    if (isWritable()) add(PROPERTY_WRITE)
    if (isEmpty()) add("EMPTY")
}.joinToString()

fun BluetoothGattDescriptor.isReadable(): Boolean =
    containsPermission(BluetoothGattDescriptor.PERMISSION_READ)

fun BluetoothGattDescriptor.isWritable(): Boolean =
    containsPermission(BluetoothGattDescriptor.PERMISSION_WRITE)

fun BluetoothGattDescriptor.containsPermission(permission: Int): Boolean =
    permissions and permission != 0

fun BluetoothGattDescriptor.isCccd() =
    uuid.toString().uppercase(Locale.US) == CCC_DESCRIPTOR_UUID.uppercase(Locale.US)

//endregion

fun ByteArray.toHexString(): String =
    joinToString(separator = " ", prefix = "0x") { String.format("%02X", it) }
