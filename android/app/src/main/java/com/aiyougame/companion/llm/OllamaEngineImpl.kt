package com.aiyougame.companion.llm

import android.util.Log
import com.aiyougame.companion.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Ollama HTTP API engine for local inference.
 *
 * Use case:
 * - Android emulator → host Windows Ollama at http://10.0.2.2:11434
 * - Real device on same LAN → http://<PC_IP>:11434
 *
 * Architecture:
 * - initialize() pings /api/tags to verify Ollama is reachable
 * - generateResponse() calls /api/chat with streaming, parses SSE, emits tokens
 * - generateResponseWithImage() is NOT implemented (Ollama multimodal requires
 *   server-side image encoding — falls back to text-only)
 *
 * This class is a drop-in replacement for [LlamaEngineImpl] — implements the
 * same [LlamaEngine] interface so [LlamaEngineManager] can use it interchangeably.
 */
@Singleton
class OllamaEngineImpl @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    @Named("ollama") private val okHttpClient: OkHttpClient,
) : LlamaEngine {

    companion object {
        private const val TAG = "OllamaEngine"
        private const val OLLAMA_URL = BuildConfig.OLLAMA_URL
        private const val OLLAMA_MODEL = BuildConfig.OLLAMA_MODEL
        private const val CHAT_ENDPOINT = "$OLLAMA_URL/api/chat"
        private const val TAGS_ENDPOINT = "$OLLAMA_URL/api/tags"
        private const val MAX_TOKENS = 512
        private const val MIME_SSE = "text/event-stream"
    }

    @Volatile private var isInitialized = false
    @Volatile private var lastError: String? = null

    override suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext Result.success(Unit)
        try {
            Log.d(TAG, "Checking Ollama at $OLLAMA_URL...")
            val request = Request.Builder()
                .url(TAGS_ENDPOINT)
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val msg = "Ollama returned HTTP ${response.code}"
                    lastError = msg
                    Log.e(TAG, msg)
                    return@withContext Result.failure(IOException(msg))
                }
                val body = response.body?.string() ?: ""
                Log.d(TAG, "Ollama models available: ${body.take(200)}")
            }
            isInitialized = true
            Log.d(TAG, "OllamaEngine initialized. Model: $OLLAMA_MODEL")
            Result.success(Unit)
        } catch (e: Exception) {
            val msg = "Ollama unreachable: ${e.message}"
            lastError = msg
            Log.e(TAG, msg, e)
            Result.failure(IOException(msg, e))
        }
    }

    override fun generateResponse(userMessage: String, systemPrompt: String): Flow<String> = callbackFlow {
        if (!isInitialized) {
            close(IllegalStateException("OllamaEngine not initialized. Call initialize() first."))
            return@callbackFlow
        }

        Log.d(TAG, "generateResponse: userMessage=${userMessage.take(50)}")

        val body = JSONObject().apply {
            put("model", OLLAMA_MODEL)
            put("stream", true)
            put("think", false)
            put("options", JSONObject().apply {
                put("num_predict", MAX_TOKENS)
            })
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userMessage)
                })
            })
        }

        val requestBody = body.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(CHAT_ENDPOINT)
            .post(requestBody)
            .header("Content-Type", "application/json")
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val msg = "Ollama HTTP ${response.code}"
                    Log.e(TAG, msg)
                    close(IOException(msg))
                    return@callbackFlow
                }

                val contentType = response.header("Content-Type") ?: ""
                if (!contentType.contains("text/event-stream") &&
                    !contentType.contains("application/x-ndjson")) {
                    // Fallback: try to read as plain JSON (non-streaming response)
                    val rawBody = response.body?.string() ?: ""
                    Log.w(TAG, "Non-SSE response: ${rawBody.take(100)}")
                    val content = extractContentFromNonStreaming(rawBody)
                    if (content.isNotEmpty()) {
                        for (char in content) { trySend(char.toString()) }
                    }
                    close()
                    return@callbackFlow
                }

                val stream = response.body?.byteStream()
                    ?: throw IOException("Empty response body")

                val buffer = StringBuilder()
                stream.bufferedReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val trimmed = line?.trim() ?: continue
                        if (!trimmed.startsWith("data:")) continue

                        val data = trimmed.removePrefix("data:").trim()
                        if (data.isEmpty() || data == "[DONE]") continue

                        // Parse SSE data line: each is a JSON object with message.content
                        try {
                            val json = JSONObject(data)
                            if (json.has("message")) {
                                val content = json.getJSONObject("message").optString("content", "")
                                if (content.isNotEmpty()) {
                                    // Send character by character for typewriter effect
                                    for (char in content) {
                                        trySend(char.toString())
                                    }
                                }
                            }
                            // Check done
                            if (json.optBoolean("done", false)) {
                                Log.d(TAG, "Stream complete")
                                break
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "SSE parse error: ${e.message} on: $data")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "generateResponse error: ${e.message}", e)
            close(e)
        }

        awaitClose { Log.d(TAG, "generateResponse flow closed") }
    }.flowOn(Dispatchers.IO)

    /**
     * Extract content from a non-streaming Ollama JSON response.
     * Fallback when server doesn't return SSE.
     */
    private fun extractContentFromNonStreaming(raw: String): String {
        return try {
            val json = JSONObject(raw)
            json.getJSONObject("message").optString("content", "")
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Multimodal: not supported via Ollama text endpoint.
     * Image inputs would need server-side preprocessing.
     * Falls back to text-only.
     */
    override fun generateResponseWithImage(
        userMessage: String,
        systemPrompt: String,
        rgbImage: ByteArray,
        width: Int,
        height: Int,
    ): Flow<String> {
        Log.w(TAG, "generateResponseWithImage: multimodal not supported via Ollama, falling back to text")
        return generateResponse(userMessage, systemPrompt)
    }

    override fun release() {
        isInitialized = false
        Log.d(TAG, "OllamaEngine released")
    }

    private val lastErrorMessage: String?
        get() = lastError
}
