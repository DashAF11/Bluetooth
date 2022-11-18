package com.example.blemedium.presenter.adapter

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.blemedium.blemodule.BleDeviceData
import com.example.blemedium.databinding.ListItemBleDeviceLayoutBinding

class BleDeviceAdapter(private val connectDeviceListener: ConnectDeviceListener) :
    RecyclerView.Adapter<BleDeviceAdapter.ViewHolder>() {
    private var bleDeviceList: MutableList<BleDeviceData> = mutableListOf()
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding =
            ListItemBleDeviceLayoutBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        return ViewHolder(binding)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setBleDevices(events: List<BleDeviceData>) {
        this.bleDeviceList = events as MutableList<BleDeviceData>
        Log.e("bleDeviceList", "$bleDeviceList")
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = bleDeviceList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(bleDeviceList[position])
    }

    inner class ViewHolder(private val binding: ListItemBleDeviceLayoutBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(entity: BleDeviceData) {

            binding.apply {
                bleDeviceData = entity

                itemView.setOnClickListener {
                    connectDeviceListener.connectDevice(entity)
                }
            }
        }
    }

    interface ConnectDeviceListener {
        fun connectDevice(deviceData: BleDeviceData)
    }
}