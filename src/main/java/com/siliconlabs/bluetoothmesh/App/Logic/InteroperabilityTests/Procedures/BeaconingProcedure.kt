/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Procedures

import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands.BeaconingCommand
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTest
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestProcedure
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestProcedureSharedProperties
import com.siliconlabs.bluetoothmesh.App.Utils.flatMap
import kotlinx.coroutines.channels.ProducerScope
import kotlin.time.Duration

abstract class BeaconingProcedure(
        sharedProperties: IOPTestProcedureSharedProperties,
        private val type: NodeType,
        private val scanMode: Int,
        private val timeout: Duration
) : IOPTestProcedure<IOPTestProcedure.TimeWithTimeoutArtifact>(sharedProperties, retriesCount = 3) {

    override suspend fun execute(
            stateOutputChannel: ProducerScope<IOPTest.State>): Result<TimeWithTimeoutArtifact> {
        return run {
            beacon()
        }.flatMap { (device, time) ->
            run {
                checkTime("beaconing", time, timeout)
            }.map {
                storeDevice(type, device)
                TimeWithTimeoutArtifact("beaconing", time, timeout)
            }
        }
    }

    private suspend fun beacon(): Result<BeaconingCommand.Artifacts> {
        return BeaconingCommand(type, scanMode, timeout).executeWithLogging()
    }
}
