package com.aiyougame.companion.engine

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * OkHttp-based implementation of [ModelDownloader].
 *
 * Features:
 * - Breakpoint resume via Range header
 * - SHA-256 integrity check after download
 * - Progress reported via [progressFlow]
 * - All IO on [Dispatchers.IO]
 *
 * Target: context.filesDir/models/gemma-4-E4B-it-Q4_0.gguf
 */
@Singleton
class ModelDownloaderImpl @Inject constructor(
    @Suppress("UNUSED") private val context: Context,
    @Named("download") private val okHttpClient: OkHttpClient,
) : ModelDownloader {

    companion object {
        private const val MODEL_DIR = "models"
        private const val MODEL_FILE = "gemma-4-E4B-it-Q4_0.gguf"
        private const val BUFFER_SIZE = 8192
    }

    private val _progressFlow = MutableStateFlow(
        ModelDownloader.Progress(bytesDownloaded = 0, totalBytes = 0, isComplete = false)
    )

    /** In-progress download state, protected by Dispatchers.IO in [download] */
    @Volatile
    private var ongoingBytesDownloaded = 0L

    @Volatile
    private var ongoingTotalBytes = 0L

    private val modelDir: File
        get() = File(context.filesDir, MODEL_DIR)

    private val modelFile: File
        get() = File(modelDir, MODEL_FILE)

    override fun progressFlow(): Flow<ModelDownloader.Progress> = callbackFlow {
        // Emit current state immediately
        trySend(_progressFlow.value)

        // Watch for updates until isComplete
        var lastEmitted = _progressFlow.value
        while (!lastEmitted.isComplete) {
            kotlinx.coroutines.delay(200)
            lastEmitted = _progressFlow.value
            trySend(lastEmitted)
        }

        awaitClose { /* no-op: state machine ends on isComplete */ }
    }

    private fun updateProgress(bytesDownloaded: Long, totalBytes: Long, isComplete: Boolean) {
        ongoingBytesDownloaded = bytesDownloaded
        ongoingTotalBytes = totalBytes
        _progressFlow.value = ModelDownloader.Progress(
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes,
            isComplete = isComplete,
        )
    }

    override suspend fun download(cdnUrl: String, sha256: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                // Ensure model directory exists
                if (!modelDir.exists()) {
                    modelDir.mkdirs()
                }

                // HEAD request to get Content-Length
                val headRequest = Request.Builder()
                    .url(cdnUrl)
                    .head()
                    .build()

                val headResponse = okHttpClient.newCall(headRequest).execute()
                if (!headResponse.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("HEAD request failed: ${headResponse.code}")
                    )
                }

                val totalBytes = headResponse.header("Content-Length")
                    ?.toLongOrNull()
                    ?: return@withContext Result.failure(
                        IllegalStateException("Missing Content-Length header")
                    )

                // Check for existing partial file (resume support)
                val existingBytes = if (modelFile.exists()) modelFile.length() else 0L

                // Build download request (with Range header if resuming)
                val downloadRequestBuilder = Request.Builder()
                    .url(cdnUrl)

                if (existingBytes > 0 && existingBytes < totalBytes) {
                    // Resume: request bytes from existingBytes onwards
                    downloadRequestBuilder.addHeader("Range", "bytes=$existingBytes-")
                } else {
                    // Fresh start: delete any existing partial file
                    if (modelFile.exists()) modelFile.delete()
                    updateProgress(0L, totalBytes, isComplete = false)
                }

                val downloadResponse = okHttpClient
                    .newCall(downloadRequestBuilder.build())
                    .execute()

                val responseCode = downloadResponse.code
                // 200 = fresh download, 206 = partial (resume)
                if (responseCode != 200 && responseCode != 206) {
                    downloadResponse.close()
                    return@withContext Result.failure(
                        IllegalStateException("Download request failed: $responseCode")
                    )
                }

                val contentLength = downloadResponse.header("Content-Length")
                    ?.toLongOrNull()
                    ?: totalBytes

                val startBytes = if (responseCode == 206) existingBytes else 0L

                // Open output stream in append mode for resume, else fresh
                val appendMode = responseCode == 206 && existingBytes > 0
                val outputStream = FileOutputStream(modelFile, appendMode)

                try {
                    downloadResponse.body?.byteStream()?.use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesWritten = startBytes
                        var read: Int

                        while (input.read(buffer).also { read = it } != -1) {
                            outputStream.write(buffer, 0, read)
                            bytesWritten += read
                            updateProgress(bytesWritten, totalBytes, isComplete = false)
                        }
                    }

                    outputStream.fd.sync()  // ensure flushed to disk
                } finally {
                    outputStream.close()
                }

                // Verify SHA-256
                val fileSha256 = computeSha256(modelFile)
                if (fileSha256.lowercase() != sha256.lowercase()) {
                    val corruptFile = modelFile
                    corruptFile.delete()
                    updateProgress(0L, 0L, isComplete = false)
                    return@withContext Result.failure(
                        SecurityException("SHA-256 mismatch: expected $sha256, got $fileSha256")
                    )
                }

                updateProgress(totalBytes, totalBytes, isComplete = true)
                Result.success(modelFile.absolutePath)

            } catch (e: Exception) {
                // Clean up corrupt file on any failure
                if (modelFile.exists()) modelFile.delete()
                updateProgress(0L, 0L, isComplete = false)
                Result.failure(e)
            }
        }

    override suspend fun isModelReady(sha256: String): Boolean =
        withContext(Dispatchers.IO) {
            if (!modelFile.exists()) return@withContext false
            try {
                val actualSha256 = computeSha256(modelFile)
                actualSha256.lowercase() == sha256.lowercase()
            } catch (_: Exception) {
                false
            }
        }

    override suspend fun deleteLocalModel() = withContext(Dispatchers.IO) {
        if (modelFile.exists()) {
            modelFile.delete()
        }
        _progressFlow.value = ModelDownloader.Progress(0, 0, isComplete = true)
    }

    /**
     * Compute SHA-256 hex string of a file.
     * File must exist and be readable.
     */
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(BUFFER_SIZE)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it as Byte) }
    }
}
