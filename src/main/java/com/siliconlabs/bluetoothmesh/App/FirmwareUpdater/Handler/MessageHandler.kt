/*
 * Copyright © 2021 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.FirmwareUpdater.Handler

import com.siliconlab.bluetoothmesh.adk.data_model.address.Address
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.AppKey
import com.siliconlab.bluetoothmesh.adk.functionality_control.blob_transfer.Blob
import com.siliconlab.bluetoothmesh.adk.functionality_control.blob_transfer.BlobTransfer
import com.siliconlab.bluetoothmesh.adk.functionality_control.blob_transfer.Mode
import com.siliconlab.bluetoothmesh.adk.functionality_control.firmware_distribution.CapabilitiesResponse
import com.siliconlab.bluetoothmesh.adk.functionality_control.firmware_distribution.DistributionResponse
import com.siliconlab.bluetoothmesh.adk.functionality_control.firmware_distribution.FirmwareDistribution
import com.siliconlab.bluetoothmesh.adk.functionality_control.firmware_distribution.FirmwareReceiver
import com.siliconlab.bluetoothmesh.adk.functionality_control.firmware_distribution.FirmwareResponse
import com.siliconlab.bluetoothmesh.adk.functionality_control.firmware_distribution.ReceiversResponse
import com.siliconlab.bluetoothmesh.adk.functionality_control.firmware_distribution.UpdatePolicy
import com.siliconlab.bluetoothmesh.adk.functionality_control.firmware_distribution.UpdatingNodesResponse
import com.siliconlab.bluetoothmesh.adk.functionality_control.firmware_distribution.UploadResponse
import com.siliconlab.bluetoothmesh.adk.functionality_control.firmware_update.FirmwareId
import com.siliconlab.bluetoothmesh.adk.functionality_control.standaloneupdater.Firmware
import com.siliconlab.bluetoothmesh.adk.isFailure
import com.siliconlabs.bluetoothmesh.App.FirmwareUpdater.DistributorConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.suspendCoroutine

internal class MessageHandler(
    private val address: Address,
    private val appKey: AppKey,
    initiatorTtl: Int,
    private val distributorConfiguration: DistributorConfiguration
) {
    private val singleContinuationHandlers = SingleContinuationHandlers()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    init {
        FirmwareDistribution.ttl = initiatorTtl
        FirmwareDistribution.appKey = appKey
        coroutineScope.launch {
            FirmwareDistribution.distributionResponse.collect {
                singleContinuationHandlers.proceed(it)
            }
        }

        coroutineScope.launch {
            FirmwareDistribution.updatingNodesResponse.collect {
                singleContinuationHandlers.proceed(it)
            }
        }
        coroutineScope.launch {
            FirmwareDistribution.receiversResponse.collect {
                singleContinuationHandlers.proceed(it)
            }
        }
        coroutineScope.launch {
            FirmwareDistribution.capabilitiesResponse.collect {
                singleContinuationHandlers.proceed(it)
            }
        }
        coroutineScope.launch {
            FirmwareDistribution.uploadResponse.collect {
                singleContinuationHandlers.proceed(it)
            }
        }
        coroutineScope.launch {
            FirmwareDistribution.firmwareResponse.collect {
                singleContinuationHandlers.proceed(it)
            }
        }
        coroutineScope.launch {
            FirmwareDistribution.uploadCompleteResponse.collect {
                singleContinuationHandlers.proceed(result = true)
            }
        }
        coroutineScope.launch {
            FirmwareDistribution.uploadFailedResponse.collect {
                singleContinuationHandlers.proceed(result = false)
            }
        }
    }

    //region distribution
    suspend fun startDistribution(firmwareIndex: Int) = suspendCoroutine<DistributionResponse?> {
        if (singleContinuationHandlers.assign(it)) {
            FirmwareDistribution.startDistribution(
                address,
                appKey,
                distributorConfiguration.ttl,
                distributorConfiguration.timeoutBase,
                Mode.PUSH,
                UpdatePolicy.VERIFY_AND_APPLY,
                firmwareIndex,
            )
        }
    }

    suspend fun getDistribution() = suspendCancellableCoroutine<DistributionResponse?>  {
        if (singleContinuationHandlers.assign(it)) {
            FirmwareDistribution.getDistribution(address) }
    }

    suspend fun applyDistribution() = suspendCoroutine<DistributionResponse?> {
        if (singleContinuationHandlers.assign(it)) {
            FirmwareDistribution.applyDistribution(address)
        }
    }

    suspend fun cancelDistribution() = suspendCoroutine<DistributionResponse?> {
        if (singleContinuationHandlers.assign(it)) {
            FirmwareDistribution.cancelDistribution(address)
        }
    }
    //endregion

    //region firmware
    suspend fun getFirmware(firmware: Firmware) = suspendCoroutine<FirmwareResponse?> {
        if (singleContinuationHandlers.assign(it)) {
            FirmwareDistribution.getFirmware(address, firmware.id)
        }
    }

    suspend fun getFirmwareId(firmwareIndex: Int) = suspendCoroutine<FirmwareResponse?> {
        if (singleContinuationHandlers.assign(it)) {
            FirmwareDistribution.getFirmware(address, firmwareIndex)
        }
    }

    suspend fun deleteFirmware(distributionAddress: Address, firmwareId: FirmwareId) =
        suspendCoroutine<FirmwareResponse?> {
            if (singleContinuationHandlers.assign(it)) {
                FirmwareDistribution.deleteFirmware(distributionAddress, firmwareId)
            }
        }

    suspend fun deleteAllFirmwares() = suspendCoroutine<FirmwareResponse?> {
        if (singleContinuationHandlers.assign(it)) {
            FirmwareDistribution.deleteAllFirmwares(address)
        }
    }
    //endregion

    //region upload
    suspend fun getUpload() = suspendCoroutine<UploadResponse?> {
        if (singleContinuationHandlers.assign(it))
            FirmwareDistribution.getUpload(address)
    }

    suspend fun startUpload(
        ttl: Int,
        timeoutBase: Int,
        blob: Blob,
        firmwareId: FirmwareId,
        metadata: ByteArray? = null
    ) = suspendCoroutine<UploadResponse?> {
        if (singleContinuationHandlers.assign(it)) {
            FirmwareDistribution.startUpload(address, ttl, timeoutBase, blob, firmwareId, metadata)
        }
    }

    suspend fun cancelUpload() = suspendCoroutine<UploadResponse?> {
        if (singleContinuationHandlers.assign(it)) {
            FirmwareDistribution.cancelUpload()
        }
    }

    suspend fun finishUpload() = suspendCoroutine<Boolean?> {
        singleContinuationHandlers.assign(it)
    }
    //endregion

    //region capabilities
    suspend fun getCapabilities() = suspendCoroutine<CapabilitiesResponse?> {
        if (singleContinuationHandlers.assign(it)) {
            FirmwareDistribution.getCapabilities(address)
        }
    }
    //endregion

    //region receivers
    suspend fun getUpdatingNodes(firstReceiverIndex: Int = 0) =
        suspendCoroutine<UpdatingNodesResponse?> {
            val receiversMaxLimit = 64
            if (singleContinuationHandlers.assign(it)) {
                FirmwareDistribution.getUpdatingNodes(
                    address,
                    firstReceiverIndex,
                    receiversMaxLimit
                )
            }
        }

    suspend fun addReceivers(receivers: List<FirmwareReceiver>) =
        suspendCoroutine<ReceiversResponse?> {
            if (singleContinuationHandlers.assign(it)) {
                FirmwareDistribution.addReceivers(address, receivers)
            }
        }

    suspend fun deleteAllReceivers() = suspendCoroutine<ReceiversResponse?> {
        if (singleContinuationHandlers.assign(it)) {
            FirmwareDistribution.deleteAllReceivers(address)
        }
    }
    //endregion

    //region transfer
    fun startTransfer(firmware: Firmware) {
        BlobTransfer.startTransfer(firmware.blob) {
            // println("DFU startTransfer ${it.isFailure()}")
            val wal = it.toString()
            if(wal.indexOf("0x0001") < 0) {
                if (it.isFailure()) singleContinuationHandlers.proceed(false)
            }
        }
    }

    //endregion
    fun abortUploadResponse() {
        singleContinuationHandlers.abortUploadResponse()
    }

    fun abortDistributionResponse() {
        singleContinuationHandlers.abortDistributionResponse()
    }

    fun abortGettingUpdatingNodes() {
        singleContinuationHandlers.abortGettingUpdatingNodes()
    }
}