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

    @Provides
    @Singleton
    fun provideLlamaEngine(impl: LlamaEngineImpl): LlamaEngine = impl

    @Provides
    @Singleton
    fun provideModelCdnUrl(): String =
        "https://cdn.aiyougame.com/models/gemma-4-E4B-it-Q4_0.gguf"

    @Provides
    @Singleton
    fun provideModelFileName(): String = "gemma-4-E4B-it-Q4_0.gguf"

    @Provides
    @Singleton
    fun provideModelDownloader(
        @ApplicationContext context: Context,
        @Named("download") okHttpClient: OkHttpClient,
    ): ModelDownloader = ModelDownloaderImpl(context, okHttpClient)

    @Provides
    @Singleton
    fun provideModelDownloadManager(
        @ApplicationContext context: Context,
        @Named("download") okHttpClient: OkHttpClient,
    ): ModelDownloadManager = ModelDownloadManager(
        context = context,
        okHttpClient = okHttpClient,
    )

    @Provides
    @Singleton
    fun provideChatTemplateLoader(): ChatTemplateLoader = ChatTemplateLoaderImpl()
}
