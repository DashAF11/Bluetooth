package com.example.blemedium.presenter.adapter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.blemedium.blemodule.BleServiceData
import com.example.blemedium.databinding.ListItemBleCharacteristicsLayoutBinding
import kotlinx.coroutines.runBlocking

class BleDeviceCharacteristicsAdapter(
    var context: Context,
    private var operationListener: BleCharacteristicPropertyAdapter.PropertyListener
) :
    RecyclerView.Adapter<BleDeviceCharacteristicsAdapter.ViewHolder>() {

    private lateinit var bleServiceData: BleServiceData
    private lateinit var bleDeviceOperationsAdapter: BleCharacteristicPropertyAdapter

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding =
            ListItemBleCharacteristicsLayoutBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        return ViewHolder(binding)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setCharacteristics(characteristicsList: BleServiceData) {
        runBlocking {
            bleServiceData = characteristicsList
            Log.e("setCharacteristics", "${bleServiceData.characteristicsList.size}")
            notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int = bleServiceData.characteristicsList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(bleServiceData)
    }

    inner class ViewHolder(private val binding: ListItemBleCharacteristicsLayoutBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(entity: BleServiceData) {

            Log.d("BleDeviceCharacteristicsAdapter: ", "$entity")

            binding.apply {
                bleServiceData = entity.characteristicsList[adapterPosition]

                bleDeviceOperationsAdapter =
                    BleCharacteristicPropertyAdapter(operationListener)
                rvOperation.adapter = bleDeviceOperationsAdapter
                rvOperation.layoutManager =
                    LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

                bleDeviceOperationsAdapter.setProperty(entity, adapterPosition)

                val bluetoothGattCharacteristic = BluetoothGattCharacteristic(
                    entity.characteristicsList[adapterPosition].charUUID,
                    entity.characteristicsList[adapterPosition].charPropertiesInt,
                    entity.characteristicsList[adapterPosition].charPermission
                )


            }
        }
    }

    interface CharacteristicsListener {
        fun characteristicRead(serviceData: BleServiceData, adapterPosition: Int)
        fun characteristicWrite(serviceData: BleServiceData, adapterPosition: Int)
        fun characteristicNotify(serviceData: BleServiceData, adapterPosition: Int)
    }
}