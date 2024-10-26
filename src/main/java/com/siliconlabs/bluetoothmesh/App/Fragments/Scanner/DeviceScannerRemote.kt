/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Scanner

import com.siliconlab.bluetoothmesh.adk.data_model.model.ModelIdentifier
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.Subnet
import com.siliconlab.bluetoothmesh.adk.onFailure
import com.siliconlab.bluetoothmesh.adk.onSuccess
import com.siliconlab.bluetoothmesh.adk.provisioning.RemoteProvisioner
import com.siliconlabs.bluetoothmesh.App.Logic.BluetoothState
import com.siliconlabs.bluetoothmesh.App.Logic.ConnectionState
import com.siliconlabs.bluetoothmesh.App.Logic.NetworkConnectionLogic
import com.siliconlabs.bluetoothmesh.App.MeshApplication
import com.siliconlabs.bluetoothmesh.App.Models.DeviceToProvision
import com.siliconlabs.bluetoothmesh.App.Models.UnprovisionedDevice
import com.siliconlabs.bluetoothmesh.App.Utils.Message
import com.siliconlabs.bluetoothmesh.App.Utils.MessageBearer
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.hasActiveSubscriptionsFlow
import com.siliconlabs.bluetoothmesh.App.Utils.withTitle
import com.siliconlabs.bluetoothmesh.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.tinylog.kotlin.Logger
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

class DeviceScannerRemote(
    scope: CoroutineScope,
    private val networkConnectionLogic: NetworkConnectionLogic,
    remoteProvisioner: Node,
    private val subnet: Subnet,
) : DeviceScanner(scope) {
    companion object {
        private val scanTimeout = 5.seconds
        private val stopScanTimeout = 2.seconds
    }

    private val remoteProvisioningServerModel = remoteProvisioner.elements
        .flatMap { it!!.sigModels }
        .first { it.modelIdentifier == ModelIdentifier.RemoteProvisioningServer }
    private val netKeyIndex = subnet.netKey.index

    private var isRemoteProvisioningScanActive = false

    private val unprovisionedDevices = mutableListOf<UnprovisionedDevice>()

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
        Logger.debug { "STARTING SCAN..." }
        try {
            executeScanProcedure()
        } catch (startError: RemoteScanStartError) {
            Logger.error { "Failed to start scan: ${startError.message}" }
            sendError(startError)
        } catch (cancelled: CancellationException) {
            Logger.debug { "FINISHING SCAN (cancelled)" }
            requestStopRemoteProvisioningScan(true)
            throw cancelled  // always rethrow cancellations
        }
        Logger.debug { "FINISHING SCAN" }
    }

    override fun isInInvalidStateFlow() = combine(
        networkConnectionLogic.currentStateFlow,
        BluetoothState.isEnabled
    ) { netState, btState ->
        Logger.debug { "network state changed: $netState, bluetoothState : $btState" }
        val invalidState: ScannerState.InvalidState? = when {
            !btState -> ScannerState.NO_BLUETOOTH
            netState != ConnectionState.CONNECTED -> ScannerState.NO_NETWORK
            else -> null
        }
        invalidState
    }

    override fun selectDevice(
        device: UnprovisionedDevice,
        targetSubnet: Subnet,   // target subnet is ignored here
    ): DeviceToProvision {
        return synchronized(unprovisionedDevices) {
            if (device !in unprovisionedDevices) {
                Logger.error { "Selected device does not exist in scanned devices!" }
            }

            DeviceToProvision.Remote(
                device = device,
                subnet = subnet,
                serverAddress = remoteProvisioningServerModel.element.address,
                netKeyIndex = netKeyIndex
            )
        }
    }

    override fun clearDevices() {
        synchronized(unprovisionedDevices) {
            unprovisionedDevices.clear()
            _scannedDevices.value = emptyList()
        }
    }

    private suspend fun executeScanProcedure() {
        coroutineScope {
            val scanCollectionJob = launch { collectScanReports() }
            requestStartRemoteProvisioningScan()
            delay(scanTimeout)
            isRemoteProvisioningScanActive = false
            scanCollectionJob.cancel("Scan timed out")
            scanCollectionJob.join()
        }
    }

    private suspend fun collectScanReports() {
        RemoteProvisioner.scanReport
            .onStart { clearDevices() } // sanity clear
            .cancellable()
            .map { scanReport ->
                synchronized(unprovisionedDevices) {
                    val searchIndex = unprovisionedDevices.indexOfFirst {
                        it.uuid == scanReport.uuid
                    }
                    val newDevice = UnprovisionedDevice(scanReport)
                    if (searchIndex < 0) {
                        unprovisionedDevices.add(newDevice)
                    } else {
                        unprovisionedDevices[searchIndex] = newDevice
                    }
                    unprovisionedDevices.toList()
                }
            }
            .flowOn(Dispatchers.Default)
            .collect {
                _scannedDevices.value = it
            }
    }

    private suspend fun requestStartRemoteProvisioningScan() {
        RemoteProvisioner.startScan(
            serverAddress = remoteProvisioningServerModel.element.address,
            reportItemsLimit = 0u,
            scanTimeout = scanTimeout.toInt(DurationUnit.SECONDS).toUByte(),
            uuid = null,
            netKeyIndex = netKeyIndex,
            sourceElementIndex = 0
        ).onSuccess {
            isRemoteProvisioningScanActive = true
        }.onFailure {
            throw RemoteScanStartError(
                Message.error(it).withTitle(R.string.error_message_title_scan_start_failed)
            )
        }
    }

    private suspend fun requestStopRemoteProvisioningScan(await: Boolean) {
        if (!isRemoteProvisioningScanActive) return
        isRemoteProvisioningScanActive = false
        // cancel the scan on main app scope instead of scanner scope because this needs to execute
        // even when scanner is closed
        val cancelJob = MeshApplication.mainScope.async {
            RemoteProvisioner.stopScan(
                serverAddress = remoteProvisioningServerModel.element.address,
                netKeyIndex = netKeyIndex,
                sourceElementIndex = 0
            )
        }
        if (!await) return
        // wait for cancellation even if current job is cancelled so starting new scan doesn't
        // happen before previous one is stopped
        withContext(NonCancellable) {
            // launch a timeout to see if stopScan lags
            withTimeoutOrNull(stopScanTimeout) {
                cancelJob.await().onFailure {
                    Logger.error { "Failed to stop remote provisioning scan: $it" }
                }
            } ?: run {
                Logger.error { "Failed to stop remote provisioning scan (exceeded timeout)" }
            }
        }
    }

    private class RemoteScanStartError(messageContent: Message) :
        MessageBearer.Exception(messageContent)
}