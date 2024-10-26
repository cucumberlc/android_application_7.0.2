/*
 * Copyright © 2021 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Distributor.Update

import com.siliconlab.bluetoothmesh.adk.functionality_control.firmware_distribution.DistributorPhase
import com.siliconlab.bluetoothmesh.adk.functionality_control.firmware_distribution.UpdatingNode
import com.siliconlabs.bluetoothmesh.App.PresenterView

interface UpdateNodesView : PresenterView {
    fun setActionBarTitle(title: String)

    fun showUpdatingNodes(updatingNodes: Collection<UpdatingNode>)

    fun showDistributionPhase(distributorPhase: DistributorPhase)

    fun showFirmwareId(firmwareId: String)

    fun showWarningToast(message: String)

    fun showDisconnectionDialog()

    fun exitDeviceFirmwareUpdate()
}