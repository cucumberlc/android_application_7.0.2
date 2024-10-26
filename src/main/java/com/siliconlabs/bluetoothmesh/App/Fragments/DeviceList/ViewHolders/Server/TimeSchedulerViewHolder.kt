/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.Server

import android.view.View
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.Presenters.DeviceListAdapterLogic
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.DeviceViewHolderBase
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.App.Models.DeviceFunctionality
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.DevicesAdapterTimeSchedulerBinding

class TimeSchedulerViewHolder(
    private val layout: DevicesAdapterTimeSchedulerBinding,
    deviceListLogic: DeviceListAdapterLogic
) : DeviceViewHolderBase(
    layout.root,
    layout.devicesAdapterBaseHeader,
    layout.devicesAdapterBaseScene,
    layout.devicesAdapterBaseSwipeMenu,
    layout.devicesAdapterBaseRemoteProvisioning,
    deviceListLogic,
) {

    private lateinit var meshNode: MeshNode

    override fun bindView(meshNode: MeshNode, isNetworkConnected: Boolean) {
        super.bindView(meshNode, isNetworkConnected)
        this.meshNode = meshNode

        bindTimeScheduler(meshNode, isNetworkConnected)
        bindBaseScene()
        bindBaseHeader()
    }

    private fun bindTimeScheduler(meshNode: MeshNode, isNetworkConnected: Boolean) {
        layout.apply {

            swipe.setup(meshNode)
            if (isNetworkConnected) {
                llBackControl.setOnClickListener {
                    llBackControl.visibility = View.GONE
                    llTimeSchedulerMenu.visibility = View.VISIBLE
                }

                llSchedulerControl.setOnClickListener {
                    deviceListLogic.deviceListAdapterListener.onFunctionalityClicked(
                            meshNode, DeviceFunctionality.FUNCTIONALITY.Scheduler)
                }

                llTimeControl.setOnClickListener {
                    deviceListLogic.deviceListAdapterListener.onFunctionalityClicked(
                            meshNode, DeviceFunctionality.FUNCTIONALITY.TimeServer)
                }
            } else {
                llSchedulerControl.setOnClickListener(null)
                llTimeControl.setOnClickListener(null)
                llBackControl.setOnClickListener(null)
            }

            setEnabledControls(isNetworkConnected)
        }
    }

    private fun bindBaseScene() {
        layout.devicesAdapterBaseScene.apply {
            scenesLayout.visibility = View.GONE
        }
    }

    private fun bindBaseHeader() {
        layout.devicesAdapterBaseHeader.apply {
            ivDeviceImage.setImageResource(R.drawable.ic_scheduler)
        }
    }
}