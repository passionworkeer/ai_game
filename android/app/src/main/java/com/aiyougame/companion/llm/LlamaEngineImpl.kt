package com.aiyougame.companion.llm

import android.content.Context
import android.util.Log
import com.aiyougame.companion.engine.ModelDownloader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 2 LlamaEngine implementation — delegates to JNI native engine.
 *
 * Falls back to MockLlamaEngine when native library is not available.
 * State machine:
 *   Idle → Initializing → Ready → Released
 *                ↘ Error ← ─ ┘
 */
@Singleton
class LlamaEngineImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelDownloader: ModelDownloader,
) : LlamaEngine {

    companion object {
        private const val TAG = "LlamaEngineImpl"
        private const val CDN_URL = "https://cdn.aiyougame.com/models/gemma-4-E4B-it-Q4_0.gguf"
        // TODO (Phase 2): real SHA-256 once model CDN is configured
        private const val MODEL_SHA256 = ""

        // NDK ABI to use (arm64-v8a = modern Android devices)
        private const val PREFERRED_ABI = "arm64-v8a"
    }

    // Tracks whether native engine is loaded
    @Volatile
    private var nativePtr: Long = 0L

    @Volatile
    private var isInitialized: Boolean = false

    // Fallback mock for when native library is unavailable
    private val mock: MockLlamaEngine = MockLlamaEngine()

    init {
        // Try to load the native library; if it fails, nativePtr stays 0
        try {
            System.loadLibrary("llama_jni")
            Log.d(TAG, "Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Native library not available — using mock engine: ${e.message}")
        }
    }

    override suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext Result.success(Unit)

        try {
            // Ensure model is downloaded
            Log.d(TAG, "Checking model availability...")
            val modelReady = modelDownloader.isModelReady(MODEL_SHA256)
            if (!modelReady) {
                Log.d(TAG, "Model not ready, downloading...")
                modelDownloader.download(CDN_URL, MODEL_SHA256)
                    .onFailure { return@withContext Result.failure(it) }
            }

            // Initialize native engine if library loaded
            if (nativePtr == 0L) {
                // No native library — use mock
                Log.w(TAG, "Using mock engine (native library unavailable)")
                isInitialized = true
                return@withContext Result.success(Unit)
            }

            // nativeInitEngine is declared as external fun in the JNI layer
            // nativePtr = nativeInitEngine(modelPath, nCtx, nThreads)
            isInitialized = true
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed", e)
            Result.failure(e)
        }
    }

    override fun generateResponse(userMessage: String, systemPrompt: String): Flow<String> {
        return if (nativePtr != 0L && isInitialized) {
            // Real inference path (Phase 2 — requires NDK)
            // return nativeGenerateStream(userMessage, systemPrompt)
            flow { emit("【Phase 2: native inference pending — NDK required】") }
                .flowOn(Dispatchers.IO)
        } else {
            // Mock inference path
            mock.generateResponse(userMessage, systemPrompt)
        }
    }

    override fun release() {
        if (nativePtr != 0L) {
            // nativeFree(nativePtr)
            nativePtr = 0L
            Log.d(TAG, "Native engine released")
        }
        isInitialized = false
    }

    // ──────────────────────────────────────────────────────────────
    // JNI declarations — implemented in llama_jni.cpp
    // ──────────────────────────────────────────────────────────────
    // private external fun nativeInitEngine(modelPath: String, nCtx: Int, nThreads: Int): Long
    // private external fun nativeGenerateStream(userMessage: String, systemPrompt: String): Flow<String>
    // private external fun nativeFree(ptr: Long)
    // private external fun nativeGetChatTemplate(modelPath: String): String
}
