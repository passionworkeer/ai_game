package com.aiyougame.companion.data.network

import com.aiyougame.companion.data.api.AiyougameApi
import com.aiyougame.companion.data.interceptor.AuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt module providing singleton instances of the network stack.
 * Configured for Android emulator access to host machine localhost (10.0.2.2).
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "http://10.0.2.2:3000/api/v1/"

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Content-Type", "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideAiyougameApi(retrofit: Retrofit): AiyougameApi {
        return retrofit.create(AiyougameApi::class.java)
    }

    /**
     * Bare OkHttpClient for model CDN downloads.
     * No auth interceptor — CDN URLs are public and Range-header-aware.
     * Uses a separate dispatcher from the auth client to avoid thread contention.
     */
    @Provides
    @Singleton
    @Named("download")
    fun provideDownloadOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)   // large GGUF may take minutes
            .writeTimeout(30, TimeUnit.SECONDS)
            .dispatcher(okhttp3.Dispatcher().apply {
                maxRequests = 1  // one model at a time
            })
            .build()
    }
}
