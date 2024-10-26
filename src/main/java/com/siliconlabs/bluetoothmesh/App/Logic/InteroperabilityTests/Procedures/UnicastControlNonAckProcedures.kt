/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Procedures

import com.siliconlab.bluetoothmesh.adk.configuration_control.ConfigurationControlSettings
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestProcedureSharedProperties
import kotlin.time.Duration.Companion.milliseconds

class UnicastControlNonAckProxyNodeProcedure(sharedProperties: IOPTestProcedureSharedProperties) :
    UnicastControlNonAckProcedure(
        sharedProperties,
        NodeType.Proxy
    )

class UnicastControlNonAckRelayNodeProcedure(sharedProperties: IOPTestProcedureSharedProperties) :
    UnicastControlNonAckProcedure(
        sharedProperties,
        NodeType.Relay
    )

class UnicastControlNonAckFriendNodeProcedure(sharedProperties: IOPTestProcedureSharedProperties) :
    UnicastControlNonAckProcedure(
        sharedProperties,
        NodeType.Friend
    )

class UnicastControlNonAckLpnNodeProcedure(sharedProperties: IOPTestProcedureSharedProperties) :
    UnicastControlNonAckProcedure(
        sharedProperties,
        NodeType.LPN,
        // timeout should be based on LPN node timeout - node can sleep when get/set messages are sent
        timeout = defaultObtainTimeout + ConfigurationControlSettings().lpnLocalTimeout.milliseconds
    )
