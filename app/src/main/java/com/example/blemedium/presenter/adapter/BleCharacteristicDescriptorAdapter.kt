package com.example.blemedium.presenter.adapter

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.blemedium.blemodule.BleServiceData
import com.example.blemedium.databinding.ListItemBleDescriptorLayoutBinding
import kotlinx.coroutines.runBlocking

class BleCharacteristicDescriptorAdapter :
    RecyclerView.Adapter<BleCharacteristicDescriptorAdapter.ViewHolder>() {

    private lateinit var serviceData: BleServiceData
    private var characteristicPosition = 0

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding =
            ListItemBleDescriptorLayoutBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        return ViewHolder(binding)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setDescriptor(service: BleServiceData, adapterPosition: Int) {
        runBlocking {
            serviceData = service
            characteristicPosition = adapterPosition
            Log.e("setDescriptor", "$serviceData")
            notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int =
        serviceData.characteristicsList[characteristicPosition].charDescriptor.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(serviceData)
    }

    inner class ViewHolder(private val binding: ListItemBleDescriptorLayoutBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(entity: BleServiceData) {

            binding.apply {

                descriptorData =
                    entity.characteristicsList[characteristicPosition].charDescriptor[adapterPosition]
            }
        }
    }
}