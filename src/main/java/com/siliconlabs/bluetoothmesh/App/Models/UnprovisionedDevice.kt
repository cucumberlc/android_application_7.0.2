/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Models

import com.siliconlab.bluetoothmesh.adk.provisioning.OobInformation
import com.siliconlab.bluetoothmesh.adk.provisioning.ScanReport
import java.util.*

data class UnprovisionedDevice(
        val rssi: Byte?,
        val uuid: UUID,
        val oobInformation: Set<OobInformation>,
        val name: String = "Unknown device",
) {
    constructor(scanReport: ScanReport) : this(
            scanReport.rssi,
            scanReport.uuid,
            scanReport.oob
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UnprovisionedDevice

        if (rssi != other.rssi) return false
        if (uuid != other.uuid) return false
        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        var result = rssi?.toInt() ?: 0
        result = 31 * result + uuid.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }
}
