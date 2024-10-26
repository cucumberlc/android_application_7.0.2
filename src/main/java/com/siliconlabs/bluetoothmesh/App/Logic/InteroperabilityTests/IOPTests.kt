/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests

import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Procedures.*
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Procedures.Provisioning.ProvisioningFriendNodeProcedure
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Procedures.Provisioning.ProvisioningLpnNodeProcedure
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Procedures.Provisioning.ProvisioningProxyNodeProcedure
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Procedures.Provisioning.ProvisioningRelayNodeProcedure

object IOPTests {
    fun create(sharedEnvironment: IOPTestProcedureSharedProperties): LinkedHashSet<IOPTest<*>> =
        listOf(
            IOPTestIdentity.BeaconingProxyNode to ::BeaconingProxyNodeProcedure,
            IOPTestIdentity.BeaconingRelayNode to ::BeaconingRelayNodeProcedure,
            IOPTestIdentity.BeaconingFriendNode to ::BeaconingFriendNodeProcedure,
            IOPTestIdentity.BeaconingLpnNode to ::BeaconingLpnNodeProcedure,
            IOPTestIdentity.ProvisioningProxyNode to ::ProvisioningProxyNodeProcedure,
            IOPTestIdentity.ProvisioningRelayNode to ::ProvisioningRelayNodeProcedure,
            IOPTestIdentity.ProvisioningFriendNode to ::ProvisioningFriendNodeProcedure,
            IOPTestIdentity.ProvisioningLpnNode to ::ProvisioningLpnNodeProcedure,
            IOPTestIdentity.UnicastControlAckProxyNode to ::UnicastControlAckProxyNodeProcedure,
            IOPTestIdentity.UnicastControlNonAckProxyNode to ::UnicastControlNonAckProxyNodeProcedure,
            IOPTestIdentity.UnicastControlAckRelayNode to ::UnicastControlAckRelayNodeProcedure,
            IOPTestIdentity.UnicastControlNonAckRelayNode to ::UnicastControlNonAckRelayNodeProcedure,
            IOPTestIdentity.UnicastControlAckFriendNode to ::UnicastControlAckFriendNodeProcedure,
            IOPTestIdentity.UnicastControlNonAckFriendNode to ::UnicastControlNonAckFriendNodeProcedure,
            IOPTestIdentity.UnicastControlAckLpnNode to ::UnicastControlAckLpnNodeProcedure,
            IOPTestIdentity.UnicastControlNonAckLpnNode to ::UnicastControlNonAckLpnNodeProcedure,
            IOPTestIdentity.MulticastControlNodes to ::MulticastControlProcedure,
            IOPTestIdentity.RemoveNodesFromNetwork to ::RemovingNodesProcedure,
            IOPTestIdentity.AddNodeToNetwork to ::AddingNodeProcedure,
            IOPTestIdentity.ConnectingNetwork to ::ConnectingProcedure,
            IOPTestIdentity.PostTesting to ::PostProcedure
        ).mapTo(linkedSetOf()) { (id, constructor) ->
            IOPTest(id, constructor(sharedEnvironment))
        }
}
