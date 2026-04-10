package com.aiyougame.companion.llm

import android.content.Context
import android.util.Log
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
 * Uses Provider<LlamaEngineImpl> to create new instances via Hilt injection.
 * All operations are thread-safe via Mutex.
 */
@Singleton
class LlamaEngineManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engineFactory: javax.inject.Provider<LlamaEngineImpl>,
) {
    companion object {
        private const val TAG = "LlamaEngineManager"
        private const val MAX_ENGINES = 2
    }

    /** Map from characterCode -> engine instance */
    private val engines = ConcurrentHashMap<String, LlamaEngineImpl>()

    /** Map from characterCode -> last access timestamp (for LRU) */
    private val accessOrder = ConcurrentHashMap<String, Long>()

    private val mutex = Mutex()

    /**
     * Get or create an engine for the given character.
     * If MAX_ENGINES is exceeded, releases the least-recently-used engine first.
     */
    suspend fun getEngine(characterCode: String): LlamaEngineImpl {
        // Fast path: engine already exists
        engines[characterCode]?.let { engine ->
            accessOrder[characterCode] = System.currentTimeMillis()
            return engine
        }

        return mutex.withLock {
            // Double-check after acquiring lock
            engines[characterCode]?.let { engine ->
                accessOrder[characterCode] = System.currentTimeMillis()
                return engine
            }

            // Evict LRU if at capacity
            if (engines.size >= MAX_ENGINES) {
                val lruCode = findLruCharacter()
                if (lruCode != null && lruCode != characterCode) {
                    releaseEngine(lruCode)
                    Log.d(TAG, "Evicted LRU engine for character: $lruCode")
                }
            }

            // Create new engine via Hilt Provider
            val engine = engineFactory.get()
            val initResult = engine.initialize()
            if (initResult.isFailure) {
                Log.e(TAG, "Failed to initialize engine for $characterCode: ${initResult.exceptionOrNull()?.message}")
            }

            engines[characterCode] = engine
            accessOrder[characterCode] = System.currentTimeMillis()
            Log.d(TAG, "Created new engine for character: $characterCode (total: ${engines.size})")
            engine
        }
    }

    /**
     * Release the engine for a specific character.
     */
    fun releaseEngine(characterCode: String) {
        engines.remove(characterCode)?.let { engine ->
            engine.release()
            accessOrder.remove(characterCode)
            Log.d(TAG, "Released engine for character: $characterCode")
        }
    }

    /**
     * Release all engines. Called when memory is low.
     */
    fun releaseAll() {
        val codes = engines.keys().asSequence().toList()
        for ((code, engine) in engines) {
            engine.release()
            Log.d(TAG, "Released engine for character: $code")
        }
        engines.clear()
        accessOrder.clear()
        Log.d(TAG, "All engines released (was tracking: $codes)")
    }

    /**
     * Get the currently loaded engine for a character, or null if not loaded.
     */
    fun getLoadedEngine(characterCode: String): LlamaEngineImpl? = engines[characterCode]

    /**
     * Returns the number of currently loaded engines.
     */
    fun loadedCount(): Int = engines.size

    private fun findLruCharacter(): String? {
        return accessOrder.minByOrNull { it.value }?.key
    }
}
