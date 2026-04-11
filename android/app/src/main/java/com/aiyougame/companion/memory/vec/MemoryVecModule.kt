package com.aiyougame.companion.memory.vec

import android.content.Context
import com.aiyougame.companion.memory.db.ChatMessageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing vector memory dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object MemoryVecModule {

    /**
     * Provide SqliteVecManager for vector memory storage.
     * Uses FTS5 for KNN approximation (Phase 2 transition).
     * When sqlite-vec native lib is available, upgrade to real KNN queries.
     */
    @Provides
    @Singleton
    fun provideSqliteVecManager(
        @ApplicationContext context: Context
    ): SqliteVecManager {
        return SqliteVecManager(context)
    }

    /**
     * Provide EmbeddingService.
     * Phase 2 transition: MockEmbeddingService (hash-based pseudo-embedding).
     * Phase 2 later: Replace with LlamaEmbeddingService using BGE-micro GGML.
     */
    @Provides
    @Singleton
    fun provideEmbeddingService(): EmbeddingService {
        return MockEmbeddingService()
    }
}
