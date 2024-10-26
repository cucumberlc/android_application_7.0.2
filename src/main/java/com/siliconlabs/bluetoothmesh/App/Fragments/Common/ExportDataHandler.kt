/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Common

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withCreated
import com.siliconlabs.bluetoothmesh.App.MeshApplication
import com.siliconlabs.bluetoothmesh.App.MeshFileProvider
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

// interfaces
fun interface ExportSaveDataHandler {
    fun selectSaveLocation()
}

fun interface ExportShareDataHandler {
    fun showShareSheet(context: Context)
}

interface ExportDataHandler : ExportShareDataHandler, ExportSaveDataHandler

// factories
fun <T> T.exportSaveDataHandler(
    getExportDataProvider: () -> ExportDataProvider
): ExportSaveDataHandler where T : ActivityResultCaller, T : LifecycleOwner {
    require(lifecycle.currentState == Lifecycle.State.INITIALIZED) { "this has to be invoked during initialization" }
    val handler = ExportSaveDataHandlerImpl(getExportDataProvider)
    // registration is a little delayed so activity / fragment attaches and viewmodels are available
    lifecycleScope.launch {
        withCreated { handler.registerLauncher(this@exportSaveDataHandler)  }
    }
    return handler
}

fun <T> T.exportShareDataHandler(
    getExportDataProvider: () -> ExportDataProvider
): ExportShareDataHandler where T : ActivityResultCaller, T : LifecycleOwner {
    return ExportShareDataHandlerImpl(getExportDataProvider)
}

fun <T> T.exportDataHandler(
    getExportDataProvider: () -> ExportDataProvider
): ExportDataHandler where T : ActivityResultCaller, T : LifecycleOwner {
    return object : ExportDataHandler,
        ExportSaveDataHandler by exportSaveDataHandler(getExportDataProvider),
        ExportShareDataHandler by exportShareDataHandler(getExportDataProvider) {}
}

// implementations

private class ExportSaveDataHandlerImpl(
    private val getExportDataProvider: () -> ExportDataProvider
) : ExportSaveDataHandler {
    private lateinit var launcher: ActivityResultLauncher<String>

    override fun selectSaveLocation() {
        launcher.launch(getExportDataProvider().defaultFilename)
    }

    fun registerLauncher(host: ActivityResultCaller) {
        val mimeType = getExportDataProvider().mimeType
        val selectLocationContract = ActivityResultContracts.CreateDocument(mimeType)
        launcher = host.registerForActivityResult(selectLocationContract) { uri ->
            uri?.let { saveData(uri) }
        }
    }

    private fun saveData(uri: Uri) {
        MeshApplication.appContext.contentResolver.openOutputStream(uri)
            ?.use { outputStream ->
                getExportDataProvider().data.copyTo(outputStream)
            }
    }
}

private class ExportShareDataHandlerImpl(
    private val getExportDataProvider: () -> ExportDataProvider
) : ExportShareDataHandler {

    override fun showShareSheet(context: Context) {
        val provider = getExportDataProvider()
        val temporaryFile = prepareTemporaryFile(provider)
        val temporaryUri = FileProvider.getUriForFile(
            context,
            getFileProviderAuthorities(context),
            temporaryFile
        )

        val shareIntent = ShareCompat.IntentBuilder(context)
            .setType(provider.mimeType)
            .addStream(temporaryUri)
            .createChooserIntent()

        context.startActivity(shareIntent)
    }

    @Throws(IOException::class)
    private fun prepareTemporaryFile(provider: ExportDataProvider) =
        File(exportedDirectory, provider.defaultFilename).apply {
            outputStream().use { outputStream ->
                provider.data.copyTo(outputStream)
            }
        }

    // see: https://stackoverflow.com/a/56624419
    private fun getFileProviderAuthorities(context: Context): String {
        val component = ComponentName(context, MeshFileProvider::class.java)

        val info = if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            context.packageManager.getProviderInfo(component, PackageManager.GET_META_DATA)
        } else {
            context.packageManager.getProviderInfo(
                component,
                PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            )
        }
        return info.authority
    }

    private companion object {
        val exportedDirectory = File(MeshApplication.appContext.cacheDir, "exported")
            .apply { mkdirs() }
    }
}