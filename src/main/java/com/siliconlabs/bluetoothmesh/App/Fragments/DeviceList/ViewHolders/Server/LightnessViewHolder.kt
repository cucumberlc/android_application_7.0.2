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

class LightnessViewHolder(private val layout: DevicesAdapterLightnessBinding, deviceListLogic: DeviceListAdapterLogic)
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

        bindLightness(meshNode)
        bindBaseScene()
        bindBaseHeader(meshNode, isNetworkConnected)
    }

    private fun bindLightness(meshNode: MeshNode) {
        layout.apply {
            swipe.setup(meshNode)
            sbLevelControl.progress = meshNode.lightnessPercentage
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
                ivDeviceImage.setImageResource(R.drawable.toggle_off)
            } else if (meshNode.lightnessPercentage > 0) {
                ivDeviceImage.setImageResource(R.drawable.toggle_on)
            } else {
                ivDeviceImage.setImageResource(R.drawable.toggle_off)
            }
            setEnabledControls(isNetworkConnected)
        }
    }
}