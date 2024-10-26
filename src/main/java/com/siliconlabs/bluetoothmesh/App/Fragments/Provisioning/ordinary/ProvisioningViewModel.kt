/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Provisioning.ordinary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.siliconlab.bluetoothmesh.adk.BluetoothMesh
import com.siliconlab.bluetoothmesh.adk.provisioning.OobInformation
import com.siliconlabs.bluetoothmesh.App.Fragments.Common.ViewModelWithMessages
import com.siliconlabs.bluetoothmesh.App.Logic.provision.ProvisioningHelper
import com.siliconlabs.bluetoothmesh.App.Logic.provision.ProvisioningLogic
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.App.Models.AppState
import com.siliconlabs.bluetoothmesh.App.Models.DeviceToProvision
import com.siliconlabs.bluetoothmesh.App.Utils.asInfo
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.emptyJob
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProvisioningViewModel @Inject constructor(
    private val provisioningLogic: ProvisioningLogic,
    private val save: SavedStateHandle,
) : ViewModelWithMessages() {
    companion object {
        private const val KEY_DEFAULT_NAME = "KEY_DEFAULT_NAME"
        private const val KEY_NAME = "KEY_NAME"
        private const val KEY_CBP_ENABLED = "KEY_CBP_ENABLED"
    }

    val deviceToProvision = AppState.deviceToProvision!!

    private var provisioningJob = emptyJob()

    private val _provisioningState = MutableStateFlow(ProvisioningState.READY)
    val provisioningState = _provisioningState.asStateFlow()

    private val _selectedSubnet = MutableStateFlow(deviceToProvision.subnet)
    val selectedSubnet = _selectedSubnet.asStateFlow()

    private val cbpEnabled = save.getStateFlow(KEY_CBP_ENABLED, true)
    val deviceNameDefault = save.getStateFlow(KEY_DEFAULT_NAME, deviceToProvision.name)
    val deviceName = save.getStateFlow(KEY_NAME, deviceNameDefault.value)

    val availableSubnets by lazy {
        BluetoothMesh.network.subnets.toList()
    }

    val isNameChangeSupported
        get() = AppState.isProcessingDeviceDirectly()

    val isSubnetSelectionSupported
        get() = AppState.isProcessingDeviceDirectly()

    private var _provisionedDevice: MeshNode? = null

    /** This field is only not null when [provisioningState] is SUCCESS. */
    val provisionedDevice: MeshNode
        get() = _provisionedDevice!!

    val cbpState: Flow<CBPState>
        get() = cbpEnabled.map { enabled ->
            when {
                !isCBPSupported -> CBPState.NOT_SUPPORTED
                enabled -> CBPState.ENABLED
                else -> CBPState.DISABLED
            }
        }

    val provisioningButtonIsEnabled
        get() = combine(deviceName, cbpState, provisioningState)
        { deviceName, cbpState, provisioningState ->
            if (provisioningState != ProvisioningState.READY) false
            else deviceName.isNotBlank() && cbpState != CBPState.ENABLED
        }

    fun setDeviceName(name: String) {
        save[KEY_NAME] = name
    }

    fun setCbpEnabled(isEnabled: Boolean) {
        save[KEY_CBP_ENABLED] = isEnabled
    }

    fun selectSubnetByIndex(index: Int) {
        _selectedSubnet.value = availableSubnets[index]
    }

    fun updateDeviceToProvision() {
        // this is no-op for remote devices
        when (deviceToProvision) {
            is DeviceToProvision.Direct -> {
                deviceToProvision.name = deviceName.value
                selectedSubnet.value.let { deviceToProvision.subnet = it }
            }
            is DeviceToProvision.Remote -> Unit
        }
    }

    fun provisionDevice() {
        if (provisioningJob.isActive) return
        updateDeviceToProvision()

        provisioningJob = viewModelScope.launch {
            _provisioningState.value = ProvisioningState.ACTIVE
            val result = provisioningLogic
                .getForDevice(deviceToProvision)
                .provision()

            when (result) {
                is ProvisioningHelper.Failure -> {
                    _provisioningState.value = ProvisioningState.READY
                    emitMessage(result.messageContent)
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
    }

    private val isCBPSupported by lazy {
        val oobInformation = deviceToProvision.oobInformation
        oobInformation.contains(OobInformation.CertificateBasedProvisioning)
    }

    enum class CBPState {
        NOT_SUPPORTED, ENABLED, DISABLED
    }

    enum class ProvisioningState {
        READY, ACTIVE, SUCCESS
    }
}