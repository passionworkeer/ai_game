package com.aiyougame.companion.speech

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whisper-based speech recognition engine.
 *
 * Phase 2 stub: returns failure until Whisper JNI is integrated.
 *
 * Whisper integration will use whisper.cpp compiled as a native library.
 * Audio format: 16kHz, 16-bit PCM mono WAV.
 *
 * Privacy: all inference is LOCAL, zero network requests.
 */
@Singleton
class WhisperEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : SpeechToTextEngine {

    companion object {
        private const val TAG = "WhisperEngine"
    }

    private var nativePtr: Long = 0L
    private var isModelLoaded = false

    init {
        try {
            System.loadLibrary("whisper_jni")
            Log.d(TAG, "Whisper native library loaded")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Whisper JNI not available: ${e.message}")
        }
    }

    override suspend fun transcribe(audioFilePath: String): Result<String> = withContext(Dispatchers.IO) {
        if (nativePtr == 0L) {
            Log.w(TAG, "Whisper not initialized, returning stub result")
            return@withContext Result.failure(
                NotYetIntegratedException("Whisper JNI not yet integrated")
            )
        }

        try {
            val result = nativeTranscribe(nativePtr, audioFilePath)
            if (result.isNotBlank()) {
                Log.d(TAG, "Transcription: $result")
                Result.success(result)
            } else {
                Result.failure(Exception("Empty transcription result"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed", e)
            Result.failure(e)
        }
    }

    override fun isReady(): Boolean = nativePtr != 0L && isModelLoaded

    /**
     * Load the Whisper model (called during app initialization).
     */
    suspend fun loadModel(): Result<Unit> = withContext(Dispatchers.IO) {
        if (nativePtr != 0L) return@withContext Result.success(Unit)

        try {
            // Model path: files/models/whisper-tiny-en.bin
            val modelPath = "${context.filesDir}/models/whisper-tiny-en.bin"
            nativePtr = nativeInitWhisper(modelPath)
            if (nativePtr != 0L) {
                isModelLoaded = true
                Log.d(TAG, "Whisper model loaded: $modelPath")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to initialize Whisper model"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Whisper model", e)
            Result.failure(e)
        }
    }

    /**
     * Release Whisper resources.
     */
    fun release() {
        if (nativePtr != 0L) {
            nativeFreeWhisper(nativePtr)
            nativePtr = 0L
            isModelLoaded = false
            Log.d(TAG, "Whisper released")
        }
    }

    // JNI declarations (implemented in whisper_jni.cpp)
    private external fun nativeInitWhisper(modelPath: String): Long
    private external fun nativeTranscribe(ptr: Long, audioPath: String): String
    private external fun nativeFreeWhisper(ptr: Long)
}

class NotYetIntegratedException(message: String) : Exception(message)
