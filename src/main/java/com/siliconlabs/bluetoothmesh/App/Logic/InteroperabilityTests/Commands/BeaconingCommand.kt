/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import com.siliconlab.bluetoothmesh.adk_low.ConnectionCallback
import com.siliconlabs.bluetoothmesh.App.Logic.BluetoothScanner
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestCommand
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestProcedure
import com.siliconlabs.bluetoothmesh.App.Models.BluetoothConnectableDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.tinylog.kotlin.Logger
import java.util.*
import kotlin.time.Duration

class BeaconingCommand(
        private val type: IOPTestProcedure.NodeType,
        private val scanMode: Int,
        private val timeout: Duration
) : IOPTestCommand<BeaconingCommand.Artifacts>() {
    override val name = "Beaconing $type device"
    override val description = "$name with UUID: ${type.uuid}"

    override suspend fun execute(): Result<Artifacts> {
        return run {
            BluetoothScanner.delayBeforeScanning()
            executeWithTimeMeasurement(::awaitDevice, ::Artifacts)
        }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { Result.failure(Failed(this, it.message!!)) }
        )
    }

    private suspend fun awaitDevice(): Result<BluetoothConnectableDevice> =
            scannedDevices
                    .filter { (uuid, _) -> uuid == this.type.uuid }
                    .firstOrNull()
                    ?.let { (_, device) -> Result.success(device) }
                    ?: Result.failure(IOPTestProcedure.NotFound(
                            "$type device with UUID ${type.uuid} not found"))

    private val scannedDevices: Flow<Pair<UUID, BluetoothConnectableDevice>>
        get() = callbackFlow {
            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    if (result?.scanRecord == null ||
                            result.scanRecord!!.serviceUuids == null ||
                            result.scanRecord!!.serviceUuids.isEmpty()) {
                        return
                    }

                    val device = BluetoothConnectableDevice(result)
                    val uuid = requireNotNull(device.uuid)

                    Logger.debug { "Found device with UUID $uuid" }
                    trySend(uuid to device)
                }
            }
            BluetoothScanner.addScanCallback(callback)
            BluetoothScanner.startLeScan(
                    ConnectionCallback.CONNECTION_SERVICE.PROVISIONING.service,
                    scanMode
            )
            launch {
                delay(timeout)
                close()
            }
            awaitClose {
                BluetoothScanner.stopLeScan()
                BluetoothScanner.removeScanCallback(callback)
            }
        }

    data class Artifacts(val device: BluetoothConnectableDevice, val time: Duration)
}
