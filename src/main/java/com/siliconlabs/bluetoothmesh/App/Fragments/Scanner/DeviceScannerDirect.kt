/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Scanner

import com.siliconlab.bluetoothmesh.adk.data_model.subnet.Subnet
import com.siliconlab.bluetoothmesh.adk.provisioning.ProvisionerConnection
import com.siliconlabs.bluetoothmesh.App.Logic.BluetoothScanner
import com.siliconlabs.bluetoothmesh.App.Logic.BluetoothState
import com.siliconlabs.bluetoothmesh.App.Models.BluetoothConnectableDevice
import com.siliconlabs.bluetoothmesh.App.Models.DeviceToProvision
import com.siliconlabs.bluetoothmesh.App.Models.UnprovisionedDevice
import com.siliconlabs.bluetoothmesh.App.Utils.Message
import com.siliconlabs.bluetoothmesh.App.Utils.MessageBearer
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.hasActiveSubscriptionsFlow
import com.siliconlabs.bluetoothmesh.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import org.tinylog.kotlin.Logger

class DeviceScannerDirect(scope: CoroutineScope) : DeviceScanner(scope) {
    private val connectableDevices = mutableListOf<BluetoothConnectableDevice>()

    private val _scannedDevices = MutableStateFlow<List<UnprovisionedDevice>>(emptyList())
    override val scannedDevices = _scannedDevices.asStateFlow()

    init {
        scope.launch {
            _scannedDevices.hasActiveSubscriptionsFlow().collect {
                if (!it) {
                    stopScan()
                    clearDevices()
                }
            }
        }
    }

    override suspend fun performScan() {
        try {
            executeScanProcedure()
        } catch (startError: DirectScanStartError) {
            Logger.error { "Failed to start scan: ${startError.message}" }
            sendError(startError)
        }
    }

    override fun isInInvalidStateFlow() = BluetoothState.isEnabled.map { btEnabled ->
        ScannerState.NO_BLUETOOTH.takeUnless { btEnabled }
    }

    override fun selectDevice(
        device: UnprovisionedDevice,
        targetSubnet: Subnet,
    ): DeviceToProvision {
        return synchronized(connectableDevices) {
            val connectableDevice = connectableDevices.first { it.uuid == device.uuid }
            DeviceToProvision.Direct(connectableDevice, device.name, targetSubnet)
        }
    }

    override fun clearDevices() {
        synchronized(connectableDevices) {
            connectableDevices.clear()
            _scannedDevices.value = emptyList()
        }
    }

    private suspend fun executeScanProcedure() {
        BluetoothScanner.resultsFlow
            .onStart {
                if (!BluetoothScanner.startLeScan(ProvisionerConnection.unprovisionedService)) {
                    val message = Message.error(R.string.message_error_cannot_start_bt_scan)
                    throw DirectScanStartError(message)
                }
            }
            .onCompletion {
                BluetoothScanner.stopLeScan()
            }
            .filter { !it.scanRecord?.serviceUuids.isNullOrEmpty() }
            .map { result ->
                val newDevice = BluetoothConnectableDevice(result)

                synchronized(connectableDevices) {
                    connectableDevices.apply {
                        val searchIndex = indexOfFirst { it.uuid == newDevice.uuid }
                        if (searchIndex < 0) add(newDevice)
                        else set(searchIndex, newDevice)
                    }

                    mapUnprovisionedDevices()
                }
            }
            .flowOn(Dispatchers.Default)
            .collect { _scannedDevices.value = it }
    }

    private fun mapUnprovisionedDevices() = connectableDevices.mapNotNull {
        runCatching {
            UnprovisionedDevice(
                rssi = it.scanResult.rssi.toByte(),
                uuid = it.uuid!!,
                oobInformation = it.oobInformation,
                name = it.name ?: it.scanResult.device.address,
            )
        }.onFailure { error ->
            Logger.error(error) { "Failed to convert BluetoothConnectableDevice into UnprovisionedDevice.\n$it" }
        }.getOrNull()
    }

    private class DirectScanStartError(messageContent: Message) :
        MessageBearer.Exception(messageContent)
}