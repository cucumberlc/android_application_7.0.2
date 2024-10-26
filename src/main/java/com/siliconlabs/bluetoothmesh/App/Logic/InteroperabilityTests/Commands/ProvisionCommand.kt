/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands

import com.siliconlab.bluetoothmesh.adk.connectable_device.ProxyConnection
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.Subnet
import com.siliconlab.bluetoothmesh.adk.fold
import com.siliconlab.bluetoothmesh.adk.provisioning.ProvisionerConfiguration
import com.siliconlab.bluetoothmesh.adk.provisioning.ProvisionerConnection
import com.siliconlab.bluetoothmesh.adk.provisioning.ProvisionerOOB
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestCommand
import com.siliconlabs.bluetoothmesh.App.Models.BluetoothConnectableDevice
import kotlinx.coroutines.suspendCancellableCoroutine
import org.tinylog.kotlin.Logger
import kotlin.coroutines.resume
import kotlin.time.Duration

class ProvisionCommand(
    private val device: BluetoothConnectableDevice,
    private val subnet: Subnet,
    private val provisionerOOB: ProvisionerOOB,
    private val proxyFeatureEnabled: Boolean,
) : IOPTestCommand<ProvisionCommand.Artifacts>() {
    override val name = "Provisioning device"
    override val description = "$name with UUID ${device.uuid}"

    override suspend fun execute(): Result<Artifacts> {
        val provisionerConnection = ProvisionerConnection(device, subnet)
        val provisionerConfiguration = ProvisionerConfiguration().apply {
            isEnablingProxy = proxyFeatureEnabled
            isEnablingNodeIdentity = true
            isUsingOneGattConnection = true
            isKeepingProxyConnection = true
            isGettingDeviceCompositionData = true
        }
        provisionerConnection.provisionerOOB = provisionerOOB

        return executeWithTimeMeasurement(
            block = {
                suspendCancellableCoroutine { continuation ->
                    provisionerConnection.provision(provisionerConfiguration, null) { outcome ->
                        val result = outcome.fold(
                            onSuccess = {
                                Result.success(Pair(it, provisionerConnection.proxyConnection))
                            },
                            onFailure = {
                                Logger.debug { "provisioning failure, $it" }
                                Result.failure(Failed(this@ProvisionCommand, it))
                            }
                        )
                        continuation.resume(result)
                    }
                }
            },
            combine = { (node, connection), provisionTime ->
                Artifacts(node, connection, provisionTime)
            }
        )
    }

    data class Artifacts(
        val node: Node,
        val proxyConnection: ProxyConnection,
        val time: Duration
    )
}