/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Procedures

import com.siliconlab.bluetoothmesh.adk.connectable_device.ProxyConnection
import com.siliconlab.bluetoothmesh.adk.data_model.model.Model
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.AppKey
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.Subnet
import com.siliconlab.bluetoothmesh.adk.provisioning.ProvisionerOOBControl
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands.BindAppKeyToModelCommand
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands.BindAppKeyToNodeCommand
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands.CloseProxyConnectionCommand
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands.ProvisionCommand
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands.SetRetransmissionCommand
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTest
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestProcedure
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestProcedureSharedProperties
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.executeWithTimeout
import com.siliconlabs.bluetoothmesh.App.Models.BluetoothConnectableDevice
import com.siliconlabs.bluetoothmesh.App.Utils.flatMap
import com.siliconlabs.bluetoothmesh.App.Utils.with
import kotlinx.coroutines.channels.ProducerScope
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

abstract class ProvisioningProcedure(
        sharedProperties: IOPTestProcedureSharedProperties,
        private val type: NodeType,
        private val timeout: Duration = 10.seconds
) : IOPTestProcedure<IOPTestProcedure.TimeWithTimeoutArtifact>(sharedProperties, retriesCount = 3) {

    protected lateinit var stateOutputChannel: ProducerScope<IOPTest.State>

    override suspend fun execute(
            stateOutputChannel: ProducerScope<IOPTest.State>): Result<TimeWithTimeoutArtifact> {
        this.stateOutputChannel = stateOutputChannel

        return run {
            provision()
        }.flatMap { artifacts ->
            run {
                configure(artifacts)
            }.map {
                TimeWithTimeoutArtifact("provisioning", artifacts.time, timeout)
            }
        }
    }

    private suspend fun provision(): Result<ProvisionCommand.Artifacts> {
        return run {
            getDevice(type) with getSubnet()
        }.flatMap { (device, subnet) ->
            run {
                provisionDevice(device, subnet)
            }.flatMap { artifacts ->
                checkTime("provisioning", artifacts.time, timeout).map { artifacts }
            }
        }
    }

    protected open suspend fun provisionDevice(device: BluetoothConnectableDevice, subnet: Subnet): Result<ProvisionCommand.Artifacts> {
        return ProvisionCommand(device, subnet, createProvisionerOOBControl(), proxyFeatureEnabled).executeWithLogging()
    }

    protected abstract fun createProvisionerOOBControl(): ProvisionerOOBControl

    protected abstract val proxyFeatureEnabled: Boolean

    private suspend fun configure(artifacts: ProvisionCommand.Artifacts): Result<Unit> {
        return artifacts.let { (node, proxyConnection) ->
            run {
                getAppKey() with getModel(node)
            }.flatMap { (appKey, model) ->
                run {
                    bindAppKeyToNode(node, appKey)
                }.flatMap {
                    bindAppKeyToModel(model, appKey)
                }.flatMap {
                    setRetransmission(node)
                }.flatMap {
                    enableSpecificFeature(node)
                }.flatMap {
                    handleOpenProxyConnection(proxyConnection)
                }
            }
        }
    }

    private suspend fun bindAppKeyToNode(node: Node, appKey: AppKey): Result<Unit> {
        return BindAppKeyToNodeCommand(node, appKey).executeWithTimeout(defaultCommandTimeout)
    }

    private suspend fun bindAppKeyToModel(model: Model, appKey: AppKey): Result<Unit> {
        return BindAppKeyToModelCommand(model, appKey).executeWithTimeout(defaultCommandTimeout)
    }

    private suspend fun setRetransmission(node: Node): Result<Unit> {
        return SetRetransmissionCommand(node).executeWithTimeout(defaultCommandTimeout)
    }

    protected abstract suspend fun enableSpecificFeature(node: Node): Result<Unit>

    protected open suspend fun handleOpenProxyConnection(proxyConnection: ProxyConnection): Result<Unit> {
        return CloseProxyConnectionCommand(proxyConnection).executeWithTimeout(defaultCommandTimeout)
            .flatMap { Result.success(Unit) }
    }

    override suspend fun rollback(cause: Throwable): Result<Unit> {
        return getNode(type).fold(
                onSuccess = { node ->
                    checkConnection().flatMap {
                        removeNodeAndRescanDevice(type, node)
                    }
                },
                onFailure = { Result.success(Unit) } // no node, nothing to do
        )
    }
}
