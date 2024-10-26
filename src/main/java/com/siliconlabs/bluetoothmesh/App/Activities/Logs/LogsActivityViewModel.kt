package com.siliconlabs.bluetoothmesh.App.Activities.Logs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.siliconlabs.bluetoothmesh.App.Fragments.Common.ExportDataProvider
import com.siliconlabs.bluetoothmesh.App.Utils.FileLogger
import java.io.File
import java.io.InputStream

class LogsActivityViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private companion object {
        private const val KEY_SAVE_LOG = "KEY_SAVE_LOG"
    }

    val logFile = savedStateHandle.getStateFlow<File?>(KEY_SAVE_LOG, null)

    fun getLogs(): List<File> {
        return FileLogger.directory?.listFiles()?.toList()
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun setSelectedLog(item: File) {
        savedStateHandle[KEY_SAVE_LOG] = item
    }

    val exportDataProvider = object : ExportDataProvider {
        override val data: InputStream get() = logFile.value!!.inputStream()
        override val mimeType: String get() = "text/plain"
        override val defaultName: String get() = logFile.value!!.nameWithoutExtension
    }
}
