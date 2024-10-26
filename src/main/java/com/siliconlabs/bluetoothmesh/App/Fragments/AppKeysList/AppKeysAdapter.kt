/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.AppKeysList

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.daimajia.swipe.SwipeLayout
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.AppKey
import com.siliconlabs.bluetoothmesh.App.Views.SwipeBaseAdapter
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.ItemAppkeyBinding

class AppKeysAdapter(private val itemListener: ItemListener<AppKey>) : SwipeBaseAdapter<AppKey>() {

    override fun generateView(position: Int, parent: ViewGroup?): View {
        return LayoutInflater.from(parent?.context).inflate(R.layout.item_appkey, parent, false)
    }

    override fun fillValues(position: Int, convertView: View) {
        val appKey = getItem(position)
        val binding = ItemAppkeyBinding.bind(convertView)

        binding.apply {
            textViewAppKeyDevices.text = convertView.resources.getQuantityString(
                    R.plurals.app_keys_adapter_devices_label,
                    appKey.nodes.size,
                    appKey.nodes.size)
            textViewAppKeyIndex.text = appKey.index.toString()

            imageViewRemove.visibility = View.GONE
            imageViewRemove.setOnClickListener {
                itemListener.onDeleteClick(appKey)
            }
            swipe.apply {
                surfaceView.setOnLongClickListener {
                    this.open()
                    return@setOnLongClickListener true
                }
                surfaceView.setOnClickListener {
                    itemListener.onItemClick(appKey)
                }
                showMode = SwipeLayout.ShowMode.LayDown
                addDrag(SwipeLayout.DragEdge.Right, binding.swipeMenu)
            }
        }
    }
}
