/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Models

import com.google.crypto.tink.subtle.EllipticCurves
import com.siliconlab.bluetoothmesh.adk.isSuccess
import com.siliconlab.bluetoothmesh.adk.provisioning.ProvisionerOOB
import com.siliconlab.bluetoothmesh.adk.provisioning.ProvisionerOOBControl
import java.security.PublicKey
import java.security.interfaces.ECPublicKey
import java.util.*

class CbpProvisionerOOB(publicKey: PublicKey) : ProvisionerOOBControl() {
    private val rawPublicKey: ByteArray

    init {
        val ecPoint = (publicKey as ECPublicKey).w
        val curve = publicKey.params.curve
        rawPublicKey = EllipticCurves
            .pointEncode(curve, EllipticCurves.PointFormatType.UNCOMPRESSED, ecPoint)
            .drop(1)
            .toByteArray()
    }

    override fun isPublicKeyAllowed() = ProvisionerOOB.PUBLIC_KEY_ALLOWED.YES

    override fun getAllowedAuthMethods() = setOf(ProvisionerOOB.AUTH_METHODS_ALLOWED.NO_OBB)

    override fun getAllowedOutputActions() = setOf(ProvisionerOOB.OUTPUT_ACTIONS_ALLOWED.NUMERIC)

    override fun getAllowedInputActions() = setOf(ProvisionerOOB.INPUT_ACTIONS_ALLOWED.NUMERIC)

    override fun minLengthOfOOBData(): Int = 0

    override fun maxLengthOfOOBData(): Int = 0

    override fun oobPublicKeyRequest(
        uuid: UUID, algorithm: Int,
        publicKeyType: Int
    ): ProvisionerOOB.RESULT {
        val result = supplyPublicKey(uuid, rawPublicKey)

        return if (result.isSuccess()) ProvisionerOOB.RESULT.SUCCESS
        else ProvisionerOOB.RESULT.ERROR
    }

    override fun outputRequest(
        uuid: UUID, outputActions: ProvisionerOOB.OUTPUT_ACTIONS?,
        outputSize: Int
    ) = ProvisionerOOB.RESULT.SUCCESS

    override fun authRequest(uuid: UUID) = ProvisionerOOB.RESULT.SUCCESS

    override fun inputOobDisplay(
        uuid: UUID, inputAction: ProvisionerOOB.INPUT_ACTIONS?,
        inputSize: Int, authData: ByteArray?
    ) {
    }
}