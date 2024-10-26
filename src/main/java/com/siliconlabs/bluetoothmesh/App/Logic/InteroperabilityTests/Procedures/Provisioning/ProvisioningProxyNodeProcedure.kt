/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Procedures.Provisioning

import com.siliconlab.bluetoothmesh.adk.connectable_device.ProxyConnection
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.provisioning.ProvisionerOOB
import com.siliconlab.bluetoothmesh.adk.provisioning.ProvisionerOOBControl
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestProcedureSharedProperties
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Procedures.ProvisioningProcedure
import java.util.*

class ProvisioningProxyNodeProcedure(sharedProperties: IOPTestProcedureSharedProperties) : ProvisioningProcedure(
        sharedProperties,
        NodeType.Proxy
) {
    override val proxyFeatureEnabled = true

    override fun createProvisionerOOBControl() = IOPProvisionerNoOOB()

    override suspend fun enableSpecificFeature(node: Node) = Result.success(Unit) // proxy feature was enabled during provisioning

    override suspend fun handleOpenProxyConnection(proxyConnection: ProxyConnection): Result<Unit> {
        storeSubnetConnection(proxyConnection)
        return Result.success(Unit)
    }
}

class IOPProvisionerNoOOB: ProvisionerOOBControl() {
    override fun isPublicKeyAllowed() = ProvisionerOOB.PUBLIC_KEY_ALLOWED.NO

    override fun minLengthOfOOBData() = 0
    override fun maxLengthOfOOBData() = 0

    override fun getAllowedAuthMethods(): Set<ProvisionerOOB.AUTH_METHODS_ALLOWED> = setOf() // no OOB authentication is used

    override fun getAllowedOutputActions(): Set<ProvisionerOOB.OUTPUT_ACTIONS_ALLOWED> = setOf()

    override fun getAllowedInputActions(): Set<ProvisionerOOB.INPUT_ACTIONS_ALLOWED> = setOf()

    override fun oobPublicKeyRequest(uuid: UUID, algorithm: Int, publicKeyType: Int) =
        ProvisionerOOB.RESULT.SUCCESS

    override fun outputRequest(
        uuid: UUID,
        outputActions: ProvisionerOOB.OUTPUT_ACTIONS?,
        outputSize: Int
    ) = ProvisionerOOB.RESULT.SUCCESS

    // method inputOobDisplay is also skipped

    override fun authRequest(uuid: UUID) = ProvisionerOOB.RESULT.SUCCESS
}
