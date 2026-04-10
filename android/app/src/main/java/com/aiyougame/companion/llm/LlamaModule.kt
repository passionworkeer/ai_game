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

    // LlamaEngineImpl instances are managed by LlamaEngineManager (not Hilt-provided as singleton).
    // LlamaEngineManager uses Provider<LlamaEngineImpl> to create new instances.

    @Provides
    @Singleton
    fun provideLlamaEngine(impl: LlamaEngineImpl): LlamaEngine = impl

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
