/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Utils.TarGzip

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.siliconlab.bluetoothmesh.adk.functionality_control.standaloneupdater.Firmware
import com.siliconlabs.bluetoothmesh.App.MeshApplication
import com.siliconlabs.bluetoothmesh.App.Models.FirmwareFile
import com.siliconlabs.bluetoothmesh.App.Models.ManifestContainer
import com.siliconlabs.bluetoothmesh.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.FileNotFoundException

object TarGzipFirmwareFactory {
    private const val FIRMWARE_UNPACK_DIR = "upload_firmwares"
    private const val FIRMWARE_MANIFEST_NAME = "manifest.json"
    private val MAX_FIRMWARE_FILE_SIZE = 10uL * 1024u * 1024u   // 10 MB

    class MissingManifestException(message: String) : FileNotFoundException(message)
    class MalformedManifestException(message: String) : FileNotFoundException(message)
    class FirmwareCreationException(message: String) : RuntimeException(message)

    fun getFirmwareUnpackDir(context: Context) = File(context.cacheDir, FIRMWARE_UNPACK_DIR)

    fun createFirmwareFromContentUri(context: Context, tgzUri: Uri): Flow<Output> = flow {
        val extractedFiles = unpackAndGetFiles(context, tgzUri)
        val firmwareFile = findAndParseManifestFile(extractedFiles)
        val firmware = createFirmware(extractedFiles, firmwareFile)
        emit(Output.Success(firmwareFile.firmwareId, firmware))
    }.catch { emit(Output.Failure(it)) }
            .flowOn(Dispatchers.IO)
            .buffer(1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private suspend fun FlowCollector<Output>.unpackAndGetFiles(context: Context, tgzUri: Uri): List<File> {
        return TarGzipFileUtils.unpackContentUri(context, tgzUri, getFirmwareUnpackDir(context), MAX_FIRMWARE_FILE_SIZE)
                .mapNotNull {
                    when (it) {
                        is UnpackOutput.Progress -> {
                            emit(Output.UnpackProgress(it))
                            null
                        }
                        is UnpackOutput.Failure -> throw it.error
                        is UnpackOutput.Success -> it.result
                    }
                }
                .first()
    }

    private fun findAndParseManifestFile(extractedFiles: List<File>): FirmwareFile {
        val manifestFile = extractedFiles.singleOrNull { file ->
            Uri.fromFile(file).lastPathSegment == FIRMWARE_MANIFEST_NAME
        } ?: throw MissingManifestException(MeshApplication.appContext.getString(R.string.error_firmware_file_malformed))

        val gson = Gson()
        val manifestContainer = gson.fromJson(manifestFile.readText(), ManifestContainer::class.java)
        return manifestContainer.manifestContent.firmwareFile
    }

    private fun createFirmware(extractedFiles: List<File>, firmwareFile: FirmwareFile): Firmware {
        val metadataBinaryFile = firmwareFile.metadata?.takeIf { it.isNotEmpty() }?.let { metadata ->
            getExtractedBinaryFile(extractedFiles, metadata, "Metadata")
        }
        val firmwareBinaryFile = getExtractedBinaryFile(extractedFiles, firmwareFile.firmwareName, "Firmware")

        return runCatching {
            FirmwareFactory.createFirmware(
                    firmwareId = firmwareFile.firmwareId,
                    firmwareData = firmwareBinaryFile.readBytes(),
                    metadata = metadataBinaryFile?.readBytes()
            )
        }.getOrElse {
            throw FirmwareCreationException(MeshApplication.appContext.getString(R.string.error_firmware_creation))
        }
    }

    private fun getExtractedBinaryFile(extractedFiles: List<File>,
                                       fileName: String,
                                       fileDescription: String): File {
        return extractedFiles.find { file ->
            Uri.fromFile(file).lastPathSegment == fileName
        } ?: throw MalformedManifestException(MeshApplication.appContext.getString(R.string.error_firmware_binary_missing, fileDescription, fileName))
    }

    sealed interface Output {
        @JvmInline
        value class Success private constructor(private val result: Pair<String, Firmware>) : Output {
            constructor(id: String, firmware: Firmware) : this(id to firmware)

            val firmwareId
                get() = result.first
            val firmware
                get() = result.second
        }

        @JvmInline
        value class Failure(val error: Throwable) : Output

        @JvmInline
        value class UnpackProgress(val progress: UnpackOutput.Progress) : Output
    }
}