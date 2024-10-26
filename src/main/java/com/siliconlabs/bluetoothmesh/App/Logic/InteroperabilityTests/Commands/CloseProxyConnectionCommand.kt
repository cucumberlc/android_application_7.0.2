/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands

import com.siliconlab.bluetoothmesh.adk.connectable_device.ConnectableDevice
import com.siliconlab.bluetoothmesh.adk.connectable_device.DisconnectionCallback
import com.siliconlab.bluetoothmesh.adk.connectable_device.ProxyConnection
import com.siliconlab.bluetoothmesh.adk.errors.ConnectionError
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestCommand
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.time.Duration

class CloseProxyConnectionCommand(
        private val proxyConnection: ProxyConnection,
) : IOPTestCommand<Duration>() {
    override val name = "Close proxy connection"
    override val description = name

    override suspend fun execute(): Result<Duration> = executeWithTimeMeasurement(
            block = {
                suspendCancellableCoroutine {
                    proxyConnection.disconnect(
                            object : DisconnectionCallback {
                                override fun success(device: ConnectableDevice?) {
                                    it.resume(Result.success(Unit))
                                }

                                override fun error(device: ConnectableDevice?, error: ConnectionError) {
                                    it.resume(Result.failure<Unit>(Failed(this@CloseProxyConnectionCommand, error)))
                                }
                            }
                    )
                }
            },
            combine = { _, disconnectingTime -> disconnectingTime }
    )
}
