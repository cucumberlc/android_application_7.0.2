package com.siliconlabs.bluetoothmesh.App.Utils.TarGzip

import com.siliconlabs.bluetoothmesh.App.MeshApplication
import com.siliconlabs.bluetoothmesh.R
import io.reactivex.annotations.CheckReturnValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.utils.CountingInputStream
import org.tinylog.Logger
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object TarGzipStreamExtractor {
    private const val PROGRESS_THROTTLE_MS = 200L

    /** Extract [tgzInputStream] into list of files. Progress is emitted every [PROGRESS_THROTTLE_MS] if [streamSizeHint] is provided. */
    fun extract(tgzInputStream: InputStream, outputDir: File, streamSizeHint: Long = 0): Flow<UnpackOutput> = flow {
        val extractedFiles = mutableListOf<File>()
        var countingInputStream: CountingInputStream? = null
        var gzipIs: InputStream? = null
        var tarIs: InputStream? = null
        var nextProgressEmission = System.currentTimeMillis() + PROGRESS_THROTTLE_MS

        try {
            val destination = prepareDestinationDirectory(outputDir)

            countingInputStream = if (streamSizeHint > 0) {
                CountingInputStream(tgzInputStream)
            } else null

            gzipIs = GzipCompressorInputStream(countingInputStream ?: tgzInputStream)
            tarIs = TarArchiveInputStream(gzipIs)

            while (currentCoroutineContext().isActive) {
                val entry = tarIs.nextTarEntry ?: break

                if (entry.isDirectory) {
                    val tmpFile = File(destination, entry.name)
                    val created = tmpFile.mkdir()
                    if (!created) {
                        Logger.debug {
                            "Unable to create directory ${tmpFile.absolutePath}, during extraction of archive contents."
                        }
                    }
                } else {
                    val childFile = File(destination, entry.name)
                    extractedFiles.add(childFile)

                    val data = ByteArray(DEFAULT_BUFFER_SIZE)
                    val fileOs = FileOutputStream(childFile, false)
                    val destOs = BufferedOutputStream(fileOs, DEFAULT_BUFFER_SIZE)

                    try {
                        while (currentCoroutineContext().isActive) {
                            val count = tarIs.read(data, 0, DEFAULT_BUFFER_SIZE)
                            if (count == -1) break
                            destOs.write(data, 0, count)

                            if (countingInputStream != null && System.currentTimeMillis() > nextProgressEmission) {
                                emit(UnpackOutput.Progress(entry.name, countingInputStream.bytesRead, streamSizeHint))
                                nextProgressEmission = System.currentTimeMillis() + PROGRESS_THROTTLE_MS
                            }
                        }
                    } finally {
                        destOs.close()
                        fileOs.close()
                    }
                }
            }

            emit(UnpackOutput.Success(extractedFiles))
        } finally {
            tarIs?.close()
            gzipIs?.close()
            countingInputStream?.close()
        }
    }.catch {
        Logger.error(it)
        emit(UnpackOutput.Failure(it))
    }.flowOn(Dispatchers.IO).buffer(1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    @CheckReturnValue
    private fun prepareDestinationDirectory(destination: File): File {
        if (destination.exists()) {
            destination.listFiles()?.forEach {
                it.deleteRecursively()
            }
        } else {
            val mkdirSuccess = destination.mkdirs()

            if (!mkdirSuccess) {
                throw FileSystemException(destination, null, MeshApplication.appContext.getString(R.string.error_process_directory_creation, destination.absolutePath))
            }
        }
        return destination
    }
}