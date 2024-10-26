/*
 * Copyright © 2021 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Distributor.Utils

import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.functionality_control.firmware_distribution.UpdatePhase
import com.siliconlab.bluetoothmesh.adk.functionality_control.firmware_distribution.UpdatingNode
import com.siliconlabs.bluetoothmesh.App.FirmwareUpdater.DistributorConfiguration
import com.siliconlabs.bluetoothmesh.App.FirmwareUpdater.FirmwareUpdater
import com.siliconlabs.bluetoothmesh.App.FirmwareUpdater.InitiatorConfiguration

fun UpdatingNode.isUpdating(): Boolean = !hasFailed()

fun UpdatingNode.hasFailed(): Boolean =
        phase == UpdatePhase.TRANSFER_ERROR
                || phase == UpdatePhase.VERIFICATION_FAILED
                || phase == UpdatePhase.APPLY_FAILED
                || phase == UpdatePhase.UNKNOWN

val updatingNodeComparator = Comparator<UpdatingNode> { node1, node2 ->
    when {
        node1.isUpdating() && node2.hasFailed() -> 1
        node1.hasFailed() && node2.isUpdating() -> -1
        else -> 0
    }
}

/*fun buildFirmwareUpdater(distributor: Node) = FirmwareUpdater(
    distributor.elements.first(),
    distributor.boundAppKeys.first(),
    InitiatorConfiguration(2, 1),
    DistributorConfiguration(2, 1),
)*/

fun buildFirmwareUpdater(distributor: Node): FirmwareUpdater {
    var resFirmware: FirmwareUpdater? = null
    if (distributor.elements.size > 1) {
        println("DFU: Imp buildFirmwareUpdater ele0 ${distributor.elements[0]!!.address}")
        println("DFU: Imp buildFirmwareUpdater ele1 ${distributor.elements[1]!!.address}")
        resFirmware = FirmwareUpdater(
            distributor.elements[1]!!,
            distributor.boundAppKeys.first(),
            InitiatorConfiguration(2, 1),
            DistributorConfiguration(2, 1),
        )
    } else {
        resFirmware = FirmwareUpdater(
            distributor.elements.first()!!,
            distributor.boundAppKeys.first(),
            InitiatorConfiguration(2, 1),
            DistributorConfiguration(2, 1),
        )
    }
    return resFirmware
}