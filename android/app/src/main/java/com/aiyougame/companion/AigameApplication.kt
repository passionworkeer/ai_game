package com.aiyougame.companion

import android.app.Application
import android.content.ComponentCallbacks2
import android.util.Log
import com.aiyougame.companion.llm.LlamaEngineManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent

@HiltAndroidApp
class AigameApplication : Application() {

    // P0-A5-2 + P0-A8: EntryPoint for accessing Hilt singletons from non-Hilt classes (Application)
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface LlamaEngineEntryPoint {
        fun engineManager(): LlamaEngineManager
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("AigameApp", "Phase 2 - App Started")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Red line: Memory safety. App process (incl. C++ heap) must stay under 2.5GB.
        // When system memory pressure reaches TRIM_MEMORY_MODERATE, release all engines
        // to prevent being killed by the system LMK.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            Log.w("AigameApp", "Memory pressure (level=$level), releasing all LlamaEngine instances")
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    this,
                    LlamaEngineEntryPoint::class.java
                )
                entryPoint.engineManager().releaseAll()
            } catch (e: Exception) {
                Log.e("AigameApp", "Failed to release LlamaEngine instances", e)
            }
        }
    }
}
