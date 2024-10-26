/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders

import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.appcompat.widget.AppCompatSpinner
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.daimajia.swipe.SwipeLayout
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.Presenters.DeviceListAdapterLogic
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.App.Views.RefreshNodeButton
import com.siliconlabs.bluetoothmesh.databinding.DevicesAdapterBaseHeaderBinding
import com.siliconlabs.bluetoothmesh.databinding.DevicesAdapterBaseRemoteProvisioningBinding
import com.siliconlabs.bluetoothmesh.databinding.DevicesAdapterBaseSceneBinding
import com.siliconlabs.bluetoothmesh.databinding.DevicesAdapterBaseSwipeMenuBinding

abstract class DeviceViewHolderBase(val view: View,
                                    private val devicesAdapterBaseHeaderBinding: DevicesAdapterBaseHeaderBinding,
                                    private val devicesAdapterBaseSceneBinding: DevicesAdapterBaseSceneBinding,
                                    private val devicesAdapterBaseSwipeMenuBinding: DevicesAdapterBaseSwipeMenuBinding,
                                    private val devicesAdapterBaseRemoteProvisioningBinding: DevicesAdapterBaseRemoteProvisioningBinding,
                                    protected val deviceListLogic: DeviceListAdapterLogic) :
        RecyclerView.ViewHolder(view) {

    open fun bindView(meshNode: MeshNode, isNetworkConnected: Boolean) {
        val node = meshNode.node
        devicesAdapterBaseHeaderBinding.apply {
            tvDeviceName.text = node.name
            tvDeviceId.text = node.primaryElementAddress.toString()

            ivRefresh.visibility = View.GONE
        }

        devicesAdapterBaseSceneBinding.scenesLayout.visibility = View.GONE

        devicesAdapterBaseSwipeMenuBinding.apply {
            ivConfig.setOnClickListener {
                deviceListLogic.deviceListAdapterListener.onConfigureClicked(meshNode)
            }
            ivRemove.setOnClickListener {
                deviceListLogic.deviceListAdapterListener.onDeleteClicked(node)
            }
        }
            setupScene(meshNode)
            setupRemoteProvisioningSection(meshNode, isNetworkConnected)

    }

    private fun setupScene(meshNode: MeshNode) {
        devicesAdapterBaseSceneBinding.apply {
            btnStoreSceneOne.setOnClickListener {
                deviceListLogic.sceneLogic.storeScene(meshNode, 1u)
            }
            btnStoreSceneTwo.setOnClickListener {
                deviceListLogic.sceneLogic.storeScene(meshNode, 2u)
            }
        }
    }

    private fun setupRemoteProvisioningSection(meshNode: MeshNode, isConnectedToSubnet: Boolean) {
        devicesAdapterBaseRemoteProvisioningBinding.apply {
            if (meshNode.supportsRemoteProvisioning()) {
                dividerRemoteProvisioning.isVisible = true
                wrapperRemoteProvisioning.isVisible = true

                wrapperRemoteProvisioning.setOnClickListener {
                    if (isConnectedToSubnet)
                        deviceListLogic.onRemoteProvisionClick(meshNode.node)
                }
            } else {
                dividerRemoteProvisioning.isGone = true
                wrapperRemoteProvisioning.isGone = true
            }
        }
    }

    fun SwipeLayout.setup(meshNode: MeshNode) {
        surfaceView.setOnClickListener {
            deviceListLogic.deviceListAdapterListener.onConfigureClicked(meshNode)
        }
        surfaceView.setOnLongClickListener {
            this@setup.open()
            return@setOnLongClickListener true
        }
        showMode = SwipeLayout.ShowMode.LayDown
        addDrag(SwipeLayout.DragEdge.Right, devicesAdapterBaseSwipeMenuBinding.swipeMenu)
    }

    fun setEnabledControls(enabled: Boolean) {
        setEnabledControls(view, enabled)
        setEnabledControls(devicesAdapterBaseSwipeMenuBinding.swipeMenu, true)
    }

    private fun setEnabledControls(view: View, enabled: Boolean) {
        if (view is ViewGroup && view !is AppCompatSpinner) {
            for (i in 0 until view.childCount) {
                setEnabledControls(view.getChildAt(i), enabled)
            }
        } else {
            view.isEnabled = enabled
            view.alpha = if (enabled) 1f else 0.5f
        }
    }

    inner class ClickRefreshListener(private val deviceInfo: MeshNode,
                                     private val refreshNodeButton: RefreshNodeButton) : View.OnClickListener {
        override fun onClick(v: View?) {
            deviceListLogic.onRefreshClick(deviceInfo, RefreshNodeListener(refreshNodeButton))
        }
    }

    inner class ControlChangeListener(private val deviceInfo: MeshNode) : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {
        }

        override fun onStopTrackingTouch(seekBar: SeekBar?) {
            seekBar?.apply {
                deviceListLogic.onSeekBarChange(deviceInfo, progress)
            }
        }
    }

    inner class ClickDeviceImageListener(private val deviceInfo: MeshNode) : View.OnClickListener {
        override fun onClick(v: View?) {
            deviceListLogic.onClickDeviceImage(deviceInfo)
        }
    }

    class RefreshNodeListener(private val refreshNodeButton: RefreshNodeButton) {
        fun startRefresh() {
            refreshNodeButton.startRefresh()
        }

        fun stopRefresh() {
            refreshNodeButton.stopRefresh()
        }
    }
}