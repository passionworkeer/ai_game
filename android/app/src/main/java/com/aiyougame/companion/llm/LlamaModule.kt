package com.aiyougame.companion.llm

import android.content.Context
import com.aiyougame.companion.engine.ChatTemplateLoader
import com.aiyougame.companion.engine.ChatTemplateLoaderImpl
import com.aiyougame.companion.engine.ModelDownloader
import com.aiyougame.companion.engine.ModelDownloaderImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LlamaModule {

    // Both engine implementations are Hilt singletons.
    // LlamaEngineManager picks which one to use based on BuildConfig.OLLAMA_ENABLED.
    // Each engine manages per-character state internally.

    @Provides
    @Singleton
    fun provideChatTemplateLoader(
        @ApplicationContext context: Context,
    ): ChatTemplateLoader = ChatTemplateLoaderImpl(context)

    @Provides
    @Singleton
    fun provideModelDownloader(
        @ApplicationContext context: Context,
        @Named("download") okHttpClient: OkHttpClient,
    ): ModelDownloader = ModelDownloaderImpl(context, okHttpClient)
}
