/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands

import com.siliconlab.bluetoothmesh.adk.configuration_control.ConfigurationControl
import com.siliconlab.bluetoothmesh.adk.configuration_control.NodeRetransmissionConfigurationCallback
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.errors.NodeControlError
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestCommand
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class SetRetransmissionCommand(
        private val node: Node
) : IOPTestCommand<Unit>() {
    override val name = "Setting retransmission"
    override val description = "$name on device with UUID: ${node.uuid}"

    override suspend fun execute(): Result<Unit> =
            suspendCancellableCoroutine {
                ConfigurationControl(node).setRetransmissionConfiguration(
                        2,
                        1,
                        object : NodeRetransmissionConfigurationCallback {
                            override fun success(
                                retransmissionCount: Int,
                                retransmissionIntervalSteps: Int
                            ) {
                                it.resume(Result.success(Unit))
                            }

                            override fun error(error: NodeControlError) {
                                it.resume(
                                    Result.failure(
                                        Failed(
                                            this@SetRetransmissionCommand,
                                            error
                                        )
                                    )
                                )
                            }
                        }
                )
            }
}