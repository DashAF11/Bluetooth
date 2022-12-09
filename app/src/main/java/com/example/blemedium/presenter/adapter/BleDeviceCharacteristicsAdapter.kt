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
import com.example.blemedium.databinding.ListItemBleCharacteristicsLayoutBinding
import com.example.blemedium.utils.drawableEnd
import com.example.blemedium.utils.gone
import com.example.blemedium.utils.setSafeOnClickListener
import com.example.blemedium.utils.visible
import kotlinx.coroutines.runBlocking
import java.util.*

class BleDeviceCharacteristicsAdapter(
    var context: Context,
    private var operationListener: BleCharacteristicPropertyAdapter.PropertyListener
) :
    RecyclerView.Adapter<BleDeviceCharacteristicsAdapter.ViewHolder>() {

    private lateinit var bleServiceData: BleServiceData
    private lateinit var bleCharacteristicPropertyAdapter: BleCharacteristicPropertyAdapter
    private lateinit var bleCharacteristicDescriptorAdapter: BleCharacteristicDescriptorAdapter
    private var propertyValue = ""
    private var propertyPosition = 0
    private var charUUID: UUID? = null

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

    fun setCharacteristicValue(value: String, position: Int, uuID: UUID) {
        runBlocking {
            propertyValue = value
            propertyPosition = position
            charUUID = uuID
            Log.d("setCharacteristicValue", "$propertyValue | $propertyPosition | $charUUID")
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

                bleCharacteristicPropertyAdapter =
                    BleCharacteristicPropertyAdapter(operationListener)
                rvProperty.adapter = bleCharacteristicPropertyAdapter
                rvProperty.layoutManager =
                    LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                bleCharacteristicPropertyAdapter.setProperty(entity, adapterPosition)


                bleCharacteristicDescriptorAdapter =
                    BleCharacteristicDescriptorAdapter()
                rvDescriptor.adapter = bleCharacteristicDescriptorAdapter
                rvDescriptor.layoutManager =
                    LinearLayoutManager(context)

                bleCharacteristicDescriptorAdapter.setDescriptor(entity, adapterPosition)

                tvDescriptor.setSafeOnClickListener {
                    if (rvDescriptor.visibility == View.VISIBLE) {
                        rvDescriptor.gone()
                        tvDescriptor.drawableEnd(R.drawable.ic_arrow_down)
                    } else {
                        rvDescriptor.visible()
                        tvDescriptor.drawableEnd(R.drawable.ic_arrow_up)
                    }
                }

            }
        }
    }
}