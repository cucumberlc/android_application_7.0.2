/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Utils.TarGzip

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.siliconlabs.bluetoothmesh.App.MeshApplication
import com.siliconlabs.bluetoothmesh.App.Utils.ActivityResultContractsExt
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.getFileSize
import com.siliconlabs.bluetoothmesh.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.IOException

object TarGzipFileUtils {
    private const val MIME_TYPE_GZIP = "application/gzip"
    private const val MIME_TYPE_GZIP_OLD = "application/x-gzip"
    private const val MIME_TYPE_TGZ = "application/x-gtar-compressed"

    private val mimeTypeArray = arrayOf(MIME_TYPE_GZIP, MIME_TYPE_GZIP_OLD, MIME_TYPE_TGZ)

    class GzipFilePickerContract : ActivityResultContractsExt.GetUriWithResult<Unit>() {
        override fun createIntent(context: Context, input: Unit): Intent {
            return createSelectGzipFileIntent()
        }
    }

    fun createSelectGzipFileIntent(): Intent {
        return Intent(Intent.ACTION_GET_CONTENT).apply {
            type = mimeTypeArray.joinToString("|")
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypeArray)
        }
    }

    fun unpackContentUri(context: Context, uri: Uri, outputDir: File, maxFileSize: ULong = ULong.MAX_VALUE): Flow<UnpackOutput> = flow {
        if (!checkFileSize(context, uri, maxFileSize)) {
            throw IllegalArgumentException(MeshApplication.appContext.getString(R.string.error_process_file_too_large))
        }

        val fileStream = context.contentResolver.openInputStream(uri)
                ?: throw IOException(MeshApplication.appContext.getString(R.string.error_open_input_stream))

        TarGzipStreamExtractor.extract(fileStream, outputDir, fileStream.available().toLong()).collect(::emit)
    }.catch { emit(UnpackOutput.Failure(it)) }
            .flowOn(Dispatchers.IO)
            .buffer(1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private fun checkFileSize(context: Context, uri: Uri, maxFileSize: ULong): Boolean {
        return try {
            context.getFileSize(uri)!!.toULong() <= maxFileSize
        } catch (exc: Exception) {
            false
        }
    }
}