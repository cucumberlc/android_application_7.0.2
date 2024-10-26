/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.provision

import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.errors.GattConnectionError
import com.siliconlab.bluetoothmesh.adk.onFailure
import com.siliconlab.bluetoothmesh.adk.onSuccess
import com.siliconlab.bluetoothmesh.adk.provisioning.NodeProperties
import com.siliconlab.bluetoothmesh.adk.provisioning.ProvisionerConfiguration
import com.siliconlab.bluetoothmesh.adk.provisioning.ProvisionerConnection
import com.siliconlab.bluetoothmesh.adk.provisioning.ProvisionerOOB
import com.siliconlab.bluetoothmesh.adk.provisioning.ProvisioningRecordsHandler
import com.siliconlabs.bluetoothmesh.App.Logic.NetworkConnectionLogic
import com.siliconlabs.bluetoothmesh.App.Models.DeviceToProvision
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.safeResume
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.safeResumeWithException
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

class ProvisioningHelperDirect(
    device: DeviceToProvision.Direct,
    networkConnectionLogic: NetworkConnectionLogic,
) : ProvisioningHelper<DeviceToProvision.Direct>(device, networkConnectionLogic) {
    companion object {
        private val defaultConfiguration = ProvisionerConfiguration().apply {
            isGettingDeviceCompositionData = true
            isKeepingProxyConnection = true
            isUsingOneGattConnection = true
        }
    }

    private var provisionerConnection: ProvisionerConnection? = null

    override suspend fun executeProvisioning() = doProvision()
    override suspend fun executeProvisioningWithCertificates(provisionerOOB: ProvisionerOOB) =
        doProvision(provisionerOOB)

    override suspend fun executeProvisioningWithRecords(
        provisioningRecordsHandler: ProvisioningRecordsHandler?,
    ) = doProvision(provisioningRecordsHandler = provisioningRecordsHandler)

    override suspend fun updateProvisionerOOBForOngoingProvisioning(provisionerOOB: ProvisionerOOB) {
        provisionerConnection!!.provisionerOOB = provisionerOOB
    }

    private suspend fun doProvision(
        provisionerOOB: ProvisionerOOB? = null,
        provisioningRecordsHandler: ProvisioningRecordsHandler? = null,
        configuration: ProvisionerConfiguration = defaultConfiguration,
        nodeProperties: NodeProperties? = null,
    ): Node {
        val provisionerConnection = ProvisionerConnection(
            provisionedDevice.device,
            provisionedDevice.subnet
        ).also {
            it.provisionerOOB = provisionerOOB
            it.provisioningRecordsHandler = provisioningRecordsHandler
            this.provisionerConnection = it
        }

        provisioningScope!!.launch { interruptProvisionOnDeviceDisconnect() }

        return try {
            suspendCancellableCoroutine { continuation ->
                provisionerConnection.provision(configuration, nodeProperties) { out ->
                    out.onSuccess {
                        continuation.safeResume(it)
                    }.onFailure {
                        continuation.safeResumeWithException(ProvisioningFailureException(it))
                    }
                }
            }.also { storeProxyConnection() }
        } finally {
            this.provisionerConnection = null
        }
    }

    private suspend fun interruptProvisionOnDeviceDisconnect() {
        provisionedDevice.device.connectionStateFlow()
            .dropWhile { isConnected -> !isConnected }  // wait for device to connect first
            .first { isConnected -> !isConnected }
        throw ProvisioningFailureException.critical(GattConnectionError.Status.Disconnected())
    }

    private fun storeProxyConnection() {
        provisionerConnection?.proxyConnection?.let {
            networkConnectionLogic.setEstablishedProxyConnection(it, provisionedDevice.subnet)
        }
    }
}