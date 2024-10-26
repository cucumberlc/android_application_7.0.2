/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands

import com.siliconlab.bluetoothmesh.adk.data_model.group.Group
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.AppKey
import com.siliconlab.bluetoothmesh.adk.data_model.model.ModelIdentifier
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.functionality_control.generic.GenericClient
import com.siliconlab.bluetoothmesh.adk.functionality_control.generic.StateType
import com.siliconlab.bluetoothmesh.adk.onFailure
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestCommand
import kotlinx.coroutines.flow.takeWhile
import kotlin.time.Duration

class SendMulticastGetCommand(
    private val group: Group,
    private val appKey: AppKey,
    private val nodes: Set<Node>
) : IOPTestCommand<SendMulticastGetCommand.Artifacts>() {
    override val name = "Send multicast Generic OnOff Get message"
    override val description =
        "$name to group $group with appkey $appKey and obtaining status messages from nodes with UUIDs: ${nodes.map { it.uuid }}"

    override suspend fun execute(): Result<Artifacts> =
        executeWithTimeMeasurement(::awaitAllStates, ::Artifacts)

    private suspend fun awaitAllStates(): Result<Map<Node, Boolean>> {
        GenericClient.getState(
            ModelIdentifier.GenericOnOffClient,
            StateType.OnOff,
            appKey,
            group.address,
            false,
        ).onFailure {
            return Result.failure(Failed(this, it))
        }
        val states = mutableMapOf<Node, Boolean>()
        GenericClient.onOffResponse.takeWhile {
            states.size < nodes.size
        }.collect { response ->
            states[nodes.first { it.primaryElementAddress == response.sourceAddress }] = response.on
        }
        return Result.success(states)
    }

    data class Artifacts(
        val states: Map<Node, Boolean>,
        val time: Duration
    )
}