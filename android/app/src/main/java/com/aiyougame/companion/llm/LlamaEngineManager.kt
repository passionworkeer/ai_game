package com.aiyougame.companion.llm

import android.content.Context
import android.util.Log
import com.aiyougame.companion.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pool of LlamaEngine instances, one per character.
 *
 * Memory constraint: maximum 2 engine instances at any time.
 * When a 3rd character is requested, the least-recently-used engine is released.
 *
 * Engine selection (based on [BuildConfig.OLLAMA_ENABLED]):
 * - true  → [OllamaEngineImpl] (HTTP calls to host Windows Ollama)
 * - false → [LlamaEngineImpl]  (native JNI llama.cpp)
 *
 * Both engine implementations are singletons — they manage per-character state internally.
 * Falls back to [MockLlamaEngine] if the selected engine fails to initialize.
 * All operations are thread-safe via Mutex.
 */
@Singleton
class LlamaEngineManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val nativeEngine: LlamaEngineImpl,
    private val ollamaEngine: OllamaEngineImpl,
) {
    companion object {
        private const val TAG = "LlamaEngineManager"
        private const val MAX_ENGINES = 2
    }

    /** Map from characterCode -> engine instance */
    private val engines = ConcurrentHashMap<String, LlamaEngine>()

    /** Map from characterCode -> last access timestamp (for LRU) */
    private val accessOrder = ConcurrentHashMap<String, Long>()

    private val mutex = Mutex()

    /** Returns the active engine label for logging */
    val engineLabel: String
        get() = if (BuildConfig.OLLAMA_ENABLED) "OllamaEngine" else "NativeEngine"

    /** Returns the currently active base engine based on build config */
    private val activeEngine: LlamaEngine
        get() = if (BuildConfig.OLLAMA_ENABLED) ollamaEngine else nativeEngine

    /**
     * Get or create an engine for the given character.
     * Both engines are singletons; this returns the active engine keyed by characterCode.
     * The engine manages its own internal character state (chat history, memory).
     *
     * If MAX_ENGINES is exceeded, releases the least-recently-used entry first.
     * Falls back to [MockLlamaEngine] if initialization fails.
     */
    suspend fun getEngine(characterCode: String): LlamaEngine = mutex.withLock {
        engines[characterCode]?.also {
            accessOrder[characterCode] = System.currentTimeMillis()
        } ?: run {
            // Evict LRU if at capacity
            if (engines.size >= MAX_ENGINES) {
                val lruCode = findLruCharacter()
                if (lruCode != null && lruCode != characterCode) {
                    releaseEngine(lruCode)
                    Log.d(TAG, "Evicted LRU for character: $lruCode")
                }
            }

            val engine = activeEngine
            Log.d(TAG, "Using [$engineLabel] for character: $characterCode")

            // Try to initialize (both engines are singletons, may be no-op if already initialized)
            val initResult = engine.initialize()
            if (initResult.isFailure) {
                val err = initResult.exceptionOrNull()?.message ?: "unknown"
                Log.e(TAG, "Failed to initialize $engineLabel: $err")
                // Fall back to mock — still better than crashing
                val mock = MockLlamaEngine()
                mock.initialize()
                engines[characterCode] = mock
                accessOrder[characterCode] = System.currentTimeMillis()
                return@run mock
            }

            engines[characterCode] = engine
            accessOrder[characterCode] = System.currentTimeMillis()
            Log.d(TAG, "[$engineLabel] ready for character: $characterCode (total: ${engines.size})")
            engine
        }
    }

    /**
     * Release the engine for a specific character.
     * Note: since both LlamaEngineImpl and OllamaEngineImpl are singletons,
     * we only clear the tracking map — the engine itself stays alive.
     */
    fun releaseEngine(characterCode: String) {
        engines.remove(characterCode)?.let {
            accessOrder.remove(characterCode)
            Log.d(TAG, "Released tracking for character: $characterCode")
        }
    }

    /**
     * Release all tracked engines and clear the pool.
     */
    fun releaseAll() {
        engines.clear()
        accessOrder.clear()
        Log.d(TAG, "Engine pool cleared (active engine kept alive as singleton)")
    }

    fun getLoadedEngine(characterCode: String): LlamaEngine? = engines[characterCode]
    fun loadedCount(): Int = engines.size

    private fun findLruCharacter(): String? = accessOrder.minByOrNull { it.value }?.key
}
