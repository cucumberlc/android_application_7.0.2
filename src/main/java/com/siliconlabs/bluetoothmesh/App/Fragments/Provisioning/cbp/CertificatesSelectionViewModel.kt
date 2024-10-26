/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Provisioning.cbp

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.siliconlabs.bluetoothmesh.App.Fragments.Provisioning.CertificateViewModel
import com.siliconlabs.bluetoothmesh.App.Logic.provision.ProvisioningHelper
import com.siliconlabs.bluetoothmesh.App.Logic.provision.ProvisioningLogic
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.App.Utils.CertificateFileUtils.CertificateFile
import com.siliconlabs.bluetoothmesh.App.Utils.asInfo
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.emptyJob
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.tinylog.kotlin.Logger
import javax.inject.Inject

@HiltViewModel
class CertificatesSelectionViewModel @Inject constructor(
    private val provisioningLogic: ProvisioningLogic,
    application: Application
) : CertificateViewModel(application) {
    private var provisioningJob = emptyJob()

    private val deviceCertificateFile = MutableStateFlow<CertificateFile?>(null)
    private val rootCertificateFile = MutableStateFlow<CertificateFile?>(null)

    val deviceCertificateState = certificateStateStateFlowFrom(deviceCertificateFile)
    val rootCertificateState = certificateStateStateFlowFrom(rootCertificateFile)

    private val _provisioningState = MutableStateFlow(ProvisioningState.READY)
    val provisioningState = _provisioningState.asStateFlow()

    private var _provisionedDevice : MeshNode? = null

    /** This field is only not null when [provisioningState] is SUCCESS. */
    val provisionedDevice : MeshNode
        get() = _provisionedDevice!!

    fun addCertificateFile(certificateFile: CertificateFile, type: CertificateType) {
        check(!provisioningJob.isActive) { "Already in provision" }
        when (type) {
            CertificateType.ROOT -> rootCertificateFile.value = certificateFile
            CertificateType.DEVICE -> deviceCertificateFile.value = certificateFile
        }
    }

    val canProvisionStart: Flow<Boolean> = combine(
        deviceCertificateState, rootCertificateState, provisioningState,
        ::canProvisionStartWithStates
    )

    fun startProvisioning() {
        if (!canStartProvision()) {
            Logger.error { "cannot start provisioning in current state: ${provisioningState.value}" }
            return
        }

        if (provisioningJob.isActive) return

        provisioningJob = viewModelScope.launch {
            provisionDevice()
        }
    }

    private suspend fun provisionDevice() {
        _provisioningState.value = ProvisioningState.ACTIVE
        val result = provisioningLogic
            .getForCurrentDevice()
            .provisionWithCertificates(
                deviceCertificateFile.value!!.getLoadedData(),
                rootCertificateFile.value!!.getLoadedData()
            )

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

    private fun canStartProvision() = canProvisionStartWithStates(
        deviceCertificateState.value, rootCertificateState.value, provisioningState.value
    )

    private fun canProvisionStartWithStates(
        deviceCertificateState: CertificateState?,
        rootCertificateState: CertificateState?,
        provisioningState: ProvisioningState
    ) = provisioningState == ProvisioningState.READY &&
            listOf(deviceCertificateState, rootCertificateState).all { it?.isReady == true }

    enum class CertificateType {
        ROOT, DEVICE
    }

    enum class ProvisioningState {
        READY, ACTIVE, SUCCESS
    }
}