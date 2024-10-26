/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Models

import com.siliconlab.bluetoothmesh.adk.functionality_control.standaloneupdater.Firmware

// AppState holds volatile fragment arguments: if they are missing fragments referencing them must
// collapse cleanly upon recreation
object AppState {
    // unrecoverable: user needs to redo the scan to obtain fresh instance of DeviceToProvision
    var deviceToProvision : DeviceToProvision? = null

    // this can be removed if distribution idle logic is redesigned
    // so it happens within one viewmodel instead of 3 fragments and leftover dialog presenter
    var firmware: Firmware? = null

    fun isProcessingDeviceDirectly() = deviceToProvision is DeviceToProvision.Direct
    fun isProcessingDeviceRemotely() = deviceToProvision is DeviceToProvision.Remote
}