/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Utils

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import com.siliconlabs.bluetoothmesh.App.Utils.TarGzip.TarGzipFileUtils

@Suppress("FunctionName")
object ActivityResultContractsExt {
    /** Validates intent and wraps returned value in a [Result].*/
    abstract class QueryWithResult<I, O> : ActivityResultContract<I, Result<O>?>() {
        @Suppress("IntroduceWhenSubject")
        final override fun parseResult(resultCode: Int, intent: Intent?): Result<O>? {
            return when (resultCode) {
                Activity.RESULT_CANCELED -> null
                Activity.RESULT_OK -> when {
                    intent == null -> Result.failure(
                            NullPointerException("Result OK but data is null"))
                    else -> runCatching { parseResult(intent) }
                }
                else -> Result.failure(UnknownResultCodeException(resultCode))
            }
        }

        abstract fun parseResult(intent: Intent): O
    }

    abstract class GetUriWithResult<I> : QueryWithResult<I, Uri>() {
        override fun parseResult(intent: Intent): Uri {
            return requireNotNull(intent.data) { "Result OK but uri is null" }
        }
    }

    fun CertificateFilePickerContract() = CertificateFileUtils.CertificateFilePickerContract()
    fun GzipFilePickerContract() = TarGzipFileUtils.GzipFilePickerContract()
}

class UnknownResultCodeException(resultCode: Int) : RuntimeException(
        "Unknown result code: $resultCode")