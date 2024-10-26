/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Procedures

import com.siliconlab.bluetoothmesh.adk.configuration_control.ConfigurationControlSettings
import com.siliconlab.bluetoothmesh.adk.data_model.group.Group
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.AppKey
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands.AddSubscriptionCommand
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands.SendMulticastGetCommand
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands.SendMulticastSetCommand
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTest
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestProcedure
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestProcedureSharedProperties
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.executeWithTimeout
import com.siliconlabs.bluetoothmesh.App.Utils.and
import com.siliconlabs.bluetoothmesh.App.Utils.flatMap
import com.siliconlabs.bluetoothmesh.App.Utils.with
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.delay
import org.tinylog.kotlin.Logger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MulticastControlProcedure(
        sharedProperties: IOPTestProcedureSharedProperties
) : IOPTestProcedure<MulticastControlProcedure.Artifacts>(sharedProperties, retriesCount = 8) {

    // timeout should be based on LPN node timeout - node can sleep when get/set messages are sent
    private val timeout = (200 + ConfigurationControlSettings().lpnLocalTimeout).milliseconds
    var configurationFailures = mutableMapOf<NodeType, Throwable>()

    override suspend fun execute(
            stateOutputChannel: ProducerScope<IOPTest.State>): Result<Artifacts> {
        return run {
            getNodes(NodeType.values().toSet()) with getGroup() and getAppKey()
        }.flatMap { (nodes, group, appKey) ->
            run {
                checkConnection()
            }.flatMap {
                configureNodes(nodes, group)
            }.flatMap {
                controlGroup(group, appKey, nodes.values.toSet())
            }
        }
    }

    private suspend fun configureNodes(nodes: Map<NodeType, Node>, group: Group): Result<Unit> {
        nodes.forEach { (type, node) ->
            configure(node, group).onFailure {
                configurationFailures[type] = it
            }
        }

        if (configurationFailures.isNotEmpty()){
            retryConfigureNodes(nodes, group)
        }

        return if (configurationFailures.isEmpty()){
            configurationFailures = mutableMapOf()
            Result.success(Unit)
        }
        else{
            configurationFailures = mutableMapOf()
            Result.failure(ConfigurationFailed(configurationFailures))
        }
    }

    private suspend fun retryConfigureNodes(nodes: Map<NodeType, Node>, group: Group){
        nodes.forEach { (type, node) ->
            configure(node, group).onFailure {
            }.onSuccess {
                configurationFailures = mutableMapOf()
            }
        }
    }

    private suspend fun configure(node: Node, group: Group): Result<Unit> {
        return run {
            getModel(node)
        }.flatMap { model ->
            AddSubscriptionCommand(model, group).executeWithTimeout(defaultCommandTimeout)
        }
    }

    private suspend fun controlGroup(
        group: Group,
        appKey: AppKey,
        nodes: Set<Node>
    ): Result<Artifacts> {
        return run {
            setAndGetState(group, appKey, nodes, true) with setAndGetState(
                group,
                appKey,
                nodes,
                false,
            )
        }.map { (setOnArtifact, setOffArtifact) ->
            Artifacts(setOnArtifact, setOffArtifact)
        }
    }

    private suspend fun setAndGetState(
        group: Group,
        appKey: AppKey,
        nodes: Set<Node>,
        state: Boolean,
    ): Result<SettingArtifact> {
        //As Set and Get was calling, giving delay
        delay(5000)
        return run {
            setState(group, appKey, state) with getState(group, appKey, nodes, state)
        }.map { (settingTimeArtifact, gettingTimeArtifact) ->
            SettingArtifact(settingTimeArtifact, gettingTimeArtifact)
        }
    }

    private suspend fun setState(
        group: Group,
        appKey: AppKey,
        state: Boolean,
    ): Result<TimeArtifact> {
        return performAsTransaction { id ->
            SendMulticastSetCommand(group, appKey, state, id).executeWithTimeout(
                5.seconds
            )
        }.map { time ->
            TimeArtifact("setting state", time)
        }
    }

    private suspend fun getState(
        group: Group,
        appKey: AppKey,
        nodes: Set<Node>,
        state: Boolean,
    ): Result<TimeWithTimeoutArtifact> {
        return run {
            SendMulticastGetCommand(group, appKey, nodes).executeWithTimeout(5.seconds)
        }.flatMap { (states, time) ->
            run {
                checkStates(states, state)
            }.flatMap {
                checkTime("obtaining state", time, timeout)
            }.map {
                TimeWithTimeoutArtifact("obtaining state", time, timeout)
            }
        }
    }

    private fun checkStates(states: Map<Node, Boolean>, expectedState: Boolean): Result<Unit> {
        val wrongStates = states.filterValues { it != expectedState }

        return if (wrongStates.isEmpty()) Result.success(Unit)
        else WrongState(
            states.mapKeys { (node, _) -> NodeType.values().single { node.uuid == it.uuid } },
            expectedState
        ).let { Result.failure(it) }
    }

    override suspend fun rollback(cause: Throwable): Result<Unit> {
        return Result.success(
                Unit) // it was decided to not rollback configuration step to make team's life easier
    }

    class ConfigurationFailed(
            causes: Map<NodeType, Throwable>
    ) : ProcedureError("Configuration failed for nodes: $causes}")

    class WrongState(
            actual: Map<NodeType, Boolean>,
            expected: Boolean,
    ) : ProcedureError("Obtained wrong states from nodes (actual: $actual, expected all to have: $expected)")

    data class Artifacts(
            private val setOnArtifact: SettingArtifact,
            private val setOffArtifact: SettingArtifact
    ) : Artifact.Reportable {
        override val reportLine: String = "setting ON - ${setOnArtifact.reportLine}. Setting OFF - ${setOffArtifact.reportLine}"
    }
}