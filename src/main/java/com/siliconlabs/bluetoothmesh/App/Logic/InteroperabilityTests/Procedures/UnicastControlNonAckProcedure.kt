/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Procedures

import com.siliconlab.bluetoothmesh.adk.data_model.element.Element
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.AppKey
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands.SendUnicastGetCommand
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands.SendUnicastSetCommand
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTest
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestProcedure
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestProcedureSharedProperties
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.executeWithTimeout
import com.siliconlabs.bluetoothmesh.App.Utils.flatMap
import com.siliconlabs.bluetoothmesh.App.Utils.with
import kotlinx.coroutines.channels.ProducerScope
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

abstract class UnicastControlNonAckProcedure(
        sharedProperties: IOPTestProcedureSharedProperties,
        private val type: NodeType,
        private val timeout: Duration = defaultObtainTimeout
) : IOPTestProcedure<IOPTestProcedure.SettingArtifact>(sharedProperties, retriesCount = 6) {

    private val stateToSet = false

    override suspend fun execute(
            stateOutputChannel: ProducerScope<IOPTest.State>): Result<SettingArtifact> {
        return run {
            getNode(type)
        }.flatMap { node ->
            run {
                checkConnection()
            }.flatMap {
                setAndGetOffState(node)
            }
        }
    }

    private suspend fun setAndGetOffState(node: Node): Result<SettingArtifact> {
        return run {
            getElement(node) with getAppKey()
        }.flatMap { (element, appKey) ->
            setOffState(element, appKey) with getOffState(element, appKey)
        }.map { (settingTimeArtifact, gettingTimeArtifact) ->
            SettingArtifact(settingTimeArtifact, gettingTimeArtifact)
        }
    }

    private suspend fun setOffState(element: Element, appKey: AppKey): Result<TimeArtifact> {
        return performAsTransaction { id ->
            SendUnicastSetCommand(element, appKey, stateToSet, false, id).executeWithTimeout(
                defaultCommandTimeout
            )
        }.map { time ->
            TimeArtifact("setting state ($stateToSet)", time)
        }
    }

    private suspend fun getOffState(
        element: Element,
        appKey: AppKey
    ): Result<TimeWithTimeoutArtifact> {
        return sendUnicastGetNonAck(element, appKey).fold(
            onSuccess = { (state, time) ->
                run {
                    checkState(state)
                }.flatMap {
                    checkTime("obtaining state ($stateToSet)", time, timeout)
                }.map {
                    TimeWithTimeoutArtifact("obtaining state ($stateToSet)", time, timeout)
                }
            },
            onFailure = {
                Result.failure(it)
            }
        )
    }

    private fun checkState(state: Boolean): Result<Unit> {
        return if (state == stateToSet) Result.success(Unit)
        else Result.failure(WrongState(state, stateToSet))
    }

    private suspend fun sendUnicastGetNonAck(
        element: Element,
        appKey: AppKey
    ): Result<SendUnicastGetCommand.Artifacts> {
        return SendUnicastGetCommand(element, appKey).executeWithTimeout(defaultCommandTimeout)
    }

    class WrongState(actual: Boolean, expected: Boolean) :
        ProcedureError("Obtained wrong Generic OnOff state (actual: $actual, expected $expected)")

    companion object {
        @JvmStatic
        protected val defaultObtainTimeout = 500.milliseconds
    }
}
