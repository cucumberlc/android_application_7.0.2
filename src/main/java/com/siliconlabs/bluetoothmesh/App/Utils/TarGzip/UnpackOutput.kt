/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Utils.TarGzip

import java.io.File

sealed interface UnpackOutput {
    @JvmInline
    value class Success(val result: List<File>) : UnpackOutput

    @JvmInline
    value class Failure(val error: Throwable) : UnpackOutput

    data class Progress(val currentFile: String, val current: Long, val max: Long) : UnpackOutput {
        fun percent() = current.toFloat() / max
    }
}