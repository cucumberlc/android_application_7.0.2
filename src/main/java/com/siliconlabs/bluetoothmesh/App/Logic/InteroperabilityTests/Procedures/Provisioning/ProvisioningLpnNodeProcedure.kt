/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Procedures.Provisioning

import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.Subnet
import com.siliconlab.bluetoothmesh.adk.provisioning.ProvisionerOOB
import com.siliconlab.bluetoothmesh.adk.provisioning.ProvisionerOOBControl
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands.ProvisionCommand
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTest
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestProcedureSharedProperties
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Procedures.ProvisioningProcedure
import com.siliconlabs.bluetoothmesh.App.Models.BluetoothConnectableDevice
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*
import kotlin.time.Duration.Companion.seconds

class ProvisioningLpnNodeProcedure(sharedProperties: IOPTestProcedureSharedProperties) : ProvisioningProcedure(
        sharedProperties,
        NodeType.LPN,
        timeout = 30.seconds
) {
    override suspend fun provisionDevice(device: BluetoothConnectableDevice, subnet: Subnet) =
            coroutineScope {
                val provisionerOOBControl = createProvisionerOOBControl()
                val observingState = launch { observeProvisionerOOBControlState(provisionerOOBControl) }
                val result = ProvisionCommand(device, subnet, provisionerOOBControl, proxyFeatureEnabled).executeWithLogging()
                result.also { observingState.cancel() }
            }

    override fun createProvisionerOOBControl() = IOPProvisionerInputOOB()

    override val proxyFeatureEnabled = false

    private suspend fun observeProvisionerOOBControlState(provisionerOOBControl: IOPProvisionerInputOOB) {
        provisionerOOBControl.inputOOBValueToDisplay.collect { value ->
            stateOutputChannel.send(
                    value?.let { WaitingForUserAction(value) } ?: IOPTest.State.Executing
            )
        }
    }

    override suspend fun enableSpecificFeature(node: Node) = Result.success(Unit) // LPN is board property

    data class WaitingForUserAction(val inputOOBValueToDisplay: Int) : IOPTest.State.InProgress {
        override fun toString() = "Execution in progress, waiting for user to enter OOB value on motherboard"
    }
}

class IOPProvisionerInputOOB : ProvisionerOOBControl() {
    private val mutableInputOOBValueToDisplay = MutableStateFlow<Int?>(null)
    val inputOOBValueToDisplay = mutableInputOOBValueToDisplay.asStateFlow()

    override fun isPublicKeyAllowed() = ProvisionerOOB.PUBLIC_KEY_ALLOWED.NO

    override fun minLengthOfOOBData() = 1
    override fun maxLengthOfOOBData() = 8

    override fun getAllowedAuthMethods() = setOf(ProvisionerOOB.AUTH_METHODS_ALLOWED.INPUT_OBB)

    override fun getAllowedOutputActions(): Set<ProvisionerOOB.OUTPUT_ACTIONS_ALLOWED> = emptySet()

    override fun getAllowedInputActions() = setOf(ProvisionerOOB.INPUT_ACTIONS_ALLOWED.PUSH)

    override fun oobPublicKeyRequest(uuid: UUID, algorithm: Int, publicKeyType: Int) =
        ProvisionerOOB.RESULT.SUCCESS

    override fun outputRequest(
        uuid: UUID,
        outputActions: ProvisionerOOB.OUTPUT_ACTIONS?,
        outputSize: Int
    ) = ProvisionerOOB.RESULT.SUCCESS

    override fun inputOobDisplay(
        uuid: UUID,
        inputAction: ProvisionerOOB.INPUT_ACTIONS,
        authNumber: Int
    ) {
        mutableInputOOBValueToDisplay.update { authNumber }
    }

    override fun authRequest(uuid: UUID) = ProvisionerOOB.RESULT.SUCCESS
}
