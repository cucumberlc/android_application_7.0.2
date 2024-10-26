/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands

import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.errors.NodeControlError
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.AppKey
import com.siliconlab.bluetoothmesh.adk.node_control.NodeControl
import com.siliconlab.bluetoothmesh.adk.node_control.NodeControlCallback
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestCommand
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class BindAppKeyToNodeCommand(
    private val node: Node,
    private val appKey: AppKey,
) : IOPTestCommand<Unit>() {
    override val name = "Binding appkey to node"
    override val description = "Adding appkey $appKey to node with UUID ${node.uuid}"

    override suspend fun execute(): Result<Unit> =
        suspendCancellableCoroutine {
            NodeControl(node).bindAppKey(
                appKey,
                object : NodeControlCallback {
                    override fun succeed() {
                        it.resume(Result.success(Unit))
                    }

                    override fun error(error: NodeControlError) {
                        it.resume(Result.failure(Failed(this@BindAppKeyToNodeCommand, error)))
                    }
                }
            )
        }
}
