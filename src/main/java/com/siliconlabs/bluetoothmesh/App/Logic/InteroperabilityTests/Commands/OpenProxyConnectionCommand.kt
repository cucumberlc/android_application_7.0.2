/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */
package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands

import com.siliconlab.bluetoothmesh.adk.connectable_device.ConnectableDevice
import com.siliconlab.bluetoothmesh.adk.connectable_device.ConnectionCallback
import com.siliconlab.bluetoothmesh.adk.connectable_device.ProxyConnection
import com.siliconlab.bluetoothmesh.adk.errors.ConnectionError
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestCommand
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.time.Duration

class OpenProxyConnectionCommand(
        private val proxyConnection: ProxyConnection
) : IOPTestCommand<Duration>() {
    override val name = "Open proxy connection"
    override val description = name

    override suspend fun execute(): Result<Duration> = executeWithTimeMeasurement(
            block = {
                suspendCancellableCoroutine {
                    proxyConnection.connectToProxy(
                            object : ConnectionCallback {
                                override fun success(device: ConnectableDevice) {
                                    it.resume(Result.success(Unit))
                                }

                                override fun error(device: ConnectableDevice, error: ConnectionError) {
                                    it.resume(Result.failure<Unit>(Failed(this@OpenProxyConnectionCommand, error)))
                                }
                            }
                    )
                }
            },
            combine = { _, connectingTime -> connectingTime }
    )
}
