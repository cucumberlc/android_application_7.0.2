/*
 * Copyright © 2021 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.Server

import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.Presenters.DeviceListAdapterLogic
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.DeviceViewHolderBase
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.DevicesAdapterDistributionBinding

class DistributionViewHolder(private val layout: DevicesAdapterDistributionBinding, deviceListLogic: DeviceListAdapterLogic) : DeviceViewHolderBase(
        layout.root,
        layout.devicesAdapterBaseHeader,
        layout.devicesAdapterBaseScene,
        layout.devicesAdapterBaseSwipeMenu,
        layout.devicesAdapterBaseRemoteProvisioning,
        deviceListLogic,
) {
    override fun bindView(meshNode: MeshNode, isNetworkConnected: Boolean) {
        super.bindView(meshNode, isNetworkConnected)

        bindDistribution(meshNode, isNetworkConnected)
        bindBaseHeader()
    }

    private fun bindDistribution(meshNode: MeshNode, isNetworkConnected: Boolean) {
        layout.apply {
            swipe.setup(meshNode)
            firmwareDistributionWrapper.isEnabled = isNetworkConnected
            setEnabledControls(isNetworkConnected)
            firmwareDistributionWrapper.setOnClickListener {
                deviceListLogic.onUpdateFirmwareClick(meshNode.node)
            }
        }
    }

    private fun bindBaseHeader() {
        layout.devicesAdapterBaseHeader.apply {
            ivDeviceImage.setImageResource(R.drawable.ic_distributor)
        }
    }
}