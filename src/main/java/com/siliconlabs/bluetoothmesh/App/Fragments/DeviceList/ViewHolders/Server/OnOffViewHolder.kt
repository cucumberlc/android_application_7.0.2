/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.Server

import android.view.View
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.Presenters.DeviceListAdapterLogic
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.DeviceViewHolderBase
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.DevicesAdapterDefaultBinding

class OnOffViewHolder(private val layout: DevicesAdapterDefaultBinding, deviceListLogic: DeviceListAdapterLogic)
    : DeviceViewHolderBase(
        layout.root,
        layout.devicesAdapterBaseHeader,
        layout.devicesAdapterBaseScene,
        layout.devicesAdapterBaseSwipeMenu,
        layout.devicesAdapterBaseRemoteProvisioning,
        deviceListLogic,
) {

    override fun bindView(meshNode: MeshNode, isNetworkConnected: Boolean) {
        super.bindView(meshNode, isNetworkConnected)

        bindOnOff(meshNode)
        bindBaseScene()
        bindBaseHeader(meshNode, isNetworkConnected)

        setEnabledControls(isNetworkConnected)
    }

    private fun bindOnOff(meshNode: MeshNode) {
        layout.swipe.setup(meshNode)
    }

    private fun bindBaseScene() {
        layout.devicesAdapterBaseScene.apply {
            scenesLayout.visibility = View.VISIBLE
        }
    }

    private fun bindBaseHeader(meshNode: MeshNode, isNetworkConnected: Boolean) {
        layout.devicesAdapterBaseHeader.apply {
            ivRefresh.visibility = View.VISIBLE
            ivDeviceImage.setOnClickListener(ClickDeviceImageListener(meshNode))
            ivRefresh.setOnClickListener(ClickRefreshListener(meshNode, ivRefresh))

            if (!isNetworkConnected) {
                ivDeviceImage.setImageResource(R.drawable.toggle_off)
            } else if (meshNode.onOffState) {
                ivDeviceImage.setImageResource(R.drawable.toggle_on)
            } else {
                ivDeviceImage.setImageResource(R.drawable.toggle_off)
            }
        }
    }
}