/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands

import com.siliconlab.bluetoothmesh.adk.configuration_control.ConfigurationControl
import com.siliconlab.bluetoothmesh.adk.configuration_control.FactoryResetCallback
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.errors.NodeControlError
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestCommand
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class RemoveNodeCommand(
        private val node: Node
) : IOPTestCommand<Unit>() {
    override val name = "Remove node"
    override val description = "$name with UUID ${node.uuid}"

    override suspend fun execute(): Result<Unit> {
        return suspendCancellableCoroutine {
            ConfigurationControl(node).factoryReset(
                    object : FactoryResetCallback {
                        override fun success() {
                            it.resume(Result.success(Unit))
                        }

                        override fun error(error: NodeControlError) {
                            it.resume(Result.failure(Failed(this@RemoveNodeCommand, error)))
                        }
                    }
            )
        }
    }
}
