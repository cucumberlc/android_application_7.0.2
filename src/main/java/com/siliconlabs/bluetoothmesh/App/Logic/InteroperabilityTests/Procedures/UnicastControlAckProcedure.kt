/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Procedures

import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands.SendUnicastSetCommand
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTest
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestProcedure
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestProcedureSharedProperties
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.executeWithTimeout
import com.siliconlabs.bluetoothmesh.App.Utils.flatMap
import com.siliconlabs.bluetoothmesh.App.Utils.with
import kotlinx.coroutines.channels.ProducerScope

abstract class UnicastControlAckProcedure(
        sharedProperties: IOPTestProcedureSharedProperties,
        private val type: NodeType
) : IOPTestProcedure<IOPTestProcedure.TimeArtifact>(sharedProperties, retriesCount = 5) {

    private val stateToSet = true

    override suspend fun execute(
            stateOutputChannel: ProducerScope<IOPTest.State>): Result<TimeArtifact> {
        return run {
            getNode(type)
        }.flatMap { node ->
            run {
                checkConnection()
            }.flatMap {
                setOnState(node)
            }
        }
    }

    private suspend fun setOnState(node: Node): Result<TimeArtifact> {
        return run {
            getElement(node) with getAppKey()
        }.flatMap { (element, appKey) ->
            performAsTransaction { id ->
                SendUnicastSetCommand(element, appKey, stateToSet, true, id).executeWithTimeout(
                    defaultCommandTimeout
                )
            }
        }.map { settingTime ->
            TimeArtifact("setting state ($stateToSet)", settingTime)
        }
    }
}
