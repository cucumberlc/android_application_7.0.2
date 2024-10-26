/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Utils.extensions

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File

fun Context.getFileName(uri: Uri): String? = when (uri.scheme) {
    ContentResolver.SCHEME_CONTENT -> getContentFileName(uri)
    else -> uri.path?.let(::File)?.name
}

private fun Context.getContentFileName(uri: Uri): String? = getFromContentResolver(uri,
        OpenableColumns.DISPLAY_NAME, Cursor::getString)

fun Context.getFileSize(uri: Uri): Long? = when (uri.scheme) {
    ContentResolver.SCHEME_CONTENT -> getContentFileSize(uri)
    else -> uri.path?.let(::File)?.length()
}

private fun Context.getContentFileSize(uri: Uri): Long? = getFromContentResolver(uri,
        OpenableColumns.SIZE, Cursor::getLong)

private inline fun <T> Context.getFromContentResolver(uri: Uri, columnName: String,
                                                      columnOperation: Cursor.(Int) -> T): T? {
    return runCatching {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            cursor.moveToFirst()
            return@use cursor.getColumnIndexOrThrow(columnName).let { cursor.columnOperation(it) }
        }
    }.getOrNull()
}

// Use to check unknown/ambiguous file format.
fun Context.getMimeType(uri: Uri, fallback: String = "*/*"): String {
    return if (ContentResolver.SCHEME_CONTENT == uri.scheme) {
        contentResolver.getType(uri)
    } else {
        val fileExt = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExt.lowercase())
    } ?: fallback
}