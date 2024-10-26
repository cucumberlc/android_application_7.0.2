/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.provision

import com.siliconlab.bluetoothmesh.adk.BluetoothMesh
import com.siliconlab.bluetoothmesh.adk.configuration_control.ConfigurationControl
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.fold
import com.siliconlab.bluetoothmesh.adk.functionality_control.cbp.CertificateValidationFailure
import com.siliconlab.bluetoothmesh.adk.functionality_control.cbp.CertificateValidator
import com.siliconlab.bluetoothmesh.adk.provisioning.ProvisionerOOB
import com.siliconlab.bluetoothmesh.adk.provisioning.ProvisioningRecordsHandler
import com.siliconlabs.bluetoothmesh.App.Logic.BluetoothState
import com.siliconlabs.bluetoothmesh.App.Logic.NetworkConnectionLogic
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.App.Models.CbpProvisionerOOB
import com.siliconlabs.bluetoothmesh.App.Models.DeviceToProvision
import com.siliconlabs.bluetoothmesh.App.Models.MeshNodeManager
import com.siliconlabs.bluetoothmesh.App.Models.ProvisioningRecordsList
import com.siliconlabs.bluetoothmesh.App.Utils.CertificateData
import com.siliconlabs.bluetoothmesh.App.Utils.Message
import com.siliconlabs.bluetoothmesh.App.Utils.MessageBearer
import com.siliconlabs.bluetoothmesh.App.Utils.apiExtensions.getDeviceCompositionData
import com.siliconlabs.bluetoothmesh.App.Utils.apiExtensions.setProxy
import com.siliconlabs.bluetoothmesh.App.Utils.withTitle
import com.siliconlabs.bluetoothmesh.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed class ProvisioningHelper<T : DeviceToProvision>(
    protected val provisionedDevice: T,
    protected val networkConnectionLogic: NetworkConnectionLogic,
) {
    companion object {
        private const val ENABLE_PROXY_TIMEOUT_MS = 5_000L
        private fun defaultSubnet() = BluetoothMesh.network.subnets.first()
    }

    protected var provisioningScope: CoroutineScope? = null
        private set

    private fun isProvisioningOngoing() = provisioningScope != null

    suspend fun provision() = provisioningRootLogic { executeProvisioning() }

    suspend fun provisionWithCertificates(
        deviceCertificate: CertificateData,
        rootCertificate: CertificateData,
    ): ProvisioningResult {
        val provisionerOOB = createProvisionerOOBFromCertificates(
            deviceCertificate,
            rootCertificate
        ) {
            return Failure(it.toString()).withTitle(R.string.cbp_certificate_invalid_title)
        }
        return provisioningRootLogic { executeProvisioningWithCertificates(provisionerOOB!!) }
    }

    suspend fun provisionWithRecords(
        lazyCertificateProvider: LazyCertificateProvider,
    ): ProvisioningResult {
        val provisioningRecordsHandler = ProvisioningRecordsHandler { control, continueCallback ->
            provisioningScope?.launch {
                val records = ProvisioningRecordsList.fetchFrom(control)
                    ?: throw ProvisioningFailureException(
                        R.string.provisioning_records_error_fetch_timeout
                    )

                runCatching {
                    updateProvisionerOOBFromCallback(lazyCertificateProvider, records)
                }.onFailure {
                    if (it !is CancellationException) throw ProvisioningFailureException(it)
                }
                ensureActive()
                continueCallback.continueProvisioning()
            }
        }
        return provisioningRootLogic { executeProvisioningWithRecords(provisioningRecordsHandler) }
    }

    protected abstract suspend fun executeProvisioning(): Node

    protected abstract suspend fun executeProvisioningWithCertificates(
        provisionerOOB: ProvisionerOOB,
    ): Node

    protected abstract suspend fun executeProvisioningWithRecords(
        provisioningRecordsHandler: ProvisioningRecordsHandler? = null,
    ): Node

    protected abstract suspend fun updateProvisionerOOBForOngoingProvisioning(
        provisionerOOB: ProvisionerOOB,
    )

    private suspend fun provisioningRootLogic(
        executeProvisioning: suspend () -> Node,
    ): ProvisioningResult {
        if (isProvisioningOngoing())
            return Failure("Provisioning is already active")
        removeNodeFromDatabase()

        return try {
            coroutineScope {
                provisioningScope = this
                launch { interruptProvisionOnBluetoothOff() }
                val node = executeProvisioning()
                coroutineContext.cancelChildren()   // cancel connection observers
                val meshNode = MeshNodeManager.getMeshNode(node)
                val proxyState = setupDeviceOnSuccess(meshNode)
                Success(meshNode, proxyState)
            }
        } catch (provisioningFailure: ProvisioningFailureException) {
            Failure.from(provisioningFailure)
        } finally {
            provisioningScope = null
        }
    }

    private fun removeNodeFromDatabase() {
        BluetoothMesh.network.subnets.flatMap {
            it.nodes
        }.find {
            it.uuid == provisionedDevice.uuid
        }?.apply {
            removeOnlyFromLocalStructure()
        }
        MeshNodeManager.removeMeshNode(provisionedDevice.uuid)
    }

    private suspend fun interruptProvisionOnBluetoothOff() {
        BluetoothState.isEnabled.first { isEnabled -> !isEnabled }
        throw ProvisioningFailureException.critical(
            R.string.error_message_disconnected_from_device_bluetooth
        )
    }

    private suspend fun setupDeviceOnSuccess(meshNode: MeshNode): SetupState {
        val node = meshNode.node
        node.name = provisionedDevice.name
        val setupState = enableProxyForNewDevice(node)

        val hintIsProxyEnabled = when (setupState.proxyEstablished) {
            FeatureState.ESTABLISHED -> true
            FeatureState.NOT_SUPPORTED -> false
            FeatureState.FAILED -> null
        }
        meshNode.giveProxyIsEnabledHint(hintIsProxyEnabled)
        return setupState
    }

    private suspend fun enableProxyForNewDevice(node: Node): SetupState {
        var proxySetupState = FeatureState.FAILED

        withTimeoutOrNull(ENABLE_PROXY_TIMEOUT_MS) {
            ConfigurationControl(node).run {
                if (setProxy(true)) proxySetupState = FeatureState.ESTABLISHED
            }
        }
        return SetupState(proxySetupState)
    }

    private suspend fun ConfigurationControl.nodeIsNotLowPower(): Boolean {
        val compositionData = node.deviceCompositionData ?: getDeviceCompositionData()
        return compositionData?.supportsLowPower == false
    }

    private inline fun createProvisionerOOBFromCertificates(
        deviceCertificate: CertificateData,
        rootCertificate: CertificateData,
        onCertificateValidationFailure: (CertificateValidationFailure) -> Unit,
    ): ProvisionerOOB? {
        return CertificateValidator(
            deviceCertificate = deviceCertificate.data,
            uuid = provisionedDevice.uuid,
            trustedCertificate = rootCertificate.data
        ).validate().fold(
            onSuccess = {
                val publicKey = CertificateValidator.getPublicKey(deviceCertificate.data)
                CbpProvisionerOOB(publicKey)
            },
            onFailure = {
                onCertificateValidationFailure(it)
                null
            }
        )
    }

    private suspend fun updateProvisionerOOBFromCallback(
        lazyCertificateProvider: LazyCertificateProvider,
        records: ProvisioningRecordsList,
    ) {
        var provisionerOOB: ProvisionerOOB?

        do {
            val rootCertificate = lazyCertificateProvider.getRootCertificate(records)

            if (records.deviceCertificate == null) {
                throw ProvisioningFailureException(
                    R.string.provisioning_records_missing_certificate_advice
                )
            }
            provisionerOOB = createProvisionerOOBFromCertificates(
                records.deviceCertificate!!, rootCertificate
            ) {
                lazyCertificateProvider.onCertificateDenied(it)
            }
            currentCoroutineContext().ensureActive()
        } while (provisionerOOB == null)

        updateProvisionerOOBForOngoingProvisioning(provisionerOOB)
    }

    class ProvisioningFailureException(message: Message) : MessageBearer.Exception(message) {
        companion object : MessageBearer.Factory<ProvisioningFailureException>() {
            override fun create(message: Message) = ProvisioningFailureException(message)
        }
    }

    interface LazyCertificateProvider {
        suspend fun getRootCertificate(records: ProvisioningRecordsList): CertificateData

        suspend fun onCertificateDenied(
            certificateValidationFailure: CertificateValidationFailure,
        )
    }

    enum class FeatureState {
        NOT_SUPPORTED, FAILED, ESTABLISHED
    }

    data class SetupState(val proxyEstablished: FeatureState) {
        fun createMessage(): Message? {
            return if (proxyEstablished == FeatureState.FAILED)
                Message.info(R.string.message_error_proxy_setup_fail)
            else null
        }
    }

    sealed interface ProvisioningResult

    data class Success(val meshNode : MeshNode, val setupState: SetupState) : ProvisioningResult
    data class Failure(override val messageContent: Message) : ProvisioningResult, MessageBearer {
        companion object : MessageBearer.Factory<Failure>() {
            override fun create(message: Message) = Failure(message)
        }
    }
}