/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands

import com.siliconlab.bluetoothmesh.adk.data_model.element.Element
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.AppKey
import com.siliconlab.bluetoothmesh.adk.data_model.model.ModelIdentifier
import com.siliconlab.bluetoothmesh.adk.fold
import com.siliconlab.bluetoothmesh.adk.functionality_control.generic.GenericClient
import com.siliconlab.bluetoothmesh.adk.functionality_control.generic.StateType
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestCommand
import kotlinx.coroutines.flow.first
import kotlin.time.Duration

class SendUnicastGetCommand(
    private val element: Element,
    private val appKey: AppKey,
) : IOPTestCommand<SendUnicastGetCommand.Artifacts>() {
    override val name: String = "Send unicast Generic OnOff Get message"
    override val description = "$name to element ${element.address} with appkey $appKey " +
            "(element from node with UUID ${element.node.uuid})"

    override suspend fun execute(): Result<Artifacts> = executeWithTimeMeasurement(
        {
            GenericClient.getState(
                ModelIdentifier.GenericOnOffClient,
                StateType.OnOff,
                appKey,
                element.address,
                false,
            ).fold(
                onSuccess = { Result.success(GenericClient.onOffResponse.first().on) },
                onFailure = { Result.failure(Failed(this, it)) }
            )
        },
        { state, time ->
            Artifacts(state, time)
        },
    )

    data class Artifacts(
        val state: Boolean,
        val time: Duration
    )
}