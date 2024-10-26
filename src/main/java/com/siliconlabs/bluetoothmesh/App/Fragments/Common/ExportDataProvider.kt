/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Common

import android.webkit.MimeTypeMap
import java.io.InputStream

interface ExportDataProvider {
    val data: InputStream
    val mimeType: String
    val defaultName: String

    val defaultFilename: String
        get() = "$defaultName.$extension"

    val extension: String?
        get() = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: defaultExtension

    companion object {
        const val defaultExtension = "txt"
    }
}