package com.aiyougame.companion.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 微信风格干净的配色方案
private val LightColors = lightColorScheme(
    primary = Color(0xFF07C160), // 微信绿
    onPrimary = Color.White,
    background = Color(0xFFEFEFEF), // 会话列表背景灰
    surface = Color(0xFFF7F7F7), // 底部输入框背景
    onSurface = Color(0xFF111111)
)

@Composable
fun AIGameTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}
