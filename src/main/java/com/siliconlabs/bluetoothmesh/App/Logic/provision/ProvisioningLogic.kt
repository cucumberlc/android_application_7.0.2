/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.provision

import com.siliconlabs.bluetoothmesh.App.Logic.NetworkConnectionLogic
import com.siliconlabs.bluetoothmesh.App.Models.AppState
import com.siliconlabs.bluetoothmesh.App.Models.DeviceToProvision

// injectable factory of ProvisioningHelper instances
class ProvisioningLogic(
    private val networkConnectionLogic: NetworkConnectionLogic
) {
    private fun getDirect(
        deviceToProvision: DeviceToProvision.Direct = (AppState.deviceToProvision as DeviceToProvision.Direct)
    ) = ProvisioningHelperDirect(deviceToProvision, networkConnectionLogic)

    private fun getRemote(
        deviceToProvision: DeviceToProvision.Remote = (AppState.deviceToProvision as DeviceToProvision.Remote)
    ) = ProvisioningHelperRemote(deviceToProvision, networkConnectionLogic)

    fun getForDevice(deviceToProvision: DeviceToProvision) =
        when (deviceToProvision) {
            is DeviceToProvision.Direct -> getDirect(deviceToProvision)
            is DeviceToProvision.Remote -> getRemote(deviceToProvision)
        }

    fun getForCurrentDevice() = getForDevice(AppState.deviceToProvision!!)
}