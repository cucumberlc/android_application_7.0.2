/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Network

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.daimajia.swipe.SwipeLayout
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.Subnet
import com.siliconlabs.bluetoothmesh.App.Views.SwipeBaseAdapter
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.ItemSubnetBinding

class SubnetsAdapter_nlc(private val itemListener: ItemListener<Subnet>) : SwipeBaseAdapter<Subnet>() {

    override fun generateView(position: Int, parent: ViewGroup): View {
        return LayoutInflater.from(parent.context).inflate(R.layout.item_subnet_nlc, parent, false)
    }
//    override fun fillValues(position: Int, convertView: View) {
//        val subnet = getItem(position)
//        val binding = ItemSubnetBinding.bind(convertView)
//        binding.apply {
//            val nodesCount = subnet.nodes.size
//            val appKeysCount = subnet.appKeys.size
//            textViewSubnetDevices.text = convertView.resources.getQuantityString(R.plurals.subnets_adapter_devices_label,
//                    nodesCount, nodesCount)
//            textViewSubnetAppKeys.text = convertView.resources.getQuantityString(R.plurals.subnets_adapter_appkeys_label,
//                    appKeysCount, appKeysCount)
//            textViewNetKeyIndex.text = subnet.netKey.index.toString()
//
//
//            imageViewRemove.setOnClickListener {
//                itemListener.onDeleteClick(subnet)
//            }
//
//            swipe.apply {
//                surfaceView.setOnLongClickListener {
//                    this.open()
//                    return@setOnLongClickListener true
//                }
//                surfaceView.setOnClickListener {
//                    itemListener.onItemClick(subnet)
//                }
//                showMode = SwipeLayout.ShowMode.LayDown
//                addDrag(SwipeLayout.DragEdge.Right, swipeMenu)
//            }
//        }
//    }

    override fun fillValues(position: Int, convertView: View) {
        val subnet = getItem(position)
        val binding = ItemSubnetBinding.bind(convertView)

        binding.apply {
            val nodesCount = subnet.nodes.size
            val appKeysCount = subnet.appKeys.size
            textViewSubnetDevices.text = convertView.resources.getQuantityString(
                R.plurals.subnets_adapter_devices_label,
                nodesCount,
                nodesCount
            )
            textViewSubnetAppKeys.text = convertView.resources.getQuantityString(
                R.plurals.subnets_adapter_appkeys_label,
                appKeysCount,
                appKeysCount
            )
            textViewNetKeyIndex.text = subnet.netKey.index.toString()
            val hasDevices = nodesCount > 0
//            convertView.visibility = if (hasDevices) View.VISIBLE else View.GONE
            // Only item click action
            convertView.setOnClickListener {
                itemListener.onItemClick(subnet)
            }
            if (hasDevices) {
                convertView.setOnClickListener {
                    itemListener.onItemClick(subnet)
                }
            } else {
                // If no devices, you may choose to set a different action or disable click
                convertView.setOnClickListener(null)
                /*swipe.apply {
                    surfaceView.setBackgroundColor(Color.TRANSPARENT)
                }*/
//                swipe.setBackgroundColor(Color.TRANSPARENT)
                // Additional actions for when there are no devices
            }
        }
    }
}
