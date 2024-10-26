/*
 * Copyright © 2021 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Dialogs

import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.functionality_control.firmware_distribution.DistributorStatus
import com.siliconlab.bluetoothmesh.adk.functionality_control.firmware_distribution.UploadPhase
import com.siliconlab.bluetoothmesh.adk.functionality_control.firmware_distribution.UploadResponse
import com.siliconlab.bluetoothmesh.adk.functionality_control.standaloneupdater.Firmware
import com.siliconlabs.bluetoothmesh.App.FirmwareUpdater.FirmwareUpdater
import com.siliconlabs.bluetoothmesh.App.FirmwareUpdater.UpdateResult
import com.siliconlabs.bluetoothmesh.App.Fragments.Distributor.Utils.buildFirmwareUpdater
import com.siliconlabs.bluetoothmesh.App.Models.AppState
import com.siliconlabs.bluetoothmesh.R
import kotlinx.coroutines.*
import org.tinylog.kotlin.Logger
import kotlin.coroutines.CoroutineContext

class UploadFirmwarePresenter(
    private val dialog: UploadFirmwareDialog,
    private val selectedDistributor : Node,
    private val nodesToUpdate: Set<Node>,
) : UploadFirmwareDialog.UploadFirmwareListener, CoroutineScope {
    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = job + Dispatchers.Main

    private lateinit var firmwareUpdater: FirmwareUpdater
    private lateinit var firmware: Firmware
    private val delayInMilliseconds = 5000L
    private var uploadPhase = UploadPhase.IDLE
    private var uploadStatusJob: Job? = null

    override fun startUploadProcess() {
        firmware = AppState.firmware ?: run {
            // invalid AppState
            dialog.uploadFailed(dialog.getString(R.string.distribution_error_firmware_null))
            return
        }

        firmwareUpdater = buildFirmwareUpdater(selectedDistributor)
        startUpdate()
        getUploadStatusCyclically()
    }

    private fun getUploadStatusCyclically() {
        uploadStatusJob = launch {
            while (isActive) {
                var uploadStatus: UploadResponse? = null
                launch {
                    uploadStatus = firmwareUpdater.getUploadStatus()
                    uploadStatus?.let {
                        Logger.debug { "uploadStatus ${it.phase} ${it.progress} ${it.status}" }
                        uploadPhase = it.phase
                        when (it.phase) {
                            UploadPhase.TRANSFER_IN_PROGRESS -> {
                                dialog.uploadInProgress(it.progress!!)
                            }
                            UploadPhase.TRANSFER_ERROR -> {
                                val errorMessage = getErrorMessage(it.status)
                                dialog.uploadFailed(errorMessage)

                            }
                            else -> Unit
                        }
                    }
                }
                delay(delayInMilliseconds)
                if (uploadStatus == null) firmwareUpdater.abortUploadResponse()
            }
        }
    }

    private fun startUpdate() {
        launch {
            val updateResult = try {
                firmwareUpdater.startUpdate(nodesToUpdate, firmware)
            } catch (e: Exception) {
                UpdateResult.UploadFailed
            }
            Logger.debug { "updateResult $updateResult" }

            withContext(Dispatchers.Main) {
                println(" startUpdate ${updateResult.name}")
                println(" startUpdate ${updateResult.ordinal}")
               handleUpdateStatus(updateResult)
            }
        }
    }

    private fun handleUpdateStatus(updateResult: UpdateResult) {
        when (updateResult) {
            UpdateResult.Success -> {
                dialog.uploadSuccess()
            }
            UpdateResult.ConcurrentCall -> {
                startUpdate()
            }
            UpdateResult.TooManyNodes -> {
                uploadStatusJob?.cancel()
                dialog.uploadFailed(dialog.getString(R.string.update_error_too_many_nodes))
            }
            UpdateResult.TooBigFirmware -> {
                uploadStatusJob?.cancel()
                dialog.uploadFailed(dialog.getString(R.string.update_error_too_large_firmware))
            }
            UpdateResult.UploadFailed -> {
                uploadStatusJob?.cancel()
                dialog.uploadFailed(dialog.getString(R.string.transfer_error))
            }
        }
    }

    private fun getErrorMessage(status: DistributorStatus): String {
        return when (status) {
            DistributorStatus.SUCCESS -> dialog.getString(R.string.transfer_error)
            DistributorStatus.INSUFFICIENT_RESOURCES -> dialog.getString(R.string.distribution_error_insufficient_resources)
            DistributorStatus.WRONG_PHASE -> dialog.getString(R.string.distribution_error_wrong_phase)
            DistributorStatus.INTERNAL_ERROR -> dialog.getString(R.string.distribution_error_internal_error)
            DistributorStatus.FIRMWARE_NOT_FOUND -> dialog.getString(R.string.distribution_error_firmware_not_found)
            DistributorStatus.INVALID_APP_KEY_INDEX -> dialog.getString(R.string.distribution_error_invalid_app_key_index)
            DistributorStatus.RECEIVERS_LIST_EMPTY -> dialog.getString(R.string.distribution_error_receivers_list_empty)
            DistributorStatus.BUSY_WITH_DISTRIBUTION -> dialog.getString(R.string.distribution_error_busy_with_distribution)
            DistributorStatus.BUSY_WITH_UPLOAD -> dialog.getString(R.string.distribution_error_busy_with_upload)
            DistributorStatus.URI_NOT_SUPPORTED -> dialog.getString(R.string.distribution_error_uri_not_supported)
            DistributorStatus.URI_MALFORMED -> dialog.getString(R.string.distribution_error_uri_malformed)
            DistributorStatus.URI_UNREACHABLE -> dialog.getString(R.string.distribution_error_uri_unreachable)
            DistributorStatus.NEW_FIRMWARE_NOT_AVAILABLE -> dialog.getString(R.string.distribution_error_new_firmware_not_available)
            DistributorStatus.SUSPEND_FAILED -> dialog.getString(R.string.distribution_error_suspend_failed)
        }
    }

    override fun cancelUploadProcess() {
        if (uploadPhase == UploadPhase.TRANSFER_IN_PROGRESS) {
            launch {
                var cancelled = false

                run {
                    repeat(5) {
                        val cancelUploadStatus = try {
                            firmwareUpdater.cancelUpload()
                        } catch (e: Exception) {
                            null
                        }

                        cancelled = cancelUploadStatus?.let {
                            cancelUploadStatus.phase == UploadPhase.IDLE
                        } ?: false

                        if (cancelled) {
                            return@run
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    dialog.handleCancelResult(cancelled)
                }
            }
        } else {
            dialog.handleCancelResult(false)
        }
    }

    override fun cleanup() {
        uploadPhase = UploadPhase.IDLE
        job.cancelChildren()
    }
}