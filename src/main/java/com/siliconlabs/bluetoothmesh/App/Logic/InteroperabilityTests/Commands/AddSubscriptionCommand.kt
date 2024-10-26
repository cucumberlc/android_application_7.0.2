/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands

import com.siliconlab.bluetoothmesh.adk.data_model.group.Group
import com.siliconlab.bluetoothmesh.adk.data_model.model.Model
import com.siliconlab.bluetoothmesh.adk.functionality_control.subscription.Subscription
import com.siliconlab.bluetoothmesh.adk.onFailure
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestCommand
import kotlinx.coroutines.flow.first

class AddSubscriptionCommand(
        private val model: Model,
        private val group: Group
) : IOPTestCommand<Unit>() {
    override val name = "Adding subscription"
    override val description = "Adding model subscription to group $group " +
            "(model from node with UUID ${model.element.node.uuid})"

    override suspend fun execute(): Result<Unit> {
        Subscription.add(
            model,
            group.address,
        ).onFailure {
            return Result.failure(Failed(this, it))
        }
        Subscription.subscriptionResponse.first()
        return Result.success(Unit)
    }
}
