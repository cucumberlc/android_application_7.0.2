/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Procedures

import android.bluetooth.le.ScanSettings
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestProcedureSharedProperties
import kotlin.time.Duration.Companion.milliseconds

class BeaconingProxyNodeProcedure(sharedProperties: IOPTestProcedureSharedProperties) :
    BeaconingProcedure(
        sharedProperties,
        NodeType.Proxy,
        ScanSettings.SCAN_MODE_LOW_LATENCY,
        1500.milliseconds
    )

class BeaconingRelayNodeProcedure(sharedProperties: IOPTestProcedureSharedProperties) :
    BeaconingProcedure(
        sharedProperties,
        NodeType.Relay,
        ScanSettings.SCAN_MODE_LOW_LATENCY,
        2500.milliseconds,
    )

class BeaconingFriendNodeProcedure(sharedProperties: IOPTestProcedureSharedProperties) :
    BeaconingProcedure(
        sharedProperties,
        NodeType.Friend,
        ScanSettings.SCAN_MODE_LOW_POWER,
        4000.milliseconds
    )

class BeaconingLpnNodeProcedure(sharedProperties: IOPTestProcedureSharedProperties) :
    BeaconingProcedure(
        sharedProperties,
        NodeType.LPN,
        ScanSettings.SCAN_MODE_BALANCED,
        5500.milliseconds,
    )
