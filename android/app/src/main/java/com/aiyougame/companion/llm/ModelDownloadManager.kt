package com.aiyougame.companion.llm

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
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Downloads the GGUF model from CDN on first launch.
 *
 * State machine:
 * ```
 * Idle → Checking → Downloading* → Verifying → Ready
 *                   ↘ Error ← ─ ─ ─ ─ ─ ─ ─ ─ ┘
 * ```
 *
 * Phase 1 intentionally skips SHA-256 verification (TODO in Phase 2).
 *
 * @param cdnUrl         Full CDN URL for the model
 * @param modelFileName Local file name within the models directory
 * @param context        Application context — used to resolve filesDir
 * @param okHttpClient  Bare OkHttpClient from [com.aiyougame.companion.data.network.NetworkModule.provideDownloadOkHttpClient]
 */
@Singleton
class ModelDownloadManager @Inject constructor(
    private val cdnUrl: String,
    private val modelFileName: String,
    @ApplicationContext private val context: Context,
    @Named("download") private val okHttpClient: OkHttpClient,
) {

    companion object {
        const val MODEL_FILE_NAME = "gemma-4-E4B-it-Q4_0.gguf"
        private const val MODEL_DIR = "models"
    }

    private val _stateFlow = MutableStateFlow<DownloadState>(DownloadState.Idle)
    /** UI observes this flow to render download progress / errors. */
    val stateFlow: StateFlow<DownloadState> = _stateFlow.asStateFlow()

    @Volatile
    private var _modelFile: File? = null
    /** Absolute path to the model file, set after [ensureModel] succeeds. Null until then. */
    val modelFile: File? get() = _modelFile

    private val modelDir: File get() = File(context.filesDir, MODEL_DIR)
    private val modelPath: File get() = File(modelDir, modelFileName)

    /**
     * Main entry point.
     *
     * State machine:
     * - [DownloadState.Checking]    — verifying the local file exists
     * - [DownloadState.Downloading]  — actively streaming from CDN with progress
     * - [DownloadState.Verifying]    — integrity check after download (Phase 1: no-op)
     * - [DownloadState.Ready]        — model file is available at [modelFile]
     * - [DownloadState.Error]        — any failure; [message] describes the cause
     *
     * All file / network IO runs on [Dispatchers.IO].
     *
     * @return [Result.success] carrying the [File] at [modelFile].
     */
    suspend fun ensureModel(): Result<File> = withContext(Dispatchers.IO) {
        // ── Step 1: check local file ──────────────────────────────────────────
        _stateFlow.value = DownloadState.Checking

        if (modelPath.exists()) {
            _modelFile = modelPath
            _stateFlow.value = DownloadState.Ready
            return@withContext Result.success(modelPath)
        }

        // ── Step 2: download from CDN ────────────────────────────────────────
        modelDir.mkdirs()
        _stateFlow.value = DownloadState.Downloading(0, "")

        return@withContext try {
            val request = Request.Builder().url(cdnUrl).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                _stateFlow.value = DownloadState.Error("HTTP ${response.code}")
                return@withContext Result.failure(
                    Exception("Download failed: ${response.code}")
                )
            }

            val body = response.body ?: throw Exception("Empty response body")
            val totalBytes = body.contentLength()
            var downloaded = 0L

            File(modelPath, ".tmp").outputStream().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytes: Int
                    while (input.read(buffer).also { bytes = it } != -1) {
                        output.write(buffer, 0, bytes)
                        downloaded += bytes
                        if (totalBytes > 0) {
                            val pct = ((downloaded * 100) / totalBytes).toInt()
                            // Phase 2: wire in a real throughput calculator
                            _stateFlow.value = DownloadState.Downloading(pct, "")
                        }
                    }
                }
            }

            File(modelPath, ".tmp").renameTo(modelPath)

            // TODO Phase 2: SHA-256 verification before marking Ready
            _stateFlow.value = DownloadState.Verifying
            _modelFile = modelPath
            _stateFlow.value = DownloadState.Ready
            Result.success(modelPath)

        } catch (e: Exception) {
            _stateFlow.value = DownloadState.Error(e.message ?: "Download failed")
            Result.failure(e)
        }
    }
}

/**
 * Download state machine exposed to the UI layer.
 *
 * Variants:
 * - [Idle]              — no download activity yet
 * - [Checking]          — verifying local model file exists
 * - [Downloading]        — actively downloading; [progress] is 0–100, [bytesPerSec] is throughput
 * - [Verifying]         — integrity / schema check after download (Phase 1: near-instant)
 * - [Ready]             — [ModelDownloadManager.modelFile] points to the valid GGUF
 * - [Error]             — download or verification failed; [message] describes the cause
 */
sealed class DownloadState {
    data object Idle : DownloadState()
    data object Checking : DownloadState()
    data class Downloading(
        val progress: Int,       // 0–100
        val bytesPerSec: String, // e.g. "1.2 MB/s"; empty string if unknown
    ) : DownloadState()
    data object Verifying : DownloadState()
    data object Ready : DownloadState()
    data class Error(val message: String) : DownloadState()
}
