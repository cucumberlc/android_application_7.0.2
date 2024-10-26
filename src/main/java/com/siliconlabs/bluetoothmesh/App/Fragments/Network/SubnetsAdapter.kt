/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Network

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.daimajia.swipe.SwipeLayout
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.Subnet
import com.siliconlabs.bluetoothmesh.App.Views.SwipeBaseAdapter
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.ItemSubnetBinding

class SubnetsAdapter(private val itemListener: ItemListener<Subnet>) : SwipeBaseAdapter<Subnet>() {

    override fun generateView(position: Int, parent: ViewGroup): View {
        return LayoutInflater.from(parent.context).inflate(R.layout.item_subnet, parent, false)
    }

    override fun fillValues(position: Int, convertView: View) {
        val subnet = getItem(position)
        val binding = ItemSubnetBinding.bind(convertView)

        binding.apply {
            val nodesCount = subnet.nodes.size
            val appKeysCount = subnet.appKeys.size

            textViewSubnetDevices.text = convertView.resources.getQuantityString(R.plurals.subnets_adapter_devices_label,
                nodesCount, nodesCount)
            textViewSubnetAppKeys.text = convertView.resources.getQuantityString(R.plurals.subnets_adapter_appkeys_label,
                appKeysCount, appKeysCount)

            textViewNetKeyIndex.text = subnet.netKey.index.toString()

            //imageViewRemove.visibility = View.GONE
            imageViewRemove.setOnClickListener {
                itemListener.onDeleteClick(subnet)
            }
            swipe.apply {
                surfaceView.setOnLongClickListener {
                    this.open()
                    return@setOnLongClickListener true
                }
                surfaceView.setOnClickListener {
                    itemListener.onItemClick(subnet)
                }
                showMode = SwipeLayout.ShowMode.LayDown
                addDrag(SwipeLayout.DragEdge.Right, swipeMenu)
            }
        }
    }
}