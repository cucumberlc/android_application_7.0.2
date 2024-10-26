/*
 * Copyright © 2021 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Distributor.UpdateStatusChecker

import androidx.lifecycle.viewModelScope
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.functionality_control.firmware_distribution.DistributionResponse
import com.siliconlab.bluetoothmesh.adk.functionality_control.firmware_distribution.DistributorPhase
import com.siliconlabs.bluetoothmesh.App.Fragments.Common.ViewModelWithMessages
import com.siliconlabs.bluetoothmesh.App.Fragments.Distributor.Utils.buildFirmwareUpdater
import com.siliconlabs.bluetoothmesh.App.Logic.ConnectionState
import com.siliconlabs.bluetoothmesh.App.Logic.NetworkConnectionLogic
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.emptyJob
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.tinylog.kotlin.Logger
import javax.inject.Inject

@HiltViewModel
class UpdateStatusCheckerViewModel @Inject constructor(
    private val networkConnectionLogic: NetworkConnectionLogic,
    val distributor: Node,
) : ViewModelWithMessages() {
    private val firmwareUpdater by lazy { buildFirmwareUpdater(distributor) }
    private var distributorPhaseUpdateJob = emptyJob()

    private val _status = MutableStateFlow<Status>(Status.INIT)
    val status = _status.asStateFlow()

    init {
        if (!networkConnectionLogic.isConnected()) {
            Logger.debug { "distributor status check failed (not connected to network)" }
            _status.value = Status.DISCONNECTED
        } else {
            distributorPhaseUpdateJob = viewModelScope.launch {
                checkDistributionPhase()
            }
        }
    }

    private suspend fun checkDistributionPhase() {
        coroutineScope {
            launch { observeNetworkConnection() }

            delay(DISTRIBUTION_TIMEOUT_MS) // used ONLY for smooth transition between fragments

            var distributionResponse: DistributionResponse? = null
            repeat(NUMBER_OF_REPETITIONS) {
                /*distributionResponse = withTimeoutOrNull(DISTRIBUTION_TIMEOUT_MS) {
                    firmwareUpdater.getDistributionStatus()
                }*/
                val resAction = async { firmwareUpdater.getDistributionStatus() }
                distributionResponse =
                    withTimeoutOrNull(DISTRIBUTION_TIMEOUT_MS) { resAction.await() }

                Logger.debug { "distributorStatusResponse: $distributionResponse" }
                distributionResponse?.let {
                    _status.value = Status.Phase(it.phase)
                    distributorPhaseUpdateJob.cancel()
                }
            }

            if (distributionResponse == null) _status.value = Status.TIMEOUT
        }
    }

    private suspend fun observeNetworkConnection() {
        networkConnectionLogic.currentStateFlow.first {
            it == ConnectionState.DISCONNECTED
        }
        Logger.debug { "disconnected from network" }
        _status.value = Status.DISCONNECTED
        distributorPhaseUpdateJob.cancel()
    }

    companion object {
        private const val DISTRIBUTION_TIMEOUT_MS = 2_000L
        private const val NUMBER_OF_REPETITIONS = 3
    }

    sealed class Status {
        object INIT : Status()
        object TIMEOUT : Status()
        object DISCONNECTED : Status()
        data class Phase(val phase: DistributorPhase) : Status()
    }
}