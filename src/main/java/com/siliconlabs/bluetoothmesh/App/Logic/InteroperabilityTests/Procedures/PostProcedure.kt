/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Procedures

import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTest
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestProcedure
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestProcedureSharedProperties
import com.siliconlabs.bluetoothmesh.App.Utils.flatMap
import kotlinx.coroutines.channels.ProducerScope

class PostProcedure(
        sharedProperties: IOPTestProcedureSharedProperties
) : IOPTestProcedure<IOPTestProcedure.Artifact.Unit>(sharedProperties) {
    override suspend fun execute(
            stateOutputChannel: ProducerScope<IOPTest.State>): Result<Artifact.Unit> {
        return run {
            getNodes(setOf(NodeType.Friend, NodeType.Relay, NodeType.Proxy))
        }.flatMap { nodes ->
            run {
                checkConnection()
            }.flatMap {
                removeAndFindNodes(nodes)
            }
        }.flatMap {
            findLpnDevice()
        }.flatMap {
            checkIfSubnetIsEmpty()
        }.map {
            Artifact.Unit
        }
    }

    private suspend fun findLpnDevice(): Result<Unit> {
        return beaconDevice(NodeType.LPN).flatMap { Result.success(Unit) }
    }

    private fun checkIfSubnetIsEmpty(): Result<Unit> {
        return getSubnet().flatMap { subnet ->
            subnet.nodes.size.let { count ->
                if (count == 0) Result.success(Unit)
                else Result.failure(WrongNodesCount(count, 0))
            }
        }
    }

    class WrongNodesCount(
            count: Int,
            expectedCount: Int
    ) : ProcedureError(
            "Wrong number of nodes in subnet (actual: $count, expected: $expectedCount)"
    )
}
