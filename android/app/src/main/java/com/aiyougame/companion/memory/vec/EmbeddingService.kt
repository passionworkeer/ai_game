package com.aiyougame.companion.memory.vec

/**
 * Embedding service interface.
 *
 * Phase 2 transition: MockEmbeddingService (text hash-based pseudo-embedding)
 * Phase 2 later: LlamaEmbeddingService using BGE-micro GGML model
 *
 * BGE-micro output: 384-dimensional float array, L2 normalized
 */
interface EmbeddingService {
    /**
     * Generate embedding vector for text.
     *
     * @param text Input text to embed
     * @return 384-dimensional float array (BGE-micro compatible)
     */
    suspend fun embed(text: String): FloatArray
}
