/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic

import com.siliconlab.bluetoothmesh.adk.data_model.model.ModelIdentifier
import com.siliconlab.bluetoothmesh.adk.data_model.model.SigModel
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.functionality_control.advertisement_extension.*
import com.siliconlab.bluetoothmesh.adk.functionality_control.advertisement_extension.AdvertisementExtension.Companion.findSilabsConfigurationServerModel
import com.siliconlab.bluetoothmesh.adk.model_control.LocalModelTransmission
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

object AdvertisementExtensionHelper {
    val timeout = 10.seconds

    fun Node.supportsAdvertisementExtension(): Boolean =
            boundAppKeys.isNotEmpty() && findSilabsConfigurationServerModel()?.boundAppKeys?.contains(
                boundAppKeys.first()
            ) ?: false

    fun setUsageForLocalBlobClient(enable: Boolean) {
        LocalModelTransmission.useExtendedPackets(
                ModelIdentifier.BlobTransferClient,
                enable
        )
    }

    fun setRemoteNodeConfiguration(node: Node) {
        val appKey = node.boundAppKeys.first()
        AdvertisementExtension(appKey).setTxConfiguration(
            node,
            TxConfiguration(TxConfiguration.TransmissionMode.TwoMegabit)
        )
    }

    suspend fun fetchRemoteNodeConfiguration(): TxConfigurationResponse? =
            withTimeoutOrNull(timeout) {
                AdvertisementExtension.txConfigurationResponse.first()
            }

    fun isAdvertisementExtensionOnNodeSetCorrectly(response: TxConfigurationResponse): Boolean =
            response.status == ConfigurationStatus.Ok && response.txConfiguration.mode == TxConfiguration.TransmissionMode.TwoMegabit

    fun setRemoteNodeNetworkPDUSize(node: Node) {
        val appKey = node.boundAppKeys.first()
        AdvertisementExtension(node.boundAppKeys.first()).setNetworkPduMaxSize(
            node,
            LocalModelTransmission.getPduMaxSize()
        )
    }

    suspend fun fetchRemoteNodeNetworkPDUSize(): NetworkPduMaxSizeResponse? =
            withTimeoutOrNull(timeout) {
                AdvertisementExtension.networkPduMaxSizeResponse.first()
            }

    fun isNetworkPDUSetCorrectly(response: NetworkPduMaxSizeResponse) =
            response.status == ConfigurationStatus.Ok && response.size == LocalModelTransmission.getPduMaxSize()

    fun setUsageForRemoteBlobClient(node: Node, enable: Boolean) {
        val model = node.findBlobClientModel()!!
        val appKey = node.boundAppKeys.first()
        AdvertisementExtension(appKey).run {
            if (enable) {
                enableTx(model)
            } else {
                disableTx(model)
            }
        }
    }

    fun getUsageForRemoteBlobClient(node: Node) {
        val model = node.findBlobClientModel()!!
        val appKey = node.boundAppKeys.first()
        AdvertisementExtension(appKey).checkModelTxEnablement(model)
    }

    private fun Node.findBlobClientModel(): SigModel? =
        elements.flatMap { it!!.sigModels }
            .find { it.modelIdentifier == ModelIdentifier.BlobTransferClient }

    suspend fun fetchRemoteModelState(): ModelTxEnablementResponse? =
            withTimeoutOrNull(timeout) {
                AdvertisementExtension.modelTxEnablementResponse.first()
            }
}