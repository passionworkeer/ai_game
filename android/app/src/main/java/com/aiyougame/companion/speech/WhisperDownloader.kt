package com.aiyougame.companion.speech

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Downloads the Whisper GGML model to local storage.
 *
 * Uses the tiny.en model (~75MB) for fast, lightweight speech recognition.
 * CDN: HuggingFace ggml-org/whisper models
 *
 * Privacy: all downloads are model weights only, no personal data.
 */
@Singleton
class WhisperDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("download") private val okHttpClient: OkHttpClient,
) {
    companion object {
        private const val TAG = "WhisperDownloader"
        private const val MODEL_DIR = "models"
        // whisper.cpp tiny.en — Q5_1_K_M is compact but accurate
        private const val CDN_URL =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin"
        // TODO: Fill with actual SHA-256 before production.
        // Empty string disables SHA-256 validation.
        private const val MODEL_SHA256 = ""
        private const val BUFFER_SIZE = 8192
    }

    private val modelDir: File
        get() = File(context.filesDir, MODEL_DIR)

    private val modelFile: File
        get() = File(modelDir, "whisper-tiny-en.bin")

    private val _progress = MutableStateFlow(Progress(0, 0, isComplete = false))
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    data class Progress(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val isComplete: Boolean,
    )

    /**
     * Check if the model is already present and valid.
     * When MODEL_SHA256 is empty, any existing file passes the check.
     */
    suspend fun isModelReady(): Boolean = withContext(Dispatchers.IO) {
        if (!modelFile.exists()) return@withContext false
        if (MODEL_SHA256.isEmpty()) return@withContext true
        try {
            val sha = computeSha256(modelFile)
            sha.lowercase() == MODEL_SHA256.lowercase()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Download the Whisper model if not already present.
     * Supports resume via Range header on partial downloads.
     *
     * @return absolute path to the model file, or failure
     */
    suspend fun download(): Result<String> = withContext(Dispatchers.IO) {
        if (!modelDir.exists()) modelDir.mkdirs()

        // Check if already ready
        if (isModelReady()) {
            _progress.value = Progress(modelFile.length(), modelFile.length(), true)
            return@withContext Result.success(modelFile.absolutePath)
        }

        try {
            // HEAD request to get file size
            val headReq = Request.Builder().url(CDN_URL).head().build()
            val headResp = okHttpClient.newCall(headReq).execute()
            if (!headResp.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("CDN HEAD failed: ${headResp.code}")
                )
            }
            val totalBytes = headResp.header("Content-Length")?.toLongOrNull()
                ?: return@withContext Result.failure(
                    IllegalStateException("Missing Content-Length from CDN")
                )
            headResp.close()

            // Existing partial download?
            val existingBytes = if (modelFile.exists()) modelFile.length() else 0L

            val reqBuilder = Request.Builder().url(CDN_URL)
            if (existingBytes in 1 until totalBytes) {
                reqBuilder.addHeader("Range", "bytes=$existingBytes-")
            } else if (modelFile.exists()) {
                modelFile.delete()
            }
            _progress.value = Progress(if (existingBytes > 0 && modelFile.exists()) existingBytes else 0L, totalBytes, false)

            val downloadResp = okHttpClient.newCall(reqBuilder.build()).execute()
            val code = downloadResp.code
            if (code != 200 && code != 206) {
                downloadResp.close()
                return@withContext Result.failure(
                    IllegalStateException("Download failed: HTTP $code")
                )
            }

            val appendMode = code == 206 && existingBytes > 0
            val outputStream = FileOutputStream(modelFile, appendMode)

            try {
                downloadResp.body?.byteStream()?.use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var written = if (appendMode) existingBytes else 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                        written += read
                        _progress.value = Progress(written, totalBytes, false)
                    }
                }
                outputStream.fd.sync()
            } finally {
                outputStream.close()
            }

            _progress.value = Progress(totalBytes, totalBytes, true)
            Result.success(modelFile.absolutePath)

        } catch (e: Exception) {
            if (modelFile.exists()) modelFile.delete()
            _progress.value = Progress(0, 0, false)
            Result.failure(e)
        }
    }

    /** Path to the downloaded model file. */
    fun getModelPath(): String = modelFile.absolutePath

    private fun computeSha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buf = ByteArray(BUFFER_SIZE)
            var read: Int
            while (fis.read(buf).also { read = it } != -1) {
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it as Byte) }
    }
}
