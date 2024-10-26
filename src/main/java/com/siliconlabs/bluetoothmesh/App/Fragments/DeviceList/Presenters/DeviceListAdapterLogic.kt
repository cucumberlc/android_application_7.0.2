/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.Presenters

import com.siliconlab.bluetoothmesh.adk.data_model.model.ModelIdentifier
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.functionality_control.generic.GenericClient
import com.siliconlab.bluetoothmesh.adk.functionality_control.generic.StateType
import com.siliconlab.bluetoothmesh.adk.functionality_control.scene.Status
import com.siliconlab.bluetoothmesh.adk.onFailure
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.DeviceListAdapter
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.Presenters.LC.DeviceListAdapterLCLogic
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.Presenters.Scene.DeviceListAdapterSceneLogic
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.DeviceViewHolderBase
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.App.Models.DeviceFunctionality
import com.siliconlabs.bluetoothmesh.App.Utils.ControlConverters
import com.siliconlabs.bluetoothmesh.App.Utils.Message
import com.siliconlabs.bluetoothmesh.App.Utils.apiExtensions.findSigModel
import com.siliconlabs.bluetoothmesh.App.Utils.defaultTimeout
import com.siliconlabs.bluetoothmesh.App.Utils.responseTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.tinylog.kotlin.Logger

class DeviceListAdapterLogic(
    val listener: DeviceListAdapterLogicListener,
    val deviceListAdapterListener: DeviceListAdapter.DeviceListAdapterListener,
    private val coroutineScope: CoroutineScope,
) {
    val lcLogic = DeviceListAdapterLCLogic(listener, coroutineScope)
    val sceneLogic = DeviceListAdapterSceneLogic(listener, coroutineScope)

    interface DeviceListAdapterLogicListener {

        fun showToast(message: Message)
        fun showToast(sceneStatusCode: Status)

        fun notifyItemChanged(item: MeshNode)
    }

    fun onClickDeviceImage(meshNode: MeshNode) {
        when (meshNode.functionality) {
            DeviceFunctionality.FUNCTIONALITY.OnOff -> {
                val newOnOffState = meshNode.onOffState.not()
                val genericOnOffServer = meshNode.findSigModel(ModelIdentifier.GenericOnOffServer)
                genericOnOffServer?.let { model ->
                    GenericClient.setOnOff(
                        model.boundAppKeys.first(),
                        model.element.address,
                        newOnOffState,
                        1,
                        1,
                        false,
                        ++transactionId,
                        false
                    ).onFailure {
                        listener.showToast(Message.info(it))
                    }
                    meshNode.onOffState = newOnOffState
                }
            }
            DeviceFunctionality.FUNCTIONALITY.Level -> {
                val newLevelPercentage = if (meshNode.levelPercentage > 0) 0 else 100
                val genericLevelServer = meshNode.findSigModel(ModelIdentifier.GenericLevelServer)
                genericLevelServer?.let { model ->
                    GenericClient.setLevel(
                        model.boundAppKeys.first(),
                        model.element.address,
                        ControlConverters.getLevel(newLevelPercentage),
                        1,
                        1,
                        false,
                        ++transactionId,
                        false,
                    )
                    meshNode.levelPercentage = newLevelPercentage
                }
            }
            DeviceFunctionality.FUNCTIONALITY.Lightness -> {
                val newLightnessPercentage = if (meshNode.lightnessPercentage > 0) 0 else 100
                val lightLightnessServer =
                    meshNode.findSigModel(ModelIdentifier.LightLightnessServer)
                lightLightnessServer?.let { model ->
                    GenericClient.setLightnessActual(
                        model.boundAppKeys.first(),
                        model.element.address,
                        ControlConverters.getLightness(newLightnessPercentage),
                        1,
                        1,
                        false,
                        ++transactionId,
                        false,
                    ).onFailure { listener.showToast(Message.info(it)) }
                    meshNode.lightnessPercentage = newLightnessPercentage
                }
            }
            DeviceFunctionality.FUNCTIONALITY.CTL -> {
                val newLightnessPercentage = if (meshNode.lightnessPercentage > 0) 0 else 100
                val lightCTLServer = meshNode.findSigModel(ModelIdentifier.LightCTLServer)
                lightCTLServer?.let { model ->
                    GenericClient.setCtl(
                        model.boundAppKeys.first(),
                        model.element.address,
                        ControlConverters.getLightness(newLightnessPercentage),
                        meshNode.temperature,
                        ControlConverters.getDeltaUv(meshNode.deltaUvPercentage),
                        1,
                        1,
                        false,
                        ++transactionId,
                        false,
                    )
                    meshNode.lightnessPercentage = newLightnessPercentage
                }
            }
            else -> Unit
        }

        listener.notifyItemChanged(meshNode)
    }

    fun onRefreshClick(
        meshNode: MeshNode,
        refreshNodeListener: DeviceViewHolderBase.RefreshNodeListener
    ) {
        when (meshNode.functionality) {
            DeviceFunctionality.FUNCTIONALITY.OnOff -> {
                val genericOnOffServer = meshNode.findSigModel(ModelIdentifier.GenericOnOffServer)
                genericOnOffServer?.let { model ->
                    GenericClient.getState(
                        ModelIdentifier.GenericOnOffClient,
                        StateType.OnOff,
                        model.boundAppKeys.first(),
                        model.element.address,
                        false,
                    ).onFailure {
                        listener.showToast(Message.info(it))
                        return
                    }
                    refreshNodeListener.startRefresh()
                    coroutineScope.launch {
                        val response = withTimeoutOrNull(defaultTimeout) {
                            GenericClient.onOffResponse.first()
                        }
                        Logger.debug { "onOffResponse ${response}" }

                        response?.let {
                            meshNode.onOffState = response.on
                            listener.notifyItemChanged(meshNode)
                        } ?: listener.showToast(Message.info(responseTimeout))
                        refreshNodeListener.stopRefresh()
                    }
                }
            }
            DeviceFunctionality.FUNCTIONALITY.Level -> {
                val genericLevelServer = meshNode.findSigModel(ModelIdentifier.GenericLevelServer)
                genericLevelServer?.let { model ->
                    GenericClient.getState(
                        ModelIdentifier.GenericLevelClient,
                        StateType.Level,
                        model.boundAppKeys.first(),
                        model.element.address,
                        false,
                    ).onFailure {
                        listener.showToast(Message.info(it))
                        return
                    }
                    refreshNodeListener.startRefresh()
                    coroutineScope.launch {
                        val response = withTimeoutOrNull(defaultTimeout) {
                            GenericClient.levelResponse.first()
                        }
                        Logger.debug { "levelResponse ${response}" }

                        response?.let {
                            meshNode.levelPercentage =
                                ControlConverters.getLevelPercentage(it.level)
                            listener.notifyItemChanged(meshNode)
                        } ?: listener.showToast(Message.info(responseTimeout))
                        refreshNodeListener.stopRefresh()
                    }
                }
            }
            DeviceFunctionality.FUNCTIONALITY.Lightness -> {
                val lightLightnessServer =
                    meshNode.findSigModel(ModelIdentifier.LightLightnessServer)
                lightLightnessServer?.let { model ->
                    GenericClient.getState(
                        ModelIdentifier.LightLightnessClient,
                        StateType.LightnessActual,
                        model.boundAppKeys.first(),
                        model.element.address,
                        false,
                    ).onFailure {
                        listener.showToast(Message.info(it))
                        return
                    }
                    refreshNodeListener.startRefresh()
                    coroutineScope.launch {
                        val response = withTimeoutOrNull(defaultTimeout) {
                            GenericClient.lightnessActualResponse.first()
                        }
                        Logger.debug { "lightnessActualResponse ${response}" }

                        response?.let {
                            meshNode.lightnessPercentage = ControlConverters.getLightnessPercentage(
                                response.level
                            )
                            listener.notifyItemChanged(meshNode)
                        } ?: listener.showToast(Message.info(responseTimeout))
                        refreshNodeListener.stopRefresh()
                    }
                }
            }
            DeviceFunctionality.FUNCTIONALITY.CTL -> {
                val lightCTLServer = meshNode.findSigModel(ModelIdentifier.LightCTLServer)
                lightCTLServer?.let { model ->
                    GenericClient.getState(
                        ModelIdentifier.LightCTLClient,
                        StateType.CtlLightnessTemperature,
                        model.boundAppKeys.first(),
                        model.element.address,
                        false,
                    ).onFailure {
                        listener.showToast(Message.info(it))
                        return
                    }
                    refreshNodeListener.startRefresh()

                    coroutineScope.launch {
                        val response = withTimeoutOrNull(defaultTimeout) {
                            GenericClient.ctlLightnessTemperatureResponse.first()
                        }
                        Logger.debug { "ctlLightnessTemperatureResponse ${response}" }

                        response?.let {
                            meshNode.lightnessPercentage = ControlConverters.getLightnessPercentage(
                                response.lightness
                            )
                            meshNode.temperature = response.temperature.toInt()

                            val lightCTLTemperatureServer =
                                meshNode.findSigModel(ModelIdentifier.LightCTLTemperatureServer)
                            lightCTLTemperatureServer?.let {
                                GenericClient.getState(
                                    ModelIdentifier.LightCTLClient,
                                    StateType.CtlTemperature,
                                    it.boundAppKeys.first(),
                                    it.element.address,
                                    false,
                                ).onFailure { error ->
                                    listener.showToast(Message.info(error))
                                    refreshNodeListener.stopRefresh()
                                    return@launch
                                }
                                launch {
                                    val ctlTemperatureResponse = withTimeoutOrNull(defaultTimeout) {
                                        GenericClient.ctlTemperatureResponse.first()
                                    }
                                    Logger.debug { "ctlTemperatureResponse ${response}" }

                                    refreshNodeListener.stopRefresh()
                                    ctlTemperatureResponse?.let {
                                        meshNode.deltaUvPercentage =
                                            ControlConverters.getDeltaUvPercentage(
                                                GenericClient.ctlTemperatureResponse.first().deltaUv
                                            )
                                        listener.notifyItemChanged(meshNode)
                                    } ?: listener.showToast(Message.info(responseTimeout))
                                }
                            }
                        } ?: kotlin.run {
                            listener.showToast(Message.info(responseTimeout))
                            refreshNodeListener.stopRefresh()
                        }
                    }
                }
            }
            else -> Unit
        }
    }

    fun onSeekBarChange(
        meshNode: MeshNode,
        levelPercentage: Int,
        temperature: Int? = null,
        deltaUvPercentage: Int? = null
    ) {
        when (meshNode.functionality) {
            DeviceFunctionality.FUNCTIONALITY.Level -> {
                val genericLevelServer = meshNode.findSigModel(ModelIdentifier.GenericLevelServer)
                genericLevelServer?.let { model ->
                    GenericClient.setLevel(
                        model.boundAppKeys.first(),
                        model.element.address,
                        ControlConverters.getLevel(levelPercentage),
                        1,
                        1,
                        false,
                        ++transactionId,
                        false,
                    ).onFailure { listener.showToast(Message.info(it)) }
                    meshNode.levelPercentage = levelPercentage
                }
            }
            DeviceFunctionality.FUNCTIONALITY.Lightness -> {
                val lightLightnessServer =
                    meshNode.findSigModel(ModelIdentifier.LightLightnessServer)
                lightLightnessServer?.let { model ->
                    GenericClient.setLightnessActual(
                        model.boundAppKeys.first(),
                        model.element.address,
                        ControlConverters.getLightness(levelPercentage),
                        1,
                        1,
                        false,
                        ++transactionId,
                        false,
                    ).onFailure { listener.showToast(Message.info(it)) }
                    meshNode.lightnessPercentage = levelPercentage
                }
            }
            DeviceFunctionality.FUNCTIONALITY.CTL -> {
                if (temperature != null && deltaUvPercentage != null) {
                    val lightCTLServer = meshNode.findSigModel(ModelIdentifier.LightCTLServer)
                    lightCTLServer?.let { model ->
                        GenericClient.setCtl(
                            model.boundAppKeys.first(),
                            model.element.address,
                            ControlConverters.getLightness(levelPercentage),
                            temperature,
                            ControlConverters.getDeltaUv(deltaUvPercentage),
                            1,
                            1,
                            false,
                            ++transactionId,
                            false,
                        )
                        meshNode.lightnessPercentage = levelPercentage
                        meshNode.temperature = temperature
                        meshNode.deltaUvPercentage = deltaUvPercentage
                    }
                }
            }
            else -> Unit
        }
        sceneLogic.refreshSceneStatus(meshNode, null)
        listener.notifyItemChanged(meshNode)
    }

    fun onUpdateFirmwareClick(node: Node) {
        deviceListAdapterListener.onUpdateFirmwareClick(node)
    }

    fun onRemoteProvisionClick(node: Node) {
        deviceListAdapterListener.onRemoteProvisionClick(node)
    }

    companion object {
        private var transactionId: UByte = 0u
    }
}