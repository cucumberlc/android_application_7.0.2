/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Utils.apiExtensions

import com.siliconlab.bluetoothmesh.adk.configuration_control.ConfigurationControl
import com.siliconlab.bluetoothmesh.adk.configuration_control.GetDeviceCompositionDataCallback
import com.siliconlab.bluetoothmesh.adk.configuration_control.SetNodeBehaviourCallback
import com.siliconlab.bluetoothmesh.adk.data_model.DeviceCompositionData
import com.siliconlab.bluetoothmesh.adk.errors.NodeControlError
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.safeResume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.tinylog.Logger

suspend fun ConfigurationControl.getDeviceCompositionData(page: Int = 0): DeviceCompositionData? {
    return suspendCancellableCoroutine {
        getDeviceCompositionData(page, object : GetDeviceCompositionDataCallback {
            override fun success(compositionData: ByteArray) {
                node.overrideDeviceCompositionData(compositionData)
                it.safeResume(node.deviceCompositionData!!)
            }

            override fun error(error: NodeControlError) {
                it.safeResume(null)
                Logger.error { "Failed to get composition data page: $page error: $error" }
            }
        })
    }
}

suspend fun ConfigurationControl.setProxy(enable: Boolean): Boolean {
    return suspendCancellableCoroutine {
        setProxy(enable, object : SetNodeBehaviourCallback {
            override fun success() {
                it.safeResume(true)
            }

            override fun error(error: NodeControlError) {
                it.safeResume(false)
                Logger.error { "failed to setProxy enable: $enable, error: $error" }
            }
        })
    }
}