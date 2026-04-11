package com.aiyougame.companion.drm

import com.aiyougame.companion.data.api.AiyougameApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * Hilt module providing DRM-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DrmModule {

    /**
     * Provide DrmApi using the existing Retrofit instance from NetworkModule.
     * The AiyougameApi base URL is http://10.0.2.2:3000/api/v1/ (for emulator).
     */
    @Provides
    @Singleton
    fun provideDrmApi(retrofit: Retrofit): DrmApi {
        return retrofit.create(DrmApi::class.java)
    }

    /**
     * KeystoreManager is provided via @Singleton constructor injection.
     * No extra provider needed — Hilt auto-generates the provider.
     */
}
