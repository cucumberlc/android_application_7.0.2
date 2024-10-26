/*
 * Copyright © 2021 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.FirmwareUpdater.Handler

import com.siliconlab.bluetoothmesh.adk.functionality_control.firmware_distribution.*
import kotlin.coroutines.Continuation

internal class SingleContinuationHandlers {
    private val distributionStatusHandler = SingleContinuationHandler<DistributionResponse>()
    private val receiversListHandler = SingleContinuationHandler<UpdatingNodesResponse>()
    private val receiversStatusHandler = SingleContinuationHandler<ReceiversResponse>()
    private val capabilitiesStatusHandler = SingleContinuationHandler<CapabilitiesResponse>()
    private val uploadStatusHandler = SingleContinuationHandler<UploadResponse>()
    private val firmwareStatusHandler = SingleContinuationHandler<FirmwareResponse>()
    private val uploadFinishedHandler = SingleContinuationHandler<Boolean>()

    @JvmName("assignDistributionStatus")
    fun assign(continuation: Continuation<DistributionResponse?>) =
        distributionStatusHandler.assign(continuation)

    fun proceed(result: DistributionResponse) = distributionStatusHandler.proceed(result)

    @JvmName("assignReceiversList")
    fun assign(continuation: Continuation<UpdatingNodesResponse?>) =
        receiversListHandler.assign(continuation)

    fun proceed(result: UpdatingNodesResponse) = receiversListHandler.proceed(result)

    @JvmName("assignReceiversStatus")
    fun assign(continuation: Continuation<ReceiversResponse?>) =
        receiversStatusHandler.assign(continuation)

    fun proceed(result: ReceiversResponse) = receiversStatusHandler.proceed(result)

    @JvmName("assignCapabilitiesStatus")
    fun assign(continuation: Continuation<CapabilitiesResponse?>) =
        capabilitiesStatusHandler.assign(continuation)

    fun proceed(result: CapabilitiesResponse) = capabilitiesStatusHandler.proceed(result)

    @JvmName("assignUploadStatus")
    fun assign(continuation: Continuation<UploadResponse?>) =
        uploadStatusHandler.assign(continuation)

    fun proceed(result: UploadResponse) = uploadStatusHandler.proceed(result)

    @JvmName("assignFirmwareStatus")
    fun assign(continuation: Continuation<FirmwareResponse?>) =
        firmwareStatusHandler.assign(continuation)

    fun proceed(result: FirmwareResponse) = firmwareStatusHandler.proceed(result)

    @JvmName("assignBoolean")
    fun assign(continuation: Continuation<Boolean?>) = uploadFinishedHandler.assign(continuation)

    fun proceed(result: Boolean) = uploadFinishedHandler.proceed(result)

    fun abortUploadResponse() = uploadStatusHandler.abort()

    fun abortDistributionResponse() = distributionStatusHandler.abort()

    fun abortGettingUpdatingNodes() = receiversListHandler.abort()
}