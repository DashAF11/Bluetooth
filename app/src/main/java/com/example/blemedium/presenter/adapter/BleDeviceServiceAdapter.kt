package com.example.blemedium.presenter.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.blemedium.R
import com.example.blemedium.blemodule.BleServiceData
import com.example.blemedium.databinding.ListItemBleServiceLayoutBinding
import com.example.blemedium.utils.drawableEnd
import com.example.blemedium.utils.setSafeOnClickListener
import kotlinx.coroutines.runBlocking

class BleDeviceServiceAdapter(var context: Context) :
    RecyclerView.Adapter<BleDeviceServiceAdapter.ViewHolder>() {

    private var bleServicesList: MutableList<BleServiceData> = mutableListOf()
    private lateinit var bleDeviceCharacteristicsAdapter: BleDeviceCharacteristicsAdapter
    private lateinit var characteristicsListener: BleDeviceCharacteristicsAdapter.CharacteristicsListener
    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ): ViewHolder {
        val binding = ListItemBleServiceLayoutBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setServices(serviceList: List<BleServiceData>) {
        runBlocking {
            bleServicesList = serviceList as MutableList<BleServiceData>
            Log.e("bleServicesList", "${bleServicesList.size}")
            notifyDataSetChanged()
        }
    }

    fun setCharacterListener(listener: BleDeviceCharacteristicsAdapter.CharacteristicsListener) {
        this.characteristicsListener = listener
    }

    override fun getItemCount(): Int = bleServicesList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(bleServicesList[position])
    }

    inner class ViewHolder(private val binding: ListItemBleServiceLayoutBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(entity: BleServiceData) {

            Log.d("BleDeviceCharacteristicsAdapter: ", "$entity")

            binding.apply {
                bleServiceData = entity

                bleDeviceCharacteristicsAdapter =
                    BleDeviceCharacteristicsAdapter(characteristicsListener)
                rvCharacteristics.adapter = bleDeviceCharacteristicsAdapter
                rvCharacteristics.layoutManager = LinearLayoutManager(context)

                bleDeviceCharacteristicsAdapter.setCharacteristics(entity.characteristicsList)

                tvServiceUUID.setSafeOnClickListener {
                    if (rvCharacteristics.visibility == View.VISIBLE) {
                        isUp = false
                        tvServiceUUID.drawableEnd(R.drawable.ic_arrow_down)
                    } else {
                        isUp = true
                        tvServiceUUID.drawableEnd(R.drawable.ic_arrow_up)
                    }
                }
            }
        }
    }
}