/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Utils.apiExtensions

import com.siliconlab.bluetoothmesh.adk.data_model.DeviceCompositionData

fun DeviceCompositionData.formattedFirmwareId(): String {
    val companyId = String.format("%04X", cid)
    val vendorVersionIdentifier = String.format("%04X", vid)
    return "$companyId $vendorVersionIdentifier"
}