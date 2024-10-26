/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Procedures

import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestProcedureSharedProperties

class UnicastControlAckProxyNodeProcedure(sharedProperties: IOPTestProcedureSharedProperties) :
    UnicastControlAckProcedure(
        sharedProperties,
        NodeType.Proxy
    )

class UnicastControlAckRelayNodeProcedure(sharedProperties: IOPTestProcedureSharedProperties) :
    UnicastControlAckProcedure(
        sharedProperties,
        NodeType.Relay
    )

class UnicastControlAckFriendNodeProcedure(sharedProperties: IOPTestProcedureSharedProperties) :
    UnicastControlAckProcedure(
        sharedProperties,
        NodeType.Friend
    )

class UnicastControlAckLpnNodeProcedure(sharedProperties: IOPTestProcedureSharedProperties) :
    UnicastControlAckProcedure(
        sharedProperties,
        NodeType.LPN
    )