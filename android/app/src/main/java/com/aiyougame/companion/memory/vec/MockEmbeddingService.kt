package com.aiyougame.companion.memory.vec

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Mock embedding service using text hash-based pseudo-embeddings.
 *
 * Phase 2 transition implementation — not semantically meaningful.
 * Each unique text produces a deterministic but pseudo-random 384-dim vector.
 *
 * Phase 2 later (BGE-micro integration):
 * Replace this with LlamaEmbeddingService that uses the BGE-micro GGML model:
 * - Load BGE-micro GGUF from assets
 * - Use llama.cpp embedding API for real semantic embeddings
 * - 384 dimensions, L2 normalized
 */
@Singleton
class MockEmbeddingService @Inject constructor() : EmbeddingService {

    companion object {
        const val EMBEDDING_DIM = 384
    }

    override suspend fun embed(text: String): FloatArray = withContext(Dispatchers.Default) {
        // Generate deterministic pseudo-random embedding based on text hash
        val hash = text.hashCode().toLong()
        val random = java.util.Random(hash)

        val embedding = FloatArray(EMBEDDING_DIM) { i ->
            // Pseudo-random values with small magnitude (not semantically meaningful)
            (random.nextGaussian() * 0.1f).toFloat()
        }

        // L2 normalize (BGE-micro outputs are L2 normalized)
        val norm = sqrt(embedding.map { it * it }.sum().toDouble()).toFloat()
        if (norm > 0f) {
            for (i in embedding.indices) {
                embedding[i] = embedding[i] / norm
            }
        }

        embedding
    }
}
