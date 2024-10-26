/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.Server

import android.view.View
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.Presenters.DeviceListAdapterLogic
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.DeviceViewHolderBase
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.DevicesAdapterLightnessBinding

class LevelViewHolder(private val layout: DevicesAdapterLightnessBinding, deviceListLogic: DeviceListAdapterLogic)
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

        bindLevel(meshNode)
        bindBaseScene()
        bindBaseHeader(meshNode, isNetworkConnected)
    }

    private fun bindLevel(meshNode: MeshNode) {
        layout.apply {
            swipe.setup(meshNode)
            sbLevelControl.progress = meshNode.levelPercentage
            sbLevelControl.setOnSeekBarChangeListener(ControlChangeListener(meshNode))
            tvLevelValue.text = view.context.getString(R.string.device_adapter_lightness_value).format(sbLevelControl.progress)
        }
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
                ivDeviceImage.setImageResource(R.drawable.lamp_disabled)
            } else if (meshNode.levelPercentage > 0) {
                ivDeviceImage.setImageResource(R.drawable.lamp_on)
            } else {
                ivDeviceImage.setImageResource(R.drawable.lamp_off)
            }
            setEnabledControls(isNetworkConnected)
        }
    }
}