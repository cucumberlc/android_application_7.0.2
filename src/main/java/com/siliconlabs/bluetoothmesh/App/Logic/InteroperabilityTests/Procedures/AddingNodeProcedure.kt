/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Procedures

import com.siliconlab.bluetoothmesh.adk.connectable_device.ProxyConnection
import com.siliconlab.bluetoothmesh.adk.data_model.element.Element
import com.siliconlab.bluetoothmesh.adk.data_model.model.Model
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.AppKey
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.Subnet
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands.BindAppKeyToModelCommand
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands.BindAppKeyToNodeCommand
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands.CloseProxyConnectionCommand
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands.ProvisionCommand
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands.SendUnicastSetCommand
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands.SetFriendCommand
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands.SetRetransmissionCommand
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTest
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestProcedure
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestProcedureSharedProperties
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Procedures.Provisioning.IOPOutputProvisionerOOB
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.executeWithTimeout
import com.siliconlabs.bluetoothmesh.App.Models.BluetoothConnectableDevice
import com.siliconlabs.bluetoothmesh.App.Utils.flatMap
import com.siliconlabs.bluetoothmesh.App.Utils.with
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Duration

class AddingNodeProcedure(
    sharedProperties: IOPTestProcedureSharedProperties,
) : IOPTestProcedure<IOPTestProcedure
.Artifact.Unit>(sharedProperties, retriesCount = 7) {

    private val type = NodeType.Friend
    private lateinit var stateOutputChannel: ProducerScope<IOPTest.State>

    override suspend fun execute(
            stateOutputChannel: ProducerScope<IOPTest.State>): Result<Artifact.Unit> {
        this.stateOutputChannel = stateOutputChannel

        return run {
            checkNodeAbsence() with checkConnection()
        }.flatMap {
            run {
                addNodeProvision()
            }.flatMap { artifacts ->
                run {
                    addNodeConfigure(artifacts)
                }.map {
                    Artifact.Unit
                }
            }
        }
    }

    private fun checkNodeAbsence(): Result<Unit> {
        return getNode(type).fold(
            onSuccess = { Result.failure(NodeNotAbsent(type)) },
            onFailure = { Result.success(Unit) }
        )
    }

    class NodeNotAbsent(type: NodeType) : ProcedureError("$type node should be absent in subnet")

    private suspend fun addNodeProvision(): Result<ProvisionCommand.Artifacts> {
        return run {
            getDevice(type) with getSubnet()
        }.flatMap { (device, subnet) ->
            provisionDevice(device, subnet)
        }
    }

    private suspend fun provisionDevice(device: BluetoothConnectableDevice, subnet: Subnet) =
        coroutineScope {
            val provisionerOOBControl = IOPOutputProvisionerOOB()
            val observingState = launch { observeProvisionerOOBControlState(provisionerOOBControl) }
            val result =
                ProvisionCommand(device, subnet, provisionerOOBControl, false).executeWithLogging()
            result.also { observingState.cancel() }
        }

    private suspend fun observeProvisionerOOBControlState(provisionerOOBControl: IOPOutputProvisionerOOB) {
        provisionerOOBControl.onProvidedOutputOOBValue.collect { callback ->
            stateOutputChannel.send(
                callback?.let { WaitingForUserAction(callback) } ?: IOPTest.State.Executing
            )
        }
    }

    private suspend fun addNodeConfigure(artifacts: ProvisionCommand.Artifacts): Result<Unit> {
        return artifacts.let { (node, proxyConnection) ->
            run {
                getAppKey() with getModel(node)
            }.flatMap { (appKey, model) ->
                run {
                    bindAppKeyToNode(node, appKey)
                }.flatMap {
                    bindAppKeyToModel(model, appKey)
                }.flatMap {
                    setAddNodeRetransmission(node)
                }.flatMap {
                    addNodeEnableSpecificFeature(node)
                }.flatMap {
                    addNodeHandleOpenProxyConnection(proxyConnection)
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

    private suspend fun closeConnection(connection: ProxyConnection): Result<Duration> {
        return CloseProxyConnectionCommand(connection).executeWithTimeout(defaultCommandTimeout)
    }

    private suspend fun setAddNodeRetransmission(node: Node): Result<Unit> {
        return SetRetransmissionCommand(node).executeWithTimeout(defaultCommandTimeout)
    }

    private suspend fun addNodeEnableSpecificFeature(node: Node): Result<Unit> {
        return SetFriendCommand(node).executeWithTimeout(defaultCommandTimeout)
    }

    protected open suspend fun addNodeHandleOpenProxyConnection(proxyConnection: ProxyConnection): Result<Unit> {
        return CloseProxyConnectionCommand(proxyConnection).executeWithTimeout(defaultCommandTimeout)
            .flatMap { Result.success(Unit) }
    }

    private suspend fun setOnAndOffState(node: Node): Result<Unit> {
        return run {
            getElement(node) with getAppKey()
        }.flatMap { (element, appKey) ->
            run {
                setState(element, appKey, true)
            }.flatMap {
                setState(element, appKey, false)
            }
        }.flatMap {
            Result.success(Unit)
        }
    }

    private suspend fun setState(
        element: Element,
        appKey: AppKey,
        state: Boolean,
    ): Result<Unit> {
        return performAsTransaction { id ->
            SendUnicastSetCommand(element, appKey, state, true, id).executeWithTimeout(
                defaultCommandTimeout
            )
        }.flatMap {
            Result.success(Unit)
        }
    }

    override suspend fun rollback(cause: Throwable): Result<Unit> {
        return when (cause) {
            is NodeNotAbsent -> Result.success(
                    Unit) // won't rollback; cannot remove node for previous "removing nodes" test
            else -> getNode(type).fold(
                    onSuccess = { node ->
                        checkConnection().flatMap {
                            removeNodeAndRescanDevice(type, node)
                        }
                    },
                    onFailure = { Result.success(Unit) } // no node, nothing to do
            )
        }
    }

    data class WaitingForUserAction(val onProvidedOutputOOBValue: (Int?) -> Unit) : IOPTest.State.InProgress {
        override fun toString() = "Execution in progress, waiting for user to provide OOB value"
    }
}