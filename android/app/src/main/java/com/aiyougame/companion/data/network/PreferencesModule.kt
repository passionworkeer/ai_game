package com.aiyougame.companion.data.network

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing SharedPreferences instances.
 * app_prefs: general app settings (cloud sync, OpenClaw IP, etc.)
 */
@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {

    private const val APP_PREFS = "app_settings"

    @Provides
    @Singleton
    fun provideAppPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
    }
}
