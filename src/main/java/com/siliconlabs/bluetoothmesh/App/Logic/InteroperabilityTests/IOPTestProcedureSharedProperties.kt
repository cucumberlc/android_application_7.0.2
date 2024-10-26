/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests

import com.siliconlab.bluetoothmesh.adk.connectable_device.ProxyConnection
import com.siliconlabs.bluetoothmesh.App.Models.BluetoothConnectableDevice
import java.util.*

class IOPTestProcedureSharedProperties {
    private val devices: MutableMap<UUID, BluetoothConnectableDevice> = mutableMapOf()
    private var subnetConnection: ProxyConnection? = null
    private var transactionId: UByte = 0u

    fun storeDevice(type: IOPTestProcedure.NodeType, device: BluetoothConnectableDevice) {
        devices[type.uuid] = device
    }

    fun getDevice(type: IOPTestProcedure.NodeType): Result<BluetoothConnectableDevice> {
        return devices[type.uuid]
                ?.let { Result.success(it) }
                ?: Result.failure(IOPTestProcedure.NotFound("No ${type.name} device found"))
    }

    fun storeSubnetConnection(connection: ProxyConnection) {
        subnetConnection = connection
    }

    fun getSubnetConnection(): Result<ProxyConnection> {
        return subnetConnection
                ?.let { Result.success(it) }
                ?: Result.failure(IOPTestProcedure.NotFound("No connection found"))
    }

    suspend fun <R> performAsTransaction(transaction: suspend (UByte) -> Result<R>): Result<R> {
        return transaction.invoke(transactionId).onSuccess { transactionId++ }
    }
}
