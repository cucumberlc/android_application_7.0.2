package com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.Server

import android.view.View
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.Presenters.DeviceListAdapterLogic
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.DeviceViewHolderBase
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.App.Models.DeviceFunctionality
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.DevicesAdapterTimeBinding

class TimeViewHolder(private val layout: DevicesAdapterTimeBinding, deviceListLogic: DeviceListAdapterLogic)
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

        bindTime(meshNode)
        bindBaseScene()
        bindBaseHeader()

        setEnabledControls(isNetworkConnected)
    }

    private fun bindTime(meshNode: MeshNode) {
        layout.apply {
            swipe.setup(meshNode)
            llTimeControl.setOnClickListener {
                deviceListLogic.deviceListAdapterListener.onFunctionalityClicked(meshNode, DeviceFunctionality.FUNCTIONALITY.TimeServer)
            }
        }
    }

    private fun bindBaseScene() {
        layout.devicesAdapterBaseScene.apply {
            scenesLayout.visibility = View.GONE
        }
    }

    private fun bindBaseHeader() {
        layout.devicesAdapterBaseHeader.apply {
            ivDeviceImage.setImageResource(R.drawable.ic_time)
        }
    }
}