/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands

import com.siliconlab.bluetoothmesh.adk.data_model.model.Model
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.AppKey
import com.siliconlab.bluetoothmesh.adk.errors.NodeControlError
import com.siliconlab.bluetoothmesh.adk.functionality_binder.FunctionalityBinder
import com.siliconlab.bluetoothmesh.adk.functionality_binder.FunctionalityBinderCallback
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestCommand
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class BindAppKeyToModelCommand(
    private val model: Model,
    private val appKey: AppKey
) : IOPTestCommand<Unit>() {
    override val name = "Binding appkey to model"
    override val description = "Binding appkey $appKey to model " +
            "(model from node with UUID ${model.element.node.uuid})"

    override suspend fun execute(): Result<Unit> =
        suspendCancellableCoroutine {
            FunctionalityBinder(appKey).bindModel(
                model,
                object : FunctionalityBinderCallback {
                    override fun succeed(succeededModels: MutableList<Model>, appKey: AppKey) {
                        it.resume(Result.success(Unit))
                    }

                    override fun error(
                        failedModels: MutableList<Model>,
                        appKey: AppKey,
                        error: NodeControlError
                    ) {
                        it.resume(Result.failure(Failed(this@BindAppKeyToModelCommand, error)))
                    }
                }
                )
            }
}
