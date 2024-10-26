/*
 * Copyright © 2021 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Distributor.Update

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.functionality_control.blob_transfer.Blob
import com.siliconlab.bluetoothmesh.adk.functionality_control.firmware_distribution.*
import com.siliconlab.bluetoothmesh.adk.functionality_control.firmware_update.FirmwareId
import com.siliconlab.bluetoothmesh.adk.functionality_control.standaloneupdater.Firmware
import com.siliconlabs.bluetoothmesh.App.BasePresenter
import com.siliconlabs.bluetoothmesh.App.Fragments.Distributor.Utils.buildFirmwareUpdater
import com.siliconlabs.bluetoothmesh.App.Fragments.Distributor.Utils.hasFailed
import com.siliconlabs.bluetoothmesh.App.Logic.NetworkConnectionListener
import com.siliconlabs.bluetoothmesh.App.Logic.NetworkConnectionLogic
import com.siliconlabs.bluetoothmesh.App.Models.AppState
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.emptyJob
import com.siliconlabs.bluetoothmesh.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import org.tinylog.kotlin.Logger
import javax.inject.Inject

@HiltViewModel
class UpdateNodesPresenter @Inject constructor(
        private val appContext: Application,
        private val networkConnectionLogic: NetworkConnectionLogic,
        selectedDistributor : Node
) : BasePresenter<UpdateNodesView>(), NetworkConnectionListener {
    private val delayInMilliseconds = 6000L

    private var updatingNodes: Collection<UpdatingNode> = emptyList()
    private val firmwareUpdater = buildFirmwareUpdater(selectedDistributor)

    private var getFirmwareReceiversJob = emptyJob()
    private var firmwareId: FirmwareId? = AppState.firmware?.id
    private var isFirmwareIdReceived = false

    fun startDistribution() {
        startGettingDistributorStatus()
        startGettingFirmwareReceivers()
    }

    private fun startGettingDistributorStatus() {
        viewModelScope.launch {
            var stopProcess = false

            while (!stopProcess) {
                var distributionStatus: DistributionResponse? = null

                launch {
                    distributionStatus = firmwareUpdater.getDistributionStatus()
                    distributionStatus?.let {
                        Logger.debug { "distributionStatus: phase ${it.phase} status ${it.status}" }
                        withContext(Dispatchers.Main) {
                            view?.showDistributionPhase(it.phase)
                        }

                        if (it.phase == DistributorPhase.COMPLETED || it.phase == DistributorPhase.FAILED) {
                            stopProcess = true
                            getFirmwareReceiversJob.cancelChildren()
                        }

                        if (!isFirmwareIdReceived) {
                            it.parameters?.let { distributionParameters ->
                                fetchFirmwareId(distributionParameters)
                            }
                        }
                    }
                }
                delay(delayInMilliseconds)
                if (distributionStatus == null) firmwareUpdater.abortDistributionResponse()
            }
        }
    }

    private fun fetchFirmwareId(distributionParameters: DistributionParameters) {
        viewModelScope.launch {
            val firmwareStatus = firmwareUpdater.getFirmwareId(distributionParameters.firmwareIndex)

            firmwareStatus?.firmwareId!!.let {
                firmwareId = it

                withContext(Dispatchers.Main) {
                    view?.showFirmwareId(it.toString())
                }
                isFirmwareIdReceived = true
            }
        }
    }

    private fun startGettingFirmwareReceivers() {
        getFirmwareReceiversJob = viewModelScope.launch {
            while (true) {
                var updatingNodesResponse: UpdatingNodesResponse? = null

                launch {
                    updatingNodesResponse = firmwareUpdater.getUpdatingNodes()
                    updatingNodesResponse?.let {
                        Logger.debug { "receiversStatus: ${it.updatingNodes}" }
                        withContext(Dispatchers.Main) {
                            updatingNodes = it.updatingNodes

                            view?.showUpdatingNodes(updatingNodes)
                        }
                    }
                }
                delay(delayInMilliseconds)
                if (updatingNodesResponse == null) firmwareUpdater.abortGettingUpdatingNodes()
            }
        }
    }

    fun cancelDistribution() {
        viewModelScope.launch {
            var cancelSuccess = false
            repeat(5) {
                val cancelDistributionStatus = try {
                    firmwareUpdater.cancelDistribution()
                } catch (e: Exception) {
                    null
                }

                val phase = cancelDistributionStatus?.phase
                cancelSuccess = phase == DistributorPhase.IDLE || phase == DistributorPhase.FAILED
                Logger.debug { "cancelDistributionStatus $cancelDistributionStatus" }

                withContext(Dispatchers.Main) {
                    cancelDistributionStatus?.let {
                        view?.showDistributionPhase(it.phase)
                    }
                }
            }

            if (!cancelSuccess) {
                withContext(Dispatchers.Main) {
                    view?.showWarningToast(appContext.getString(R.string.cancel_of_distribution_failed))
                    view?.exitDeviceFirmwareUpdate()
                }
            }
        }
    }

    fun resetDistributor() {
        viewModelScope.launch {
            try {
                firmwareUpdater.cancelDistribution()
            } catch (_: Exception) { }
            withContext(Dispatchers.Main) {
                view?.exitDeviceFirmwareUpdate()
            }
        }
    }

    fun startUpdateAgainOnFailedNodes() {
        firmwareId?.let {
            val failedNodes = updatingNodes.filter { entry ->
                entry.hasFailed()
            }.mapNotNull { it.node }.toSet()

            viewModelScope.launch {
                if (failedNodes.isNotEmpty()) {
                    val firmware = Firmware(it, Blob(byteArrayOf()), null)
                    firmwareUpdater.startUpdate(failedNodes, firmware)
                    startDistribution()
                } else {
                    withContext(Dispatchers.Main) {
                        view?.showWarningToast(appContext.getString(R.string.no_failed_nodes_detected))
                    }
                }
            }
        } ?: viewModelScope.launch(Dispatchers.Main) {
            view?.showWarningToast(appContext.getString(R.string.firmware_id_has_not_been_fetched))
        }
    }

    override fun onResume() {
        networkConnectionLogic.addListener(this)
    }

    override fun onPause() {
        networkConnectionLogic.removeListener(this)
    }

    override fun disconnected() {
        view?.showDisconnectionDialog()
    }

    override fun onCleared() {
        super.onCleared()
        updatingNodes = emptyList()
        firmwareId = null
        isFirmwareIdReceived = false
    }
}