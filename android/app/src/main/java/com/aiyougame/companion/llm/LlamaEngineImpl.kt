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

/**
 * LlamaEngine implementation for local GGUF inference.
 *
 * IMPORTANT: This class is NOT a Singleton — [LlamaEngineManager] uses Provider<LlamaEngineImpl>
 * to manufacture a fresh instance per character via Hilt's Provider<T>.get().
 * The @Singleton annotation on the class is informational only; Hilt's Provider pattern
 * bypasses singleton semantics and always returns a new object.
 *
 * Memory is freed via [LlamaEngineManager.release] and [LlamaEngineManager.releaseAll].
 */
class LlamaEngineImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelDownloader: ModelDownloader,
) : LlamaEngine, TokenCallback {

    companion object {
        private const val TAG = "LlamaEngineImpl"
        // Text model (GGUF)
        private const val MODEL_FILE = "Gemma-4-E2B-Uncensored-HauhauCS-Aggressive-Q4_K_P.gguf"
        private const val MODEL_URL = "https://cdn.aiyougame.com/models/$MODEL_FILE"
        private const val MODEL_SHA256 = "aa866c1e514468f3d0f33971679d63c11b7c9c47acddd1cc5785fc467e52c21d"

        // Multimodal projector (GGUF) for vision
        private const val MMPROJ_FILE = "mmproj-Gemma-4-E2B-Uncensored-HauhauCS-Aggressive-f16.gguf"
        private const val MMPROJ_URL = "https://cdn.aiyougame.com/models/$MMPROJ_FILE"
        private const val MMPROJ_SHA256 = "628b7e999f89beef70b32396ae84f59c096e867747d7901f0134064ff672e290"
        private const val MAX_TOKENS = 256
        private const val POLL_MS = 50L
    }

    @Volatile private var nativePtr: Long = 0L
    @Volatile private var isInitialized: Boolean = false
    private var modelPath: String = ""
    private var mmprojPath: String = ""
    private val tokenQueue = ConcurrentLinkedQueue<String>()
    @Volatile private var callbackDone = false
    @Volatile private var callbackError: String? = null
    private val accumulated = StringBuilder()
    private val mock: MockLlamaEngine = MockLlamaEngine()

    init {
        // NOTE: Native library loading is intentionally NOT done here.
        // Loading in init{} makes exceptions uncatchable when Hilt constructs this singleton,
        // causing SIGABRT on emulators with ABI mismatches or JNI signature errors.
        // Lazy loading (on first generateResponse call) lets us catch and fall back to mock.
        Log.d(TAG, "LlamaEngineImpl created (native library will load lazily)")
    }

    /** Loads the native JNI library once, throws on failure so callers can fall back to mock. */
    @Synchronized
    private fun ensureNativeLoaded(): Long {
        if (nativePtr != 0L) return nativePtr
        System.loadLibrary("llama_jni")
        nativePtr = nativeInitEngine(modelPath, mmprojPath)
        return nativePtr
    }

    override suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext Result.success(Unit)
        try {
            Log.d(TAG, "Checking model + mmproj...")

            if (!modelDownloader.isFileReady(MODEL_FILE, MODEL_SHA256)) {
                Log.d(TAG, "Downloading model...")
                modelDownloader.download(MODEL_URL, MODEL_SHA256, MODEL_FILE)
                    .onFailure { return@withContext Result.failure(it) }
            }
            if (!modelDownloader.isFileReady(MMPROJ_FILE, MMPROJ_SHA256)) {
                Log.d(TAG, "Downloading mmproj...")
                modelDownloader.download(MMPROJ_URL, MMPROJ_SHA256, MMPROJ_FILE)
                    .onFailure { return@withContext Result.failure(it) }
            }

            modelPath = modelDownloader.getFilePath(MODEL_FILE)
            mmprojPath = modelDownloader.getFilePath(MMPROJ_FILE)
            Log.d(TAG, "Model path: $modelPath")
            Log.d(TAG, "MMProj path: $mmprojPath")
            if (nativePtr == 0L && nativeIsLoaded()) {
                nativePtr = nativeInitEngine(modelPath, mmprojPath)
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

    override fun generateResponseWithImage(
        userMessage: String,
        systemPrompt: String,
        rgbImage: ByteArray,
        width: Int,
        height: Int,
    ): Flow<String> {
        if (nativePtr != 0L && isInitialized) {
            return nativeGenerateFlowWithImage(userMessage, systemPrompt, rgbImage, width, height)
                .flowOn(Dispatchers.IO)
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

    private fun nativeGenerateFlowWithImage(
        userMessage: String,
        systemPrompt: String,
        rgbImage: ByteArray,
        width: Int,
        height: Int,
    ): Flow<String> = callbackFlow {
        val template = if (nativePtr != 0L) nativeGetChatTemplate(nativePtr) else ""
        val promptText = if (template.isNotEmpty()) applyTemplate(template, systemPrompt, userMessage)
        else "$systemPrompt\n\nUser: $userMessage\nAssistant:"
        val prompt = "<__media__>\n$promptText"

        tokenQueue.clear(); callbackDone = false; callbackError = null; accumulated.setLength(0)
        if (nativePtr != 0L) nativeGenerateStreamWithMedia(nativePtr, prompt, rgbImage, width, height, MAX_TOKENS, this@LlamaEngineImpl)
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
    private external fun nativeInitEngine(modelPath: String, mmprojPath: String): Long
    private external fun nativeFree(ptr: Long)
    private external fun nativeGetChatTemplate(ptr: Long): String
    private external fun nativeGenerateStream(ptr: Long, prompt: String, maxTokens: Int, callback: TokenCallback)
    private external fun nativeGenerateStreamWithMedia(ptr: Long, prompt: String, rgb: ByteArray, width: Int, height: Int, maxTokens: Int, callback: TokenCallback)
    private external fun nativeAbort(ptr: Long)
}
