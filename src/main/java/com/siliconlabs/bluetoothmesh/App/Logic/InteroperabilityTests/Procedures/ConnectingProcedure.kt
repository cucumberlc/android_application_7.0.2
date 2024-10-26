/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Procedures

import com.siliconlab.bluetoothmesh.adk.connectable_device.ProxyConnection
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands.CloseProxyConnectionCommand
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands.OpenProxyConnectionCommand
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTest
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestProcedure
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestProcedureSharedProperties
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.executeWithTimeout
import com.siliconlabs.bluetoothmesh.App.Utils.flatMap
import com.siliconlabs.bluetoothmesh.App.Utils.with
import kotlinx.coroutines.channels.ProducerScope
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ConnectingProcedure(
        sharedProperties: IOPTestProcedureSharedProperties
) : IOPTestProcedure<IOPTestProcedure.TimeWithTimeoutArtifact>(sharedProperties, retriesCount = 3) {

    val timeout: Duration = 30.seconds

    override suspend fun execute(
            stateOutputChannel: ProducerScope<IOPTest.State>): Result<TimeWithTimeoutArtifact> {
        return run {
            checkConnection()
        }.flatMap { connection ->
            reopenConnection(connection).map { reconnectingTime ->
                TimeWithTimeoutArtifact("reconnecting to network", reconnectingTime, timeout)
            }
        }.flatMap { artifact ->
            run {
                checkThatSubnetHasThreeNodes()
            }.map {
                artifact
            }
        }
    }

    private suspend fun reopenConnection(connection: ProxyConnection): Result<Duration> {
        return run {
            closeConnection(connection) with openConnection(connection)
        }.flatMap { (disconnectingTime, connectingTime) ->
            val totalTime = disconnectingTime + connectingTime
            checkTime("reconnecting", totalTime, timeout)
        }
    }

    private suspend fun closeConnection(connection: ProxyConnection): Result<Duration> {
        return CloseProxyConnectionCommand(connection).executeWithTimeout(timeout)
    }

    private suspend fun openConnection(connection: ProxyConnection): Result<Duration> {
        return OpenProxyConnectionCommand(connection).executeWithTimeout(timeout)
    }

    private fun checkThatSubnetHasThreeNodes(): Result<Unit> =
            getNodes(setOf(NodeType.Proxy, NodeType.Relay,
                    NodeType.Friend)).flatMap { Result.success(Unit) }
}