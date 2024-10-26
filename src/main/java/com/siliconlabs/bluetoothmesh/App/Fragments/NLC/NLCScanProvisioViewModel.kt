/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.NLC

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.siliconlab.bluetoothmesh.adk.BluetoothMesh
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.Subnet
import com.siliconlab.bluetoothmesh.adk.provisioning.OobInformation
import com.siliconlabs.bluetoothmesh.App.Fragments.Common.ViewModelWithMessages
import com.siliconlabs.bluetoothmesh.App.Fragments.Provisioning.ordinary.ProvisioningViewModel
import com.siliconlabs.bluetoothmesh.App.Fragments.Scanner.DeviceScanner
import com.siliconlabs.bluetoothmesh.App.Fragments.Scanner.DeviceScannerDirect
import com.siliconlabs.bluetoothmesh.App.Fragments.Scanner.DeviceScannerRemote
import com.siliconlabs.bluetoothmesh.App.Logic.NetworkConnectionLogic
import com.siliconlabs.bluetoothmesh.App.Logic.provision.ProvisioningHelper
import com.siliconlabs.bluetoothmesh.App.Logic.provision.ProvisioningLogic
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.App.Models.AppState
import com.siliconlabs.bluetoothmesh.App.Models.DeviceToProvision
import com.siliconlabs.bluetoothmesh.App.Models.UnprovisionedDevice
import com.siliconlabs.bluetoothmesh.App.Navigation.MeshAppDestination
import com.siliconlabs.bluetoothmesh.App.Utils.asInfo
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.emptyJob
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NLCScanProvisioViewModel @Inject constructor(
    networkConnectionLogic: NetworkConnectionLogic,
    navArgs: MeshAppDestination?,
    private val provisioningLogic: ProvisioningLogic?=null,
    private val save: SavedStateHandle?=null
):ViewModelWithMessages() {
    private val subnet: Subnet?
    private val scanner: DeviceScanner
    init {
        val scannerScope = CoroutineScope(viewModelScope.coroutineContext + SupervisorJob())
        if (navArgs is MeshAppDestination.OfNode) {
            subnet = navArgs.subnet
            scanner = DeviceScannerRemote(
                scannerScope,
                networkConnectionLogic,
                navArgs.node,
                navArgs.subnet
            )
        } else {
            subnet = null
            scanner = DeviceScannerDirect(scannerScope)
        }

        viewModelScope.launch {
            scanner.errors.collect {
                emitMessage(it)
            }
        }
    }

    val scanResults = scanner.scannedDevices
    val scanState = scanner.scannerState

    val isRemoteScan
        get() = scanner is DeviceScannerRemote

    fun startScan() {
        scanner.startScan()
    }

    fun stopScan() {
        scanner.stopScan()
    }

    fun isScanning() = scanner.scannerState.value == DeviceScanner.ScannerState.SCANNING

    // requires that app contains at least 1 subnet
    fun selectDevice(device: UnprovisionedDevice) =
        scanner.selectDevice(device, defaultSubnet()!!)

    fun defaultSubnet() = subnet ?: BluetoothMesh.network.subnets.firstOrNull()

    /**
     * Provision Logic.
     */

    private val _provisionResultLiveData = MutableLiveData<ProvisioningHelper.ProvisioningResult?>()
    val provisionResultLiveData: LiveData<ProvisioningHelper.ProvisioningResult?> = _provisionResultLiveData

      fun provisionDevice(deviceToProvision:DeviceToProvision) {
        viewModelScope.launch {
            val result = provisioningLogic
                ?.getForDevice(deviceToProvision)
                ?.provision()
            when(result){
                is ProvisioningHelper.Failure -> {

                    emitMessage(result.messageContent)
                }
                is ProvisioningHelper.Success -> {
                 val meshNode = result.meshNode

                    println("Name of the Model= "+meshNode.functionality.getAllModels())
                    _provisionResultLiveData.value = result
                }
                else -> {}
            }

        }
    }
}