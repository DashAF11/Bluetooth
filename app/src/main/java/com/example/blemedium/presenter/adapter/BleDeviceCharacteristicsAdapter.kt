package com.example.blemedium.presenter.adapter

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.blemedium.blemodule.BleCharacteristicsData
import com.example.blemedium.databinding.ListItemBleCharacteristicsLayoutBinding
import kotlinx.coroutines.runBlocking

class BleDeviceCharacteristicsAdapter :
    RecyclerView.Adapter<BleDeviceCharacteristicsAdapter.ViewHolder>() {

    private var bleCharacteristicsList: MutableList<BleCharacteristicsData> = mutableListOf()

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
    fun setCharacteristics(characteristicsList: List<BleCharacteristicsData>) {
        runBlocking {
            bleCharacteristicsList = characteristicsList as MutableList<BleCharacteristicsData>
            Log.e("bleCharacteristicsList", "${bleCharacteristicsList.size}")
            notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int = bleCharacteristicsList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(bleCharacteristicsList[position])
    }

    inner class ViewHolder(private val binding: ListItemBleCharacteristicsLayoutBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(entity: BleCharacteristicsData) {

            Log.d("BleDeviceCharacteristicsAdapter: ", "$entity")

            binding.apply {
                bleServiceData = entity

            }
        }
    }
}