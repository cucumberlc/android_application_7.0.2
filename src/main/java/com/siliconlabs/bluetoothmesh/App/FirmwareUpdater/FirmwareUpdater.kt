/*
 * Copyright © 2021 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.FirmwareUpdater

import com.siliconlab.bluetoothmesh.adk.data_model.element.Element
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.AppKey
import com.siliconlab.bluetoothmesh.adk.functionality_control.firmware_distribution.DistributionResponse
import com.siliconlab.bluetoothmesh.adk.functionality_control.firmware_distribution.FirmwareReceiver
import com.siliconlab.bluetoothmesh.adk.functionality_control.firmware_distribution.UploadResponse
import com.siliconlab.bluetoothmesh.adk.functionality_control.standaloneupdater.Firmware
import com.siliconlabs.bluetoothmesh.App.FirmwareUpdater.Handler.MessageHandler
import com.siliconlabs.bluetoothmesh.App.FirmwareUpdater.UpdateResult.ConcurrentCall
import com.siliconlabs.bluetoothmesh.App.FirmwareUpdater.UpdateResult.Success
import com.siliconlabs.bluetoothmesh.App.FirmwareUpdater.UpdateResult.TooBigFirmware
import com.siliconlabs.bluetoothmesh.App.FirmwareUpdater.UpdateResult.TooManyNodes
import com.siliconlabs.bluetoothmesh.App.FirmwareUpdater.UpdateResult.UploadFailed

class FirmwareUpdater(
    element: Element,
    appKey: AppKey,
    private val initiatorConfiguration: InitiatorConfiguration,
    distributorConfiguration: DistributorConfiguration
) {
    private val messageHandler = MessageHandler(
            element.address,
            appKey,
            initiatorConfiguration.ttl,
            distributorConfiguration
    )

    private companion object {
        const val RECEIVER_FIRMWARE_INDEX = 0
    }

    suspend fun startUpdate(nodes: Set<Node>, firmware: Firmware): UpdateResult {
        messageHandler.getCapabilities()?.let { capabilities ->
            if (capabilities.maxReceiverListSize < nodes.size) return TooManyNodes

            messageHandler.getFirmware(firmware)?.let { firmwareStatus ->
                val distributorFirmwareIndex = firmwareStatus.index ?: run {
                    if (capabilities.maxFirmwareSize < firmware.blob.data.size) return TooBigFirmware
                    if (capabilities.remainingUploadSpace < firmware.blob.data.size || capabilities.maxFirmwareListSize == firmwareStatus.entriesNumber) {
                        messageHandler.deleteAllFirmwares() ?: return ConcurrentCall
                    }
                    messageHandler.startUpload(
                            initiatorConfiguration.ttl,
                            initiatorConfiguration.timeoutBase,
                            firmware.blob,
                            firmware.id,
                            firmware.metadata
                    ) ?: return ConcurrentCall
                    messageHandler.startTransfer(firmware)
                    messageHandler.finishUpload()?.let { success ->
                        if (!success) return UploadFailed
                    } ?: return ConcurrentCall

                    messageHandler.getFirmware(firmware)?.let {
                        it.index!!
                    } ?: return ConcurrentCall
                }

                messageHandler.deleteAllReceivers() ?: return ConcurrentCall
                messageHandler.addReceivers(nodes.map { FirmwareReceiver(it, RECEIVER_FIRMWARE_INDEX) }) ?: return ConcurrentCall
                messageHandler.startDistribution(distributorFirmwareIndex) ?: return ConcurrentCall
            } ?: return ConcurrentCall
        } ?: return ConcurrentCall
        return Success
    }

    suspend fun cancelUpload(): UploadResponse? = messageHandler.cancelUpload()

    suspend fun cancelDistribution(): DistributionResponse? = messageHandler.cancelDistribution()

    suspend fun getUploadStatus() = messageHandler.getUpload()

    suspend fun getDistributionStatus() = messageHandler.getDistribution()

    suspend fun getUpdatingNodes() = messageHandler.getUpdatingNodes()

    suspend fun applyFirmware() = messageHandler.applyDistribution()

    suspend fun getFirmwareId(firmwareIndex: Int) = messageHandler.getFirmwareId(firmwareIndex)

    fun abortUploadResponse() = messageHandler.abortUploadResponse()

    fun abortDistributionResponse() = messageHandler.abortDistributionResponse()

    fun abortGettingUpdatingNodes() = messageHandler.abortGettingUpdatingNodes()
}