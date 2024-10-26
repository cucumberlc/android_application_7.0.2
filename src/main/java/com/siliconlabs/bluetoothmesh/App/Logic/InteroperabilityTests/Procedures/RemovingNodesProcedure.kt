/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Procedures

import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTest
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestProcedure
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestProcedureSharedProperties
import com.siliconlabs.bluetoothmesh.App.Utils.flatMap
import kotlinx.coroutines.channels.ProducerScope

class RemovingNodesProcedure(
        sharedProperties: IOPTestProcedureSharedProperties
) : IOPTestProcedure<IOPTestProcedure.Artifact.Unit>(sharedProperties, retriesCount = 5) {
    override suspend fun execute(
            stateOutputChannel: ProducerScope<IOPTest.State>): Result<Artifact.Unit> {
        return run {
            getNodes(setOf(NodeType.LPN, NodeType.Friend))
        }.flatMap { nodes ->
            run {
                checkConnection()
            }.flatMap {
                removeAndFindNodes(nodes)
            }
        }.map {
            Artifact.Unit
        }
    }
}
