/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.provision

import com.siliconlab.bluetoothmesh.adk.Outcome
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.errors.StackError
import com.siliconlab.bluetoothmesh.adk.onFailure
import com.siliconlab.bluetoothmesh.adk.provisioning.CloseLinkReason
import com.siliconlab.bluetoothmesh.adk.provisioning.LinkResponse
import com.siliconlab.bluetoothmesh.adk.provisioning.LinkState
import com.siliconlab.bluetoothmesh.adk.provisioning.ProvisionerOOB
import com.siliconlab.bluetoothmesh.adk.provisioning.ProvisioningRecordsHandler
import com.siliconlab.bluetoothmesh.adk.provisioning.RemoteProvisioner
import com.siliconlab.bluetoothmesh.adk.provisioning.Status
import com.siliconlab.bluetoothmesh.adk.requireSuccess
import com.siliconlabs.bluetoothmesh.App.Logic.NetworkConnectionLogic
import com.siliconlabs.bluetoothmesh.App.Models.DeviceToProvision
import com.siliconlabs.bluetoothmesh.App.Utils.Message
import kotlinx.coroutines.flow.first
import org.tinylog.kotlin.Logger

class ProvisioningHelperRemote(
    device: DeviceToProvision.Remote,
    networkConnectionLogic: NetworkConnectionLogic
) : ProvisioningHelper<DeviceToProvision.Remote>(device, networkConnectionLogic) {

    override suspend fun executeProvisioning() = doProvision()

    override suspend fun executeProvisioningWithCertificates(provisionerOOB: ProvisionerOOB): Node {
        throw ProvisioningFailureException("Not yet implemented")
        //    return doProvision(provisionerOOB)
    }

    override suspend fun executeProvisioningWithRecords(
        provisioningRecordsHandler: ProvisioningRecordsHandler?
    ): Node {
        throw ProvisioningFailureException("Not yet implemented")
    }

    override suspend fun updateProvisionerOOBForOngoingProvisioning(provisionerOOB: ProvisionerOOB) {
        throw ProvisioningFailureException("Not yet implemented")
    }

    private suspend fun doProvision(provisionerOOB: ProvisionerOOB? = null): Node {
        return try {
            openRemoteBearer()
            RemoteProvisioner.provision(
                    serverAddress = provisionedDevice.serverAddress,
                    subnet = provisionedDevice.subnet,
                    uuid = provisionedDevice.uuid,
                    oob = provisionerOOB
            ).requireSuccess {
                throw ProvisioningFailureException(it.cause)
            }.data
        } finally {
            closeRemoteBearer()
        }
    }

    private suspend fun openRemoteBearer() {
        RemoteProvisioner.openLink(
            serverAddress = provisionedDevice.serverAddress,
            uuid = provisionedDevice.uuid,
            netKeyIndex = provisionedDevice.netKeyIndex,
            sourceElementIndex = 0
        )

        val linkReport = RemoteProvisioner.linkReport.first()
        if (linkReport.status != Status.Success
            && linkReport.state != LinkState.LinkActive) {
            throw ProvisioningFailureException(
                Message.error(
                    "Link could not be open. LinkState = ${linkReport.state}, LinkStatus = ${linkReport.status}"
                )
            )
        }
    }

    private suspend fun closeRemoteBearer(): Outcome<LinkResponse, StackError> {
        return RemoteProvisioner.closeLink(
            serverAddress = provisionedDevice.serverAddress,
            reason = CloseLinkReason.Success,
            netKeyIndex = provisionedDevice.netKeyIndex,
            sourceElementIndex = 0
        ).onFailure {
            Logger.error { "Failed to close remote bearer: $it" }
        }
    }
}