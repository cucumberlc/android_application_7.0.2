/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.Presenters.LC

import com.siliconlab.bluetoothmesh.adk.data_model.model.ModelIdentifier
import com.siliconlab.bluetoothmesh.adk.functionality_control.light_control.LightControlClient
import com.siliconlab.bluetoothmesh.adk.functionality_control.light_control.Mode
import com.siliconlab.bluetoothmesh.adk.functionality_control.light_control.OccupancyMode
import com.siliconlab.bluetoothmesh.adk.functionality_control.light_control.OnOffState
import com.siliconlab.bluetoothmesh.adk.onFailure
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.Presenters.DeviceListAdapterLogic
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.DeviceViewHolderBase
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.App.Utils.Message
import com.siliconlabs.bluetoothmesh.App.Utils.apiExtensions.findSigModel
import com.siliconlabs.bluetoothmesh.App.Utils.defaultTimeout
import com.siliconlabs.bluetoothmesh.App.Utils.responseTimeout
import com.siliconlabs.bluetoothmesh.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.tinylog.kotlin.Logger

class DeviceListAdapterLCLogic(
    val listener: DeviceListAdapterLogic.DeviceListAdapterLogicListener,
    private val coroutineScope: CoroutineScope,
) {
    fun refreshLCMode(
        meshNode: MeshNode,
        refreshNodeListener: DeviceViewHolderBase.RefreshNodeListener,
    ) {
        val lightLCServer = meshNode.findSigModel(ModelIdentifier.LightLCServer)
        lightLCServer?.let { model ->
            LightControlClient.getMode(
                model.element.address,
                meshNode.node.boundAppKeys.first(),
            ).onFailure {
                listener.showToast(Message.info(it))
                return
            }

            refreshNodeListener.startRefresh()
            coroutineScope.launch {
                val response = withTimeoutOrNull(defaultTimeout) {
                    LightControlClient.modeResponse.first()
                }
                Logger.debug { "refreshLCMode ${response}" }
                refreshNodeListener.stopRefresh()
                response?.let {
                    meshNode.lcMode = response.mode == Mode.On
                    listener.notifyItemChanged(meshNode)
                } ?: listener.showToast(Message.info(responseTimeout))
            }
        }
    }

    fun setLCMode(meshNode: MeshNode, enable: Boolean) {
        val lightLCServer = meshNode.findSigModel(ModelIdentifier.LightLCServer)
        lightLCServer?.let { model ->
            LightControlClient.setMode(
                model.element.address,
                meshNode.node.boundAppKeys.first(),
                true,
                if (enable) Mode.On else Mode.Off,
            ).onFailure {
                listener.showToast(Message.info(it))
                return
            }

            coroutineScope.launch {
                val response = withTimeoutOrNull(defaultTimeout) {
                    LightControlClient.modeResponse.first()
                }
                Logger.debug { "setLCMode ${response}" }

                response?.let {
                    listener.showToast(Message.info("New LC Mode: ${response.mode}"))
                    meshNode.lcMode = response.mode == Mode.On
                    listener.notifyItemChanged(meshNode)
                } ?: listener.showToast(Message.info(responseTimeout))
            }
        }
    }

    fun refreshLCOccupancyMode(
        meshNode: MeshNode,
        refreshNodeListener: DeviceViewHolderBase.RefreshNodeListener,
    ) {
        val lightLCServer = meshNode.findSigModel(ModelIdentifier.LightLCServer)
        lightLCServer?.let { model ->
            LightControlClient.getOccupancyMode(
                model.element.address,
                meshNode.node.boundAppKeys.first(),
            ).onFailure {
                listener.showToast(Message.info(it))
                return
            }
            refreshNodeListener.startRefresh()

            coroutineScope.launch {
                val response = withTimeoutOrNull(defaultTimeout) {
                    LightControlClient.occupancyModeResponse.first()
                }
                Logger.debug { "refreshLCOccupancyMode ${response}" }
                refreshNodeListener.stopRefresh()
                response?.let {
                    meshNode.lcOccupancyMode =
                        response.mode == OccupancyMode.StandbyTransitionEnabled
                    listener.notifyItemChanged(meshNode)
                } ?: listener.showToast(Message.info(responseTimeout))
            }
        }
    }

    fun setLCOccupancyMode(meshNode: MeshNode, enable: Boolean) {
        val lightLCServer = meshNode.findSigModel(ModelIdentifier.LightLCServer)
        lightLCServer?.let { model ->
            LightControlClient.setOccupancyMode(
                model.element.address,
                meshNode.node.boundAppKeys.first(),
                true,
                if (enable) Mode.On else Mode.Off,
            ).onFailure {
                listener.showToast(Message.info(it))
                return
            }

            coroutineScope.launch {
                val response = withTimeoutOrNull(defaultTimeout) {
                    LightControlClient.occupancyModeResponse.first()
                }
                Logger.debug { "setLCOccupancyMode ${response}" }

                response?.let {
                    listener.showToast(Message.info("New LC Occupancy Mode: ${response.mode}"))
                    meshNode.lcOccupancyMode =
                        response.mode == OccupancyMode.StandbyTransitionEnabled
                    listener.notifyItemChanged(meshNode)
                } ?: listener.showToast(Message.info(responseTimeout))
            }
        }
    }

    fun refreshLCLightOnOff(
        meshNode: MeshNode,
        refreshNodeListener: DeviceViewHolderBase.RefreshNodeListener,
    ) {
        val lightLCServer = meshNode.findSigModel(ModelIdentifier.LightLCServer)
        lightLCServer?.let { model ->
            LightControlClient.getOnOff(
                model.element.address,
                meshNode.node.boundAppKeys.first(),
            ).onFailure {
                listener.showToast(Message.info(it))
                return
            }
            refreshNodeListener.startRefresh()

            coroutineScope.launch {
                val response = withTimeoutOrNull(defaultTimeout) {
                    LightControlClient.onOffResponse.first()
                }
                Logger.debug { "refreshLCLightOnOff ${response}" }
                refreshNodeListener.stopRefresh()
                response?.let {
                    meshNode.lcOnOff = response.presentOnOffState == OnOffState.NotOffAndNotStandby
                    listener.notifyItemChanged(meshNode)
                } ?: listener.showToast(Message.info(responseTimeout))
            }
        }
    }

    fun setLCOnOff(meshNode: MeshNode, enable: Boolean) {
        val lightLCServer = meshNode.findSigModel(ModelIdentifier.LightLCServer)
        lightLCServer?.let { model ->
            LightControlClient.setOnOff(
                model.element.address,
                meshNode.node.boundAppKeys.first(),
                true,
                if (enable) OnOffState.NotOffAndNotStandby else OnOffState.OffOrStandby,
                ++transactionId,
            ).onFailure {
                listener.showToast(Message.info(it))
                return
            }

            coroutineScope.launch {
                val response = withTimeoutOrNull(defaultTimeout) {
                    LightControlClient.onOffResponse.first()
                }
                Logger.debug { "setLCOnOff ${response}" }

                response?.let {
                    listener.showToast(Message.info("New LC OnOff Mode: ${response.presentOnOffState}"))
                    meshNode.lcOnOff = response.presentOnOffState == OnOffState.NotOffAndNotStandby
                    listener.notifyItemChanged(meshNode)
                } ?: listener.showToast(Message.info(responseTimeout))
            }
        }
    }

    fun refreshLCProperty(
        meshNode: MeshNode,
        refreshNodeListener: DeviceViewHolderBase.RefreshNodeListener
    ) {
        val lightLCServer = meshNode.findSigModel(ModelIdentifier.LightLCServer)
        lightLCServer?.let { model ->
            LightControlClient.getProperty(
                model.element.address,
                meshNode.node.boundAppKeys.first(),
                meshNode.lcProperty.id,
            ).onFailure {
                listener.showToast(Message.info(it))
                return
            }

            coroutineScope.launch {
                val response = withTimeoutOrNull(defaultTimeout) {
                    LightControlClient.propertyResponse.first()
                }
                Logger.debug { "refreshLCProperty ${response}" }
                refreshNodeListener.stopRefresh()
                response?.let {
                    meshNode.lcPropertyValue =
                        meshNode.lcProperty.convertToValue(response.propertyValue)
                    listener.notifyItemChanged(meshNode)
                } ?: listener.showToast(Message.info(responseTimeout))
            }
            refreshNodeListener.startRefresh()
        }
    }

    fun setLCProperty(meshNode: MeshNode, data: String) {
        if (data.isBlank()) {
            listener.showToast(Message.info(R.string.device_adapter_lc_input_blank))
            return
        }

        val property = meshNode.lcProperty
        val propertyId = property.id
        val propertyData: ByteArray
        try {
            propertyData = property.convertToByteArray(data)
        } catch (e: LightLCProperty.LightLCPropertyValueRangeException) {
            val factor = property.characteristic.factor
            val min = property.characteristic.min?.div(factor)
            val max = property.characteristic.max?.div(factor)
            if (isInteger(property)) {
                listener.showToast(
                    Message.info {
                        getString(
                            R.string.device_adapter_lc_input_out_of_range,
                            min?.toInt().toString(), max?.toInt().toString()
                        )
                    })
            } else {
                listener.showToast(
                    Message.info {
                        getString(
                            R.string.device_adapter_lc_input_out_of_range, min.toString(),
                            max.toString()
                        )
                    })
            }
            return
        } catch (e: NumberFormatException) {
            listener.showToast(Message.info(R.string.device_adapter_lc_input_format_wrong))
            return
        }

        val lightLCServer = meshNode.findSigModel(ModelIdentifier.LightLCServer)
        lightLCServer?.let { model ->
            LightControlClient.setProperty(
                model.element.address,
                meshNode.node.boundAppKeys.first(),
                true,
                propertyId,
                propertyData,
            ).onFailure {
                listener.showToast(Message.info(it))
                return
            }
            coroutineScope.launch {
                val response = withTimeoutOrNull(defaultTimeout) {
                    LightControlClient.propertyResponse.first()
                }
                Logger.debug { "setLCProperty ${response}" }

                response?.let {
                    meshNode.lcPropertyValue = property.convertToValue(response.propertyValue)
                    listener.notifyItemChanged(meshNode)
                    listener.showToast(Message.info("New LC Property value: ${meshNode.lcPropertyValue}"))
                } ?: listener.showToast(Message.info(responseTimeout))
            }
        }
    }

    private fun isInteger(property: LightLCProperty): Boolean {
        val typeCheck = property.characteristic.firstElement ?: return false
        return typeCheck % 1.0 == 0.0
    }

    companion object {
        private var transactionId: UByte = 0u
    }
}
