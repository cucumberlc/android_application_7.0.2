/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Procedures.Provisioning

import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.provisioning.ProvisionerOOB
import com.siliconlab.bluetoothmesh.adk.provisioning.ProvisionerOOBControl
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands.SetRelayCommand
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestProcedureSharedProperties
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Procedures.ProvisioningProcedure
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.executeWithTimeout
import com.siliconlabs.bluetoothmesh.App.Utils.decodeHex
import com.siliconlabs.bluetoothmesh.App.Utils.encodeHex
import org.tinylog.Logger
import java.util.*
import kotlin.time.Duration.Companion.seconds

class ProvisioningRelayNodeProcedure(sharedProperties: IOPTestProcedureSharedProperties) :
    ProvisioningProcedure(
        sharedProperties,
        NodeType.Relay,
        timeout = 11.seconds
    ) {
    override val proxyFeatureEnabled = false

    override fun createProvisionerOOBControl() = IOPProvisionerStaticOOB()

    override suspend fun enableSpecificFeature(node: Node): Result<Unit> {
        return SetRelayCommand(node).executeWithTimeout(Companion.defaultCommandTimeout)
    }
}

class IOPProvisionerStaticOOB : ProvisionerOOBControl() {
    override fun isPublicKeyAllowed() = ProvisionerOOB.PUBLIC_KEY_ALLOWED.NO

    override fun minLengthOfOOBData() = 1
    override fun maxLengthOfOOBData() = 8

    override fun getAllowedAuthMethods() = setOf(ProvisionerOOB.AUTH_METHODS_ALLOWED.STATIC_OBB)

    override fun getAllowedOutputActions(): Set<ProvisionerOOB.OUTPUT_ACTIONS_ALLOWED> = emptySet()

    override fun getAllowedInputActions(): Set<ProvisionerOOB.INPUT_ACTIONS_ALLOWED> = emptySet()

    override fun oobPublicKeyRequest(uuid: UUID, algorithm: Int, publicKeyType: Int) =
        ProvisionerOOB.RESULT.SUCCESS

    override fun outputRequest(
        uuid: UUID,
        outputActions: ProvisionerOOB.OUTPUT_ACTIONS?,
        outputSize: Int
    ) = ProvisionerOOB.RESULT.SUCCESS

    // method inputOobDisplay is also skipped

    override fun authRequest(uuid: UUID): ProvisionerOOB.RESULT {
        Logger.debug { "Static OOB auth data requested for $uuid, providing ${staticAuthData.encodeHex()}" }
        provideAuthData(uuid, staticAuthData)
        return ProvisionerOOB.RESULT.SUCCESS
    }

    companion object {
        private val staticAuthData = "00112233445566778899aabbccddeeff".repeat(2).decodeHex()
    }
}