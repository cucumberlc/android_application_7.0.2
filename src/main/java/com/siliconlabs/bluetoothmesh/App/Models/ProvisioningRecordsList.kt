/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Models

import com.siliconlab.bluetoothmesh.adk.errors.ProvisioningError
import com.siliconlab.bluetoothmesh.adk.errors.StackError
import com.siliconlab.bluetoothmesh.adk.provisioning.records.ProvisioningRecord
import com.siliconlab.bluetoothmesh.adk.provisioning.records.ProvisioningRecordCallback
import com.siliconlab.bluetoothmesh.adk.provisioning.records.ProvisioningRecordId
import com.siliconlab.bluetoothmesh.adk.provisioning.records.ProvisioningRecordsControl
import com.siliconlab.bluetoothmesh.adk.provisioning.records.ProvisioningRecordsListCallback
import com.siliconlabs.bluetoothmesh.App.Utils.CertificateData
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.suspendCoroutine

data class ProvisioningRecordsList(val records: List<ProvisioningRecord>) {
    val deviceCertificate: CertificateData? by lazy {
        records.find {
            it.recordId.recordName == "Device Certificate"
        }?.data?.let { CertificateData(it) }
    }

    val uriExists = records.find {
        it.recordId.recordName == "URI"
    } != null

    val intermediateCertificatesExist = records.any {
        it.recordId.recordName.contains("Intermediate Certificate")
    }

    companion object {
        private const val PROVISIONING_RECORDS_FETCH_TIMEOUT = 10_000L

        suspend fun fetchFrom(control: ProvisioningRecordsControl) =
                withTimeoutOrNull(PROVISIONING_RECORDS_FETCH_TIMEOUT) {
                    ProvisioningRecordsList(
                            control.fetchProvisioningRecordIdList().map { recordId ->
                                control.fetchProvisioningRecord(recordId)
                            })
                }

        private suspend fun ProvisioningRecordsControl.fetchProvisioningRecordIdList() = suspendCoroutine {
            this.getProvisioningRecordsList(object : ProvisioningRecordsListCallback {
                override fun success(recordIds: List<ProvisioningRecordId>) {
                    it.resumeWith(Result.success(recordIds))
                }

                override fun error(error: ProvisioningError) {
                    it.resumeWith(Result.failure(RuntimeException(error.toString())))
                }
            })
        }

        private suspend fun ProvisioningRecordsControl.fetchProvisioningRecord(
                recordId: ProvisioningRecordId): ProvisioningRecord = suspendCoroutine {
            this.getProvisioningRecord(recordId.recordId, object : ProvisioningRecordCallback {
                override fun success(record: ProvisioningRecord) {
                    it.resumeWith(Result.success(record))
                }

                override fun error(error: StackError) {
                    it.resumeWith(Result.failure(RuntimeException(error.toString())))
                }
            })
        }
    }
}