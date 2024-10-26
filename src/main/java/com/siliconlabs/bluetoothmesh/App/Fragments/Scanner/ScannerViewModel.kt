/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Scanner

import androidx.lifecycle.viewModelScope
import com.siliconlab.bluetoothmesh.adk.BluetoothMesh
import com.siliconlab.bluetoothmesh.adk.data_model.network.Network
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.Subnet
import com.siliconlabs.bluetoothmesh.App.Fragments.Common.ViewModelWithMessages
import com.siliconlabs.bluetoothmesh.App.Logic.NetworkConnectionLogic
import com.siliconlabs.bluetoothmesh.App.Models.UnprovisionedDevice
import com.siliconlabs.bluetoothmesh.App.Navigation.MeshAppDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScannerViewModel @Inject constructor(
    networkConnectionLogic: NetworkConnectionLogic,
    navArgs: MeshAppDestination?,
) : ViewModelWithMessages() {
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

    fun subnetList() = BluetoothMesh.network.subnets
}