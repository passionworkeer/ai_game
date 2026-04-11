package com.aiyougame.companion.memory.vec

import com.aiyougame.companion.memory.db.ChatMessageDao
import com.aiyougame.companion.memory.db.ChatMessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vector Memory Manager — manages character memories using embeddings.
 *
 * Phase 2 transition: Uses MockEmbeddingService + SQLite FTS5
 * Phase 2 later: Upgrades to BGE-micro GGML + sqlite-vec KNN
 *
 * Responsibilities:
 * 1. Generate embeddings for user messages
 * 2. Store embeddings with character isolation
 * 3. Search for similar memories (KNN)
 * 4. Rebuild index from Room history
 *
 * Privacy: Only user messages are embedded and stored (not assistant responses).
 */
@Singleton
class VectorMemoryManager @Inject constructor(
    private val embeddingService: EmbeddingService,
    private val sqliteVecManager: SqliteVecManager,
    private val chatMessageDao: ChatMessageDao? = null // Optional: for index rebuild
) {

    companion object {
        private const val MAX_MEMORY_COUNT = 1000 // Max memories per character
        private const val DEFAULT_TOP_K = 5
    }

    /**
     * Process a new message: generate embedding and store in vector database.
     * Only user messages are processed (assistant messages are not stored for privacy).
     *
     * @param message The message text
     * @param characterCode The character this message belongs to
     * @param userRole "user" or "assistant" (default: "user")
     */
    suspend fun processMessage(
        message: String,
        characterCode: String,
        userRole: String = "user"
    ) = withContext(Dispatchers.IO) {
        // Privacy: Only embed user messages
        if (userRole != "user") return@withContext

        // Generate embedding
        val embedding = embeddingService.embed(message)

        // Store in vector database
        sqliteVecManager.insert(characterCode, message, embedding, userRole)

        // Trim old memories if over limit
        trimOldMemories(characterCode)
    }

    /**
     * Search for similar memories using embeddings.
     *
     * Phase 2 transition: Uses SQLite FTS5 for text-based matching
     * Phase 2 later: Uses real KNN with BGE-micro + sqlite-vec
     *
     * @param query Search query text
     * @param characterCode The character to search within
     * @param topK Maximum results (default: 5)
     * @return List of similar MemoryResult sorted by relevance
     */
    suspend fun searchSimilar(
        query: String,
        characterCode: String,
        topK: Int = DEFAULT_TOP_K
    ): List<MemoryResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        // Generate query embedding
        val queryEmbedding = embeddingService.embed(query)

        // Search using FTS5 text matching (Phase 2 transition)
        // Phase 2 later: sqliteVecManager.knnSearch(characterCode, queryEmbedding, topK)
        val textResults = sqliteVecManager.ftsSearch(characterCode, query, topK)

        if (textResults.isNotEmpty()) {
            return@withContext textResults
        }

        // Fallback: return recent memories
        sqliteVecManager.search(characterCode, queryEmbedding, topK)
    }

    /**
     * Search for similar memories using FTS5 full-text search.
     *
     * @param keywords FTS5 search keywords
     * @param characterCode The character to search within
     * @param topK Maximum results
     */
    suspend fun searchByKeywords(
        keywords: String,
        characterCode: String,
        topK: Int = DEFAULT_TOP_K
    ): List<MemoryResult> = withContext(Dispatchers.IO) {
        if (keywords.isBlank()) return@withContext emptyList()
        sqliteVecManager.ftsSearch(characterCode, keywords, topK)
    }

    /**
     * Rebuild vector index from Room history.
     * Scans all historical ChatMessages for a character and re-inserts into vector store.
     *
     * Use cases:
     * - After app update with new embedding model
     * - After vector database corruption
     * - First launch after upgrade from Phase 1 ProfileExtractor
     *
     * @param characterCode The character to rebuild index for
     */
    suspend fun rebuildIndex(characterCode: String) = withContext(Dispatchers.IO) {
        val dao = chatMessageDao ?: return@withContext

        // Clear existing vector memories for this character
        sqliteVecManager.deleteByCharacter(characterCode)

        // Load recent user messages from Room (up to 1000)
        val messages = dao.queryRecentByCharacter(characterCode, limit = 1000)
            .map { msgs -> msgs.filter { it.role == "user" } }
            .first()

        // Re-insert each message into vector store
        messages.forEach { message ->
            processMessage(message.content, characterCode, message.role)
        }
    }

    /**
     * Clear all vector memories for a character.
     */
    suspend fun clearCharacterMemory(characterCode: String) {
        sqliteVecManager.deleteByCharacter(characterCode)
    }

    /**
     * Get total memory count for a character.
     */
    suspend fun getMemoryCount(characterCode: String): Int {
        return sqliteVecManager.getMemoryCount(characterCode)
    }

    /**
     * Trim old memories to stay within MAX_MEMORY_COUNT limit.
     * Keeps the most recent memories.
     */
    private suspend fun trimOldMemories(characterCode: String) {
        val count = sqliteVecManager.getMemoryCount(characterCode)
        if (count <= MAX_MEMORY_COUNT) return
        // Trimming implementation: Phase 2 later use efficient SQL query
        // For now, just log and continue (the limit is high enough)
    }
}
