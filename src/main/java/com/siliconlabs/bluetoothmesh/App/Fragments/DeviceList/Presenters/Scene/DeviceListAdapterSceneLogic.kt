/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.Presenters.Scene

import com.siliconlab.bluetoothmesh.adk.data_model.model.ModelIdentifier
import com.siliconlab.bluetoothmesh.adk.functionality_control.scene.SceneClient
import com.siliconlab.bluetoothmesh.adk.functionality_control.scene.Status
import com.siliconlab.bluetoothmesh.adk.onFailure
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.Presenters.DeviceListAdapterLogic
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.DeviceViewHolderBase
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.App.Utils.Message
import com.siliconlabs.bluetoothmesh.App.Utils.apiExtensions.findSigModel
import com.siliconlabs.bluetoothmesh.App.Utils.defaultTimeout
import com.siliconlabs.bluetoothmesh.App.Utils.responseTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class DeviceListAdapterSceneLogic(
    val listener: DeviceListAdapterLogic.DeviceListAdapterLogicListener,
    private val coroutineScope: CoroutineScope,
) {
    fun refreshSceneStatus(
        meshNode: MeshNode,
        refreshNodeListener: DeviceViewHolderBase.RefreshNodeListener?
    ) {
        val sceneServer = meshNode.findSigModel(ModelIdentifier.SceneServer)
        sceneServer?.let { model ->
            SceneClient.getRegister(
                model.element.address,
                model.boundAppKeys.first(),
            ).onFailure {
                listener.showToast(Message.info(it))
                return
            }
            refreshNodeListener?.startRefresh()
            coroutineScope.launch {
                val response = withTimeoutOrNull(defaultTimeout) {
                    SceneClient.sceneRegisterResponse.first()
                }
                refreshNodeListener?.stopRefresh()
                response?.let {
                    if (response.status == Status.Success) {
                        if (response.scenes.contains(1)) {
                            meshNode.sceneOneStatus = MeshNode.SceneStatus.STORED
                        } else {
                            meshNode.sceneOneStatus = MeshNode.SceneStatus.NOT_STORED
                        }
                        if (response.scenes.contains(2)) {
                            meshNode.sceneTwoStatus = MeshNode.SceneStatus.STORED
                        } else {
                            meshNode.sceneTwoStatus = MeshNode.SceneStatus.NOT_STORED
                        }
                        changeActiveScene(meshNode, response.currentScene)
                        listener.notifyItemChanged(meshNode)
                    } else {
                        listener.showToast(response.status)
                    }
                } ?: listener.showToast(Message.info(responseTimeout))
            }
        }
    }

    fun storeScene(meshNode: MeshNode, sceneNumber: UShort) {
        val sceneServer = meshNode.findSigModel(ModelIdentifier.SceneServer)
        sceneServer?.let { model ->
            SceneClient.store(
                model.element.address,
                model.boundAppKeys.first(),
                true,
                sceneNumber,
            ).onFailure {
                listener.showToast(Message.info(it))
                return
            }
            coroutineScope.launch {
                val response = withTimeoutOrNull(defaultTimeout) {
                    SceneClient.sceneRegisterResponse.first()
                }

                response?.let {
                    listener.showToast(response.status)
                    if (response.status == Status.Success) {
                        changeActiveScene(meshNode, response.currentScene)
                        listener.notifyItemChanged(meshNode)
                    }
                } ?: listener.showToast(Message.info(responseTimeout))
            }
        }
    }

    fun recallScene(meshNode: MeshNode, sceneNumber: UShort) {
        val sceneServer = meshNode.findSigModel(ModelIdentifier.SceneServer)
        sceneServer?.let { model ->
            SceneClient.recall(
                model.element.address,
                model.boundAppKeys.first(),
                true,
                sceneNumber,
                ++transactionId,
            ).onFailure {
                listener.showToast(Message.info(it))
                return
            }
            coroutineScope.launch {
                val response = withTimeoutOrNull(defaultTimeout) {
                    SceneClient.sceneResponse.first()
                }

                response?.let {
                    listener.showToast(response.status)
                    if (response.status == Status.Success) {
                        changeActiveScene(meshNode, response.currentScene.toInt())
                        listener.notifyItemChanged(meshNode)
                    }
                } ?: listener.showToast(Message.info(responseTimeout))
            }
        }
    }

    fun deleteScene(meshNode: MeshNode, sceneNumber: UShort) {
        val sceneServer = meshNode.findSigModel(ModelIdentifier.SceneServer)
        sceneServer?.let { model ->
            SceneClient.delete(
                model.element.address,
                model.boundAppKeys.first(),
                true,
                sceneNumber,
            ).onFailure {
                listener.showToast(Message.info(it))
                return
            }
            coroutineScope.launch {
                val response = withTimeoutOrNull(defaultTimeout) {
                    SceneClient.sceneRegisterResponse.first()
                }

                response?.let {
                    listener.showToast(response.status)
                    if (response.status == Status.Success) {
                        when (sceneNumber.toInt()) {
                            1 -> meshNode.sceneOneStatus = MeshNode.SceneStatus.NOT_STORED
                            2 -> meshNode.sceneTwoStatus = MeshNode.SceneStatus.NOT_STORED
                        }
                        listener.notifyItemChanged(meshNode)
                    }
                } ?: listener.showToast(Message.info(responseTimeout))
            }
        }
    }

    private fun changeActiveScene(meshNode: MeshNode, sceneNumber: Int) {
        when (sceneNumber) {
            1 -> {
                meshNode.sceneOneStatus = MeshNode.SceneStatus.ACTIVE
                if (meshNode.sceneTwoStatus == MeshNode.SceneStatus.ACTIVE) {
                    meshNode.sceneTwoStatus = MeshNode.SceneStatus.STORED
                }
            }
            2 -> {
                meshNode.sceneTwoStatus = MeshNode.SceneStatus.ACTIVE
                if (meshNode.sceneOneStatus == MeshNode.SceneStatus.ACTIVE) {
                    meshNode.sceneOneStatus = MeshNode.SceneStatus.STORED
                }
            }
        }
    }

    companion object {
        private var transactionId: UByte = 0u
    }
}