package com.aiyougame.companion.llm

import android.content.Context
import android.util.Log
import com.aiyougame.companion.engine.ModelDownloader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LlamaEngine implementation for local GGUF inference.
 * Hilt manages this as a Singleton; [LlamaEngineManager] uses Provider<LlamaEngineImpl>
 * to create multiple instances on demand (one per character).
 * Memory is freed via [LlamaEngineManager.release] and [LlamaEngineManager.releaseAll].
 */
@Singleton
class LlamaEngineImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelDownloader: ModelDownloader,
) : LlamaEngine, TokenCallback {

    companion object {
        private const val TAG = "LlamaEngineImpl"
        private const val CDN_URL = "https://cdn.aiyougame.com/models/gemma-4-E4B-it-Q4_0.gguf"
        // TODO: Fill with actual SHA-256 of the GGUF file before production.
        // Empty string disables SHA-256 validation (any existing file passes isModelReady).
        // Generate with: sha256sum gemma-4-E4B-it-Q4_0.gguf
        // SHA-256 of gemma-4-E4B-it-Q4_0.gguf (computed 2026-04-11)
        private const val MODEL_SHA256 = "7c6dec4f0480ab4109743ab7cf13c07c850a99a6907c0366fa1678cef377e8ca"
        private const val MAX_TOKENS = 256
        private const val POLL_MS = 50L
    }

    @Volatile private var nativePtr: Long = 0L
    @Volatile private var isInitialized: Boolean = false
    private var modelPath: String = ""
    private val tokenQueue = ConcurrentLinkedQueue<String>()
    @Volatile private var callbackDone = false
    @Volatile private var callbackError: String? = null
    private val accumulated = StringBuilder()
    private val mock: MockLlamaEngine = MockLlamaEngine()

    init {
        try {
            System.loadLibrary("llama_jni")
            Log.d(TAG, "Native library loaded")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Native unavailable: ${e.message}")
        }
    }

    override suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext Result.success(Unit)
        try {
            Log.d(TAG, "Checking model...")
            if (!modelDownloader.isModelReady(MODEL_SHA256)) {
                Log.d(TAG, "Downloading model...")
                modelDownloader.download(CDN_URL, MODEL_SHA256)
                    .onFailure { return@withContext Result.failure(it) }
            }
            modelPath = modelDownloader.getModelPath()
            Log.d(TAG, "Model path: $modelPath")
            if (nativePtr == 0L && nativeIsLoaded()) {
                nativePtr = nativeInitEngine(modelPath)
                if (nativePtr != 0L) {
                    Log.d(TAG, "Native engine init OK: ptr=$nativePtr")
                    val tpl = nativeGetChatTemplate(nativePtr)
                    Log.d(TAG, "Chat template: ${if (tpl.isEmpty()) "(empty)" else "found"}")
                }
            }
            if (nativePtr == 0L) Log.w(TAG, "Using mock engine")
            isInitialized = true
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Init failed", e)
            Result.failure(e)
        }
    }

    override fun generateResponse(userMessage: String, systemPrompt: String): Flow<String> {
        if (nativePtr != 0L && isInitialized) {
            return nativeGenerateFlow(userMessage, systemPrompt).flowOn(Dispatchers.IO)
        }
        return mock.generateResponse(userMessage, systemPrompt)
    }

    private fun nativeGenerateFlow(userMessage: String, systemPrompt: String): Flow<String> = callbackFlow {
        val template = if (nativePtr != 0L) nativeGetChatTemplate(nativePtr) else ""
        val prompt = if (template.isNotEmpty()) applyTemplate(template, systemPrompt, userMessage)
                     else "$systemPrompt\n\nUser: $userMessage\nAssistant:"
        tokenQueue.clear(); callbackDone = false; callbackError = null; accumulated.setLength(0)
        if (nativePtr != 0L) nativeGenerateStream(nativePtr, prompt, MAX_TOKENS, this@LlamaEngineImpl)
        while (!callbackDone && !isClosedForSend) {
            val tok = tokenQueue.poll()
            if (tok != null) { accumulated.append(tok); trySend(tok) }
            else delay(POLL_MS)
        }
        callbackError?.let { close(IllegalStateException(it)) }
        awaitClose { if (nativePtr != 0L && !callbackDone) nativeAbort(nativePtr) }
    }

    private fun applyTemplate(tpl: String, sys: String, user: String): String {
        return try {
            when {
                tpl.contains("Gemma") || tpl.contains("gemma") ->
                    "<start_of_turn>model\n$sys\n$user<end_of_turn>\n<start_of_turn>model\n"
                tpl.contains("messages") -> tpl
                    .replace("{{ messages[0].role }}","system").replace("{{ messages[0].content }}",sys)
                    .replace("{{ messages[1].role }}","user").replace("{{ messages[1].content }}",user)
                    .replace("{{ BosToken }}","").replace("{{ eos_token }}","")
                    .replace("{% for message in messages %}","")
                    .replace("{% endfor %}","")
                    .replace("{{ message.content }}","")
                else -> "$sys\n\nUser: $user\nAssistant:"
            }
        } catch (e: Exception) { Log.w(TAG,"tpl apply failed",e); "$sys\n\nUser: $user\nAssistant:" }
    }

    override fun release() {
        if (nativePtr != 0L) { nativeAbort(nativePtr); nativeFree(nativePtr); nativePtr = 0L; Log.d(TAG,"released") }
        isInitialized = false
    }

    override fun onToken(token: String) { tokenQueue.add(token) }
    override fun onDone(fullText: String) { callbackDone = true }
    override fun onError(error: String) { callbackError = error; callbackDone = true }

    private fun nativeIsLoaded(): Boolean = try { System.loadLibrary("llama_jni"); true } catch (e: UnsatisfiedLinkError) { false }

    // JNI (implemented in llama_jni.cpp)
    private external fun nativeInitEngine(modelPath: String): Long
    private external fun nativeFree(ptr: Long)
    private external fun nativeGetChatTemplate(ptr: Long): String
    private external fun nativeGenerateStream(ptr: Long, prompt: String, maxTokens: Int, callback: TokenCallback)
    private external fun nativeAbort(ptr: Long)
}
