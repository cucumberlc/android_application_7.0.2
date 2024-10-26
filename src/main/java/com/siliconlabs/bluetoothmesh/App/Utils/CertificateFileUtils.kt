/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.siliconlabs.bluetoothmesh.App.MeshApplication
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.getFileName
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.getFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

object CertificateFileUtils {
    private val DER_MIME_TYPES = arrayOf(
            "application/octet-stream",
            "application/x-x509-ca-cert"
    )
    private const val MAX_CERTIFICATE_FILE_SIZE = 5 * 1024 * 1024 // 5 MB

    class CertificateFilePickerContract : ActivityResultContractsExt.QueryWithResult<Unit, CertificateFile>() {
        override fun createIntent(context: Context, input: Unit): Intent {
            return Intent(Intent.ACTION_GET_CONTENT).apply {
                type = DER_MIME_TYPES.joinToString("|")
                putExtra(Intent.EXTRA_MIME_TYPES, DER_MIME_TYPES)
            }
        }

        override fun parseResult(intent: Intent): CertificateFile {
            val uri = intent.data!!
            require(MeshApplication.appContext.getFileSize(uri)
                    ?.let { it > MAX_CERTIFICATE_FILE_SIZE } == false) { "File is too large" }
            return CertificateFile(uri)
        }
    }

    /** Certificate file needs to [loadData] to [getLoadedData] safely. */
    data class CertificateFile(val uri: Uri) {
        private var data: CertificateData? = null

        fun getLoadedData() = data!!
        fun getName(context: Context) = context.getFileName(uri)

        suspend fun loadData(context: Context): Result<CertificateData> {
            return withContext(Dispatchers.IO) {
                runCatching { loadCertificateDataFromFile(context) }.apply { ensureActive() }
            }.onSuccess {
                data = it
            }
        }

        private suspend fun loadCertificateDataFromFile(context: Context) = runInterruptible {
            context.contentResolver.openInputStream(uri).use {
                CertificateData(it!!.readBytes())
            }
        }
    }
}

@JvmInline
value class CertificateData(val data: ByteArray)