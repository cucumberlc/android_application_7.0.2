/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands

import com.siliconlab.bluetoothmesh.adk.data_model.element.Element
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.AppKey
import com.siliconlab.bluetoothmesh.adk.fold
import com.siliconlab.bluetoothmesh.adk.functionality_control.generic.GenericClient
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestCommand
import kotlinx.coroutines.flow.first
import kotlin.time.Duration

class SendUnicastSetCommand(
    private val element: Element,
    private val appKey: AppKey,
    private val state: Boolean,
    private val withAcknowledgment: Boolean,
    private val transactionId: UByte
) : IOPTestCommand<Duration>() {
    override val name =
        "Send unicast Generic OnOff Set message ${if (withAcknowledgment) "with" else "without"} acknowledgment"
    override val description =
        "$name (value = $state) to element ${element.address} with appkey $appKey (transaction ID = $transactionId) " +
                "(element from node with UUID ${element.node.uuid})"

    override suspend fun execute(): Result<Duration> = executeWithTimeMeasurement(
        {
            GenericClient.setOnOff(
                appKey,
                element.address,
                state,
                0,
                0,
                withAcknowledgment,
                transactionId,
                false,
            ).fold(
                onSuccess = {
                    if(withAcknowledgment) GenericClient.onOffResponse.first()
                    Result.success(Unit)
                },
                onFailure = {
                    Result.failure(Failed(this, it))
                }
            )
        },
        { _, settingTime -> settingTime }
    )
}