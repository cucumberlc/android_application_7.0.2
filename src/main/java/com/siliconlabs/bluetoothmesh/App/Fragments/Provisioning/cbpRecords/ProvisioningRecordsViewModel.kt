/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Provisioning.cbpRecords

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.siliconlab.bluetoothmesh.adk.functionality_control.cbp.CertificateValidationFailure
import com.siliconlabs.bluetoothmesh.App.Fragments.Provisioning.CertificateViewModel
import com.siliconlabs.bluetoothmesh.App.Logic.provision.ProvisioningHelper
import com.siliconlabs.bluetoothmesh.App.Logic.provision.ProvisioningLogic
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.App.Models.ProvisioningRecordsList
import com.siliconlabs.bluetoothmesh.App.Utils.CertificateData
import com.siliconlabs.bluetoothmesh.App.Utils.CertificateFileUtils.CertificateFile
import com.siliconlabs.bluetoothmesh.App.Utils.Message
import com.siliconlabs.bluetoothmesh.App.Utils.asInfo
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.emptyJob
import com.siliconlabs.bluetoothmesh.App.Utils.withTitle
import com.siliconlabs.bluetoothmesh.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.tinylog.kotlin.Logger
import javax.inject.Inject
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

@HiltViewModel
class ProvisioningRecordsViewModel @Inject constructor(
    private val provisioningLogic: ProvisioningLogic,
    application: Application
) : CertificateViewModel(application) {
    private var provisioningJob = emptyJob()
    private var provisioningPause: Continuation<CertificateData>? = null

    private val rootCertificateFile = MutableStateFlow<CertificateFile?>(null)
    val rootCertificateState = certificateStateStateFlowFrom(rootCertificateFile)

    private val _provisioningState = MutableStateFlow(ProvisioningState.READY)
    val provisioningState = _provisioningState.asStateFlow()

    private val _records = MutableStateFlow<ProvisioningRecordsList?>(null)
    val records = _records.asStateFlow()

    val canStartOrContinueProvisioning =
        combine(rootCertificateState, provisioningState, ::canStartOrContinueWithStates)

    private var _provisionedDevice : MeshNode? = null

    /** This field is only not null when [provisioningState] is SUCCESS. */
    val provisionedDevice : MeshNode
        get() = _provisionedDevice!!

    fun setRootCertificateFile(certificateFile: CertificateFile) {
        rootCertificateFile.value = certificateFile
    }

    fun startOrContinueCbpProvisioning() {
        if (!provisioningJob.isActive) startCbpProvisioning()
        else continueCbpProvisioning()
    }

    fun startCbpProvisioning() {
        if (provisioningJob.isActive) return

        provisioningJob = viewModelScope.launch {
            try {
                provisionDevice()
            } finally {
                clearProvisioningValues()
            }
        }
    }

    private fun continueCbpProvisioning() {
        if (!canContinue()) {
            Logger.error { "cannot continue CBP in current state: ${provisioningState.value}" }
            return
        }
        provisioningPause!!.resume(rootCertificateFile.value!!.getLoadedData())
    }

    private suspend fun provisionDevice() {
        _provisioningState.value = ProvisioningState.PREPARING

        val result = provisioningLogic
            .getForCurrentDevice()
            .provisionWithRecords(rootCertificateProvider)

        when (result) {
            is ProvisioningHelper.Failure -> {
                _provisioningState.value = ProvisioningState.READY
                emitMessage(result)
            }
            is ProvisioningHelper.Success -> {
                _provisionedDevice = result.meshNode
                _provisioningState.value = ProvisioningState.SUCCESS
                result.setupState.createMessage()?.let {
                    emitMessage(it.asInfo())
                }
            }
        }
    }

    private val rootCertificateProvider = object : ProvisioningHelper.LazyCertificateProvider {
        override suspend fun getRootCertificate(records: ProvisioningRecordsList): CertificateData {
            val recordsAreValid = records.deviceCertificate != null
            _records.value = records

            return suspendCancellableCoroutine {
                provisioningPause = it

                _provisioningState.value =
                    if (recordsAreValid) ProvisioningState.PAUSED
                    else ProvisioningState.STOP_FAIL
            }.also {
                provisioningPause = null
                _provisioningState.value = ProvisioningState.ACTIVE
            }
        }

        override suspend fun onCertificateDenied(
            certificateValidationFailure: CertificateValidationFailure
        ) {
            emitMessage(
                Message.error(certificateValidationFailure.toString())
                    .withTitle(R.string.cbp_certificate_invalid_title)
            )
        }
    }

    private fun canContinue() = canContinueWithStates(
        rootCertificateState.value, provisioningState.value
    )

    private fun canContinueWithStates(
        rootCertificateState: CertificateState?,
        provisioningState: ProvisioningState
    ) = rootCertificateState?.isReady == true && provisioningState == ProvisioningState.PAUSED

    private fun canStartOrContinueWithStates(
        rootCertificateState: CertificateState?,
        provisioningState: ProvisioningState
    ) = when {
        provisioningState == ProvisioningState.READY -> StartOrContinue.START
        canContinueWithStates(rootCertificateState, provisioningState) -> StartOrContinue.CONTINUE
        else -> StartOrContinue.DISABLED
    }

    private fun clearProvisioningValues() {
        if (provisioningState.value != ProvisioningState.SUCCESS) {
            _provisioningState.value = ProvisioningState.READY
            _records.value = null
            provisioningPause = null
        }
    }

    enum class StartOrContinue {
        START, CONTINUE, DISABLED
    }

    enum class ProvisioningState {
        READY, PREPARING, PAUSED, STOP_FAIL, ACTIVE, SUCCESS
    }
}