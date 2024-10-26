/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Procedures.Provisioning

import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.Subnet
import com.siliconlab.bluetoothmesh.adk.provisioning.ProvisionerOOB
import com.siliconlab.bluetoothmesh.adk.provisioning.ProvisionerOOBControl
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands.ProvisionCommand
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands.SetFriendCommand
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTest
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestProcedureSharedProperties
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Procedures.ProvisioningProcedure
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.executeWithTimeout
import com.siliconlabs.bluetoothmesh.App.Models.BluetoothConnectableDevice
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*
import kotlin.time.Duration.Companion.seconds

class ProvisioningFriendNodeProcedure(sharedProperties: IOPTestProcedureSharedProperties) : ProvisioningProcedure(
        sharedProperties,
        NodeType.Friend,
        timeout = 30.seconds
) {
    override suspend fun provisionDevice(device: BluetoothConnectableDevice, subnet: Subnet) =
            coroutineScope {
                val provisionerOOBControl = createProvisionerOOBControl()
                val observingState = launch { observeProvisionerOOBControlState(provisionerOOBControl) }
                val result = ProvisionCommand(device, subnet, provisionerOOBControl, proxyFeatureEnabled).executeWithLogging()
                result.also { observingState.cancel() }
            }

    override fun createProvisionerOOBControl() = IOPOutputProvisionerOOB()

    override val proxyFeatureEnabled = false

    private suspend fun observeProvisionerOOBControlState(provisionerOOBControl: IOPOutputProvisionerOOB) {
        provisionerOOBControl.onProvidedOutputOOBValue.collect { callback ->
            stateOutputChannel.send(
                    callback?.let { WaitingForUserAction(callback) } ?: IOPTest.State.Executing
            )
        }
    }

    override suspend fun enableSpecificFeature(node: Node): Result<Unit> {
        return SetFriendCommand(node).executeWithTimeout(defaultCommandTimeout)
    }

    data class WaitingForUserAction(val onProvidedOutputOOBValue: (Int?) -> Unit) : IOPTest.State.InProgress {
        override fun toString() = "Execution in progress, waiting for user to provide OOB value"
    }
}

class IOPOutputProvisionerOOB : ProvisionerOOBControl() {
    private val mutableOnProvidedOutputOOBValue = MutableStateFlow<((Int?) -> Unit)?>(null)
    val onProvidedOutputOOBValue = mutableOnProvidedOutputOOBValue.asStateFlow()

    override fun isPublicKeyAllowed() = ProvisionerOOB.PUBLIC_KEY_ALLOWED.NO

    override fun minLengthOfOOBData() = 1
    override fun maxLengthOfOOBData() = 8

    override fun oobPublicKeyRequest(uuid: UUID, algorithm: Int, publicKeyType: Int) =
        ProvisionerOOB.RESULT.SUCCESS

    override fun getAllowedAuthMethods() = setOf(ProvisionerOOB.AUTH_METHODS_ALLOWED.OUTPUT_OOB)

    override fun getAllowedOutputActions() = setOf(ProvisionerOOB.OUTPUT_ACTIONS_ALLOWED.NUMERIC)

    override fun getAllowedInputActions() = emptySet<ProvisionerOOB.INPUT_ACTIONS_ALLOWED>()

    override fun outputRequest(
        uuid: UUID,
        outputActions: ProvisionerOOB.OUTPUT_ACTIONS?,
        outputSize: Int
    ): ProvisionerOOB.RESULT {
        val callback: (Int?) -> Unit = { outputOOBValue ->
            outputOOBValue?.let { provideNumericAuthData(uuid, it) }
            mutableOnProvidedOutputOOBValue.update { null }
        }
        mutableOnProvidedOutputOOBValue.update { callback }

        return ProvisionerOOB.RESULT.SUCCESS
    }

    // method inputOobDisplay is also skipped

    override fun authRequest(uuid: UUID) = ProvisionerOOB.RESULT.SUCCESS
}