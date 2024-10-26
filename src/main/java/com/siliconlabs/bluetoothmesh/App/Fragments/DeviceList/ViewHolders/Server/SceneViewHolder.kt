/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.Server

import android.view.View
import android.widget.TextView
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.Presenters.DeviceListAdapterLogic
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.DeviceViewHolderBase
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.DevicesAdapterSceneBinding

class SceneViewHolder(private val layout: DevicesAdapterSceneBinding, deviceListLogic: DeviceListAdapterLogic)
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

        bindScene(meshNode, isNetworkConnected)
        bindBaseScene()
        bindBaseHeader()
    }

    private fun bindScene(meshNode: MeshNode, isNetworkConnected: Boolean) {
        layout.apply {
            swipe.setup(meshNode)

            ivSceneRefresh.setOnClickListener {
                deviceListLogic.sceneLogic.refreshSceneStatus(meshNode, RefreshNodeListener(ivSceneRefresh))
            }
            // scene one
            btnSceneOneRecall.setOnClickListener {
                deviceListLogic.sceneLogic.recallScene(meshNode, 1u)
            }
            ivSceneOneRemove.setOnClickListener {
                deviceListLogic.sceneLogic.deleteScene(meshNode, 1u)
            }
            setSceneStatus(tvSceneOneStatus, meshNode.sceneOneStatus)
            // scene two
            btnSceneTwoRecall.setOnClickListener {
                deviceListLogic.sceneLogic.recallScene(meshNode, 2u)
            }
            ivSceneTwoRemove.setOnClickListener {
                deviceListLogic.sceneLogic.deleteScene(meshNode, 2u)
            }
            setSceneStatus(tvSceneTwoStatus, meshNode.sceneTwoStatus)

            setEnabledControls(isNetworkConnected)
        }
    }

    private fun bindBaseScene() {
        layout.devicesAdapterBaseScene.apply {
            scenesLayout.visibility = View.VISIBLE
        }
    }

    private fun bindBaseHeader() {
        layout.devicesAdapterBaseHeader.apply {
            ivDeviceImage.setImageResource(R.drawable.ic_scene)
        }
    }

    private fun setSceneStatus(tvSceneStatus: TextView, sceneStatus: MeshNode.SceneStatus) {
        tvSceneStatus.apply {
            val greyColor = resources.getColor(R.color.adapter_item_label_color, null)
            val whiteColor = resources.getColor(R.color.adapter_item_title_color, null)
            val greenColor = resources.getColor(R.color.adapter_item_active_color, null)

            when (sceneStatus) {
                MeshNode.SceneStatus.NOT_KNOWN -> {
                    text = resources.getString(R.string.device_adapter_scenes_not_known_state)
                    setTextColor(greyColor)
                }
                MeshNode.SceneStatus.NOT_STORED -> {
                    text = resources.getString(R.string.device_adapter_scenes_not_stored_state)
                    setTextColor(greyColor)
                }
                MeshNode.SceneStatus.STORED -> {
                    text = resources.getString(R.string.device_adapter_scenes_stored_state)
                    setTextColor(whiteColor)
                }
                MeshNode.SceneStatus.ACTIVE -> {
                    text = resources.getString(R.string.device_adapter_scenes_active_state)
                    setTextColor(greenColor)
                }
            }
        }
    }
}