package com.example.blemedium.presenter.adapter

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.blemedium.blemodule.BleServiceData
import com.example.blemedium.databinding.ListItemBleOperationLayoutBinding
import com.example.blemedium.utils.Constants.Companion.PROPERTY_INDICATE
import com.example.blemedium.utils.Constants.Companion.PROPERTY_NOTIFY
import com.example.blemedium.utils.Constants.Companion.PROPERTY_READ
import com.example.blemedium.utils.Constants.Companion.PROPERTY_WITHOUT_RESPONSE
import com.example.blemedium.utils.Constants.Companion.PROPERTY_WRITE
import com.example.blemedium.utils.setSafeOnClickListener
import kotlinx.coroutines.runBlocking

class BleCharacteristicPropertyAdapter(val propertyListener: PropertyListener) :
    RecyclerView.Adapter<BleCharacteristicPropertyAdapter.ViewHolder>() {

    private lateinit var serviceData: BleServiceData
    private var characteristicPosition = 0

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding =
            ListItemBleOperationLayoutBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        return ViewHolder(binding)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setProperty(characteristicsList: BleServiceData, adapterPosition: Int) {
        runBlocking {
            serviceData = characteristicsList
            characteristicPosition = adapterPosition
            Log.e("setProperty", "$serviceData")
            notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int =
        serviceData.characteristicsList[characteristicPosition].charProperties.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(serviceData)
    }

    inner class ViewHolder(private val binding: ListItemBleOperationLayoutBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(entity: BleServiceData) {

            Log.d(
                "BleDeviceOperationsAdapter: ",
                entity.characteristicsList[characteristicPosition].charProperties[adapterPosition]
            )

            binding.apply {

                operationName =
                    when (entity.characteristicsList[characteristicPosition].charProperties[adapterPosition].trim()) {
                        PROPERTY_READ -> {
                            "R"
                        }
                        PROPERTY_WRITE -> {
                            "W"
                        }
                        PROPERTY_NOTIFY -> {
                            "N"
                        }
                        PROPERTY_INDICATE -> {
                            "I"
                        }
                        PROPERTY_WITHOUT_RESPONSE -> {
                            "WR"
                        }
                        else -> {
                            "U"
                        }
                    }

                tvProperty.setSafeOnClickListener {
                    when (entity.characteristicsList[characteristicPosition].charProperties[adapterPosition]) {
                        PROPERTY_READ -> {
                            propertyListener.propertyRead(entity)
                        }
                        PROPERTY_WRITE -> {
                            propertyListener.propertyWrite(entity)
                        }
                        PROPERTY_NOTIFY -> {
                            propertyListener.propertyNotify(entity)
                        }
                    }
                }

            }
        }
    }

    interface PropertyListener {
        fun propertyRead(serviceData: BleServiceData)
        fun propertyWrite(serviceData: BleServiceData)
        fun propertyNotify(serviceData: BleServiceData)
        fun propertyIndicate(serviceData: BleServiceData)
        fun propertyWithoutResponse(serviceData: BleServiceData)
        fun propertyUnknown(serviceData: BleServiceData)
    }
}