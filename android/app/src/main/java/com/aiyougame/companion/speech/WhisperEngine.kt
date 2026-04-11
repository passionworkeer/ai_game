package com.aiyougame.companion.speech

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whisper-based speech recognition engine using whisper.cpp native library.
 *
 * Audio format: 16kHz, 16-bit PCM mono WAV (written by AudioRecorder).
 * The tiny.en model (~75MB) is downloaded on first use from HuggingFace CDN.
 *
 * Privacy: all inference is LOCAL, zero network requests.
 */
@Singleton
class WhisperEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloader: WhisperDownloader,
) {

    companion object {
        private const val TAG = "WhisperEngine"
    }

    private var nativePtr: Long = 0L
    private var isModelLoaded = false

    init {
        try {
            System.loadLibrary("whisper_jni")
            Log.d(TAG, "Native library loaded")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Native unavailable: ${e.message}")
        }
    }

    /**
     * Initialize Whisper: download model + load native context.
     * Safe to call multiple times (idempotent).
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        if (isModelLoaded && nativePtr != 0L) return@withContext Result.success(Unit)

        // Download model if needed
        if (!downloader.isModelReady()) {
            Log.d(TAG, "Downloading Whisper model...")
            val dlResult = downloader.download()
            if (dlResult.isFailure) {
                val err = dlResult.exceptionOrNull()?.message ?: "unknown"
                Log.e(TAG, "Model download failed: $err")
                return@withContext Result.failure(
                    IllegalStateException("Whisper model download failed: $err")
                )
            }
        }

        val modelPath = downloader.getModelPath()
        Log.d(TAG, "Model path: $modelPath")

        val ptr = nativeInitWhisper(modelPath)
        if (ptr != 0L) {
            nativePtr = ptr
            isModelLoaded = true
            Log.d(TAG, "Whisper initialized: ptr=$ptr")
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Failed to initialize Whisper native context"))
        }
    }

    /**
     * Transcribe a 16kHz mono WAV file to text.
     * Must call [initialize] first.
     *
     * @param audioFilePath path to WAV file produced by AudioRecorder
     * @return transcription text, or failure
     */
    suspend fun transcribe(audioFilePath: String): Result<String> = withContext(Dispatchers.IO) {
        if (nativePtr == 0L) {
            // Try to initialize on demand
            val init = initialize()
            if (init.isFailure) {
                return@withContext Result.failure(
                    NotYetIntegratedException("Whisper not ready: ${init.exceptionOrNull()?.message}")
                )
            }
        }

        try {
            val result = nativeTranscribe(nativePtr, audioFilePath)
            if (result.isNotBlank()) {
                Log.d(TAG, "Transcribed: $result")
                Result.success(result)
            } else {
                Result.failure(Exception("Empty transcription"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed", e)
            Result.failure(e)
        }
    }

    /** True if the model is loaded and ready for transcription. */
    fun isReady(): Boolean = nativePtr != 0L && isModelLoaded

    /** Release native Whisper resources. */
    fun release() {
        if (nativePtr != 0L) {
            nativeFreeWhisper(nativePtr)
            nativePtr = 0L
            isModelLoaded = false
            Log.d(TAG, "Released")
        }
    }

    // ── JNI declarations (implemented in whisper_jni.cpp) ────────────────────
    private external fun nativeInitWhisper(modelPath: String): Long
    private external fun nativeTranscribe(ptr: Long, audioPath: String): String
    private external fun nativeFreeWhisper(ptr: Long)
}

class NotYetIntegratedException(message: String) : Exception(message)
