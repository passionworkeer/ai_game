package com.aiyougame.companion

import android.app.Application
import android.content.ComponentCallbacks2
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AigameApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("AigameApp", "Phase 1 - App Started")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // 红线 1：内存安全。包含 C++ 堆不能超过 2.5G
        // 当系统内存紧张达到 TRIM_MEMORY_MODERATE 时，必须尽力释放
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            Log.w("AigameApp", "Memory low (level $level), triggering engine memory release.")
            // TODO: 调用 LlamaEngine.freeEngine(ptr) 释放模型内存，防止被系统强杀
        }
    }
}
