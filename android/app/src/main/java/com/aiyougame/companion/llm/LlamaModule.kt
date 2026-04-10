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
    fun provideLlamaEngine(): LlamaEngine = MockLlamaEngine()

    @Provides
    @Singleton
    fun provideModelDownloader(
        @ApplicationContext context: Context,
        @Named("download") okHttpClient: OkHttpClient,
    ): ModelDownloader = ModelDownloaderImpl(context, okHttpClient)

    @Provides
    @Singleton
    fun provideChatTemplateLoader(): ChatTemplateLoader = ChatTemplateLoaderImpl()
}
