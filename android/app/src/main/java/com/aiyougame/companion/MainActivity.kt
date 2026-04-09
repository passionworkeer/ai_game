package com.aiyougame.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.aiyougame.companion.ui.navigation.AppNavigation
import com.aiyougame.companion.ui.theme.AIGameTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 沉浸式状态栏：让内容延伸到状态栏和导航栏区域
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContent {
            AIGameTheme {
                AppNavigation()
            }
        }
    }
}
