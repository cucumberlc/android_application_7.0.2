/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Common.Adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.siliconlabs.bluetoothmesh.App.Fragments.Common.Adapters.ScannedDevicesAdapter.ScannedDevicesViewHolder
import com.siliconlabs.bluetoothmesh.App.Models.UnprovisionedDevice
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.ItemScannedDeviceBinding

class ScannedDevicesAdapter : ListAdapter<UnprovisionedDevice, ScannedDevicesViewHolder>(ScannedDevicesDiffCallback) {
    var onScannedDeviceClick: ((UnprovisionedDevice) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScannedDevicesViewHolder {
        return ScannedDevicesViewHolder.from(parent)
    }

    override fun onBindViewHolder(holder: ScannedDevicesViewHolder, position: Int) {
        holder.bind(getItem(position), onScannedDeviceClick)
    }

    fun clearAdapter() {
        submitList(emptyList())
    }

    class ScannedDevicesViewHolder private constructor(private val binding: ItemScannedDeviceBinding) : ViewHolder(binding.root) {
        fun bind(unprovisionedDevice: UnprovisionedDevice,
                 onScannedDeviceClick: ((UnprovisionedDevice) -> Unit)?) {
            binding.apply {
                tvDeviceName.text = unprovisionedDevice.name
                tvDeviceRssi.text = if (unprovisionedDevice.rssi != null)
                    binding.root.context.getString(R.string.scanner_adapter_rssi)
                            .format(unprovisionedDevice.rssi)
                else
                    "Unknown rssi"
                tvDeviceUuid.text = binding.root.context.getString(R.string.scanner_adapter_uuid)
                        .format(unprovisionedDevice.uuid.toString())


                layoutUnprovisionedDevice.setOnClickListener {
                    onScannedDeviceClick?.invoke(unprovisionedDevice)
                }
            }
        }

        companion object {
            fun from(parent: ViewGroup): ScannedDevicesViewHolder {
                val binding = ItemScannedDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return ScannedDevicesViewHolder(binding)
            }
        }
    }

    private object ScannedDevicesDiffCallback : DiffUtil.ItemCallback<UnprovisionedDevice>() {
        override fun areItemsTheSame(oldItem: UnprovisionedDevice, newItem: UnprovisionedDevice): Boolean {
            return oldItem.uuid == newItem.uuid
        }

        override fun areContentsTheSame(oldItem: UnprovisionedDevice, newItem: UnprovisionedDevice): Boolean {
            return oldItem == newItem
        }
    }
}