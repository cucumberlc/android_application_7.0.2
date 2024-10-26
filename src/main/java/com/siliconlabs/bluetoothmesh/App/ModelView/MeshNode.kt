/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.ModelView

import com.siliconlab.bluetoothmesh.adk.data_model.model.ModelIdentifier
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.functionality_control.scheduler.SchedulerActionResponse
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.Presenters.LC.LightLCProperty
import com.siliconlabs.bluetoothmesh.App.Models.DeviceFunctionality

data class MeshNode(val node: Node) {
    // Default values
    private val defaultTemperature = 800

    var onOffState = false
    var levelPercentage = 0
    var lightnessPercentage = 0
    var temperature = defaultTemperature
    var deltaUvPercentage = 0
    var functionality = DeviceFunctionality.FUNCTIONALITY.Unknown

    // Light LC
    var lcMode = false
    var lcOccupancyMode = false
    var lcOnOff = false
    var lcPropertyValue = "---"
    var lcProperty = LightLCProperty.AmbientLuxLevelOn

    // Time
    var timeRole = "-"
    var taiSeconds: ULong = 0u
    var subsecond: UByte = 0u
    var uncertainty: UByte = 0u
    var timeAuthority = false
    var taiUtcDelta = 0
    var timeZoneOffset: Short = 0

    // Scene
    var sceneOneStatus = SceneStatus.NOT_KNOWN
    var sceneTwoStatus = SceneStatus.NOT_KNOWN

    // Scheduler
    var schedules = MutableList(16) { false }
    var scheduleRegister = mutableMapOf<UByte, SchedulerActionResponse>()

    private var hintIsProxyEnabled: Boolean? = null

    fun supportsFirmwareUpdate(): Boolean {
        val sigModels = DeviceFunctionality.getSigModels(node, functionality)
        return node.boundAppKeys.isNotEmpty() && sigModels.find { it.modelIdentifier == ModelIdentifier.DeviceFirmwareUpdateServer }
            ?.boundAppKeys?.contains(node.boundAppKeys.first()) ?: false
    }

    fun supportsRemoteProvisioning(): Boolean {
        return node.elements.flatMap { it!!.sigModels }
            .any { it.modelIdentifier == ModelIdentifier.RemoteProvisioningServer }
    }

    fun giveProxyIsEnabledHint(isEnabled: Boolean?) {
        hintIsProxyEnabled = isEnabled
    }

    fun takeProxyIsEnabledHint() = hintIsProxyEnabled.also { hintIsProxyEnabled = null }

    enum class SceneStatus {
        NOT_KNOWN,
        NOT_STORED,
        STORED,
        ACTIVE,
    }
}