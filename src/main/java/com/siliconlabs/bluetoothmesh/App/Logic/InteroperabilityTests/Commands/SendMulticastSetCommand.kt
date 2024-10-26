/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands

import com.siliconlab.bluetoothmesh.adk.data_model.group.Group
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.AppKey
import com.siliconlab.bluetoothmesh.adk.functionality_control.generic.GenericClient
import com.siliconlab.bluetoothmesh.adk.onFailure
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestCommand
import kotlin.time.Duration

class SendMulticastSetCommand(
    private val group: Group,
    private val appKey: AppKey,
    private val state: Boolean,
    private val transactionId: UByte
) : IOPTestCommand<Duration>() {
    override val name =
        "Send multicast Generic OnOff Set message without acknowledgment"
    override val description =
        "$name (value = $state) to group $group with appkey $appKey (transaction ID = $transactionId)"

    override suspend fun execute(): Result<Duration> = executeWithTimeMeasurement(
        block@{
            GenericClient.setOnOff(
                appKey,
                group.address,
                state,
                0,
                0,
                true,
                transactionId,
                false,
            ).onFailure {
                return@block Result.failure(Failed(this, it))
            }
            return@block Result.success(Unit)
        },
        { _, settingTime -> settingTime }
    )
}
