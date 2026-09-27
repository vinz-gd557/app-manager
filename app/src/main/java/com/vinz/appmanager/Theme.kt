package com.vinz.appmanager

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColorScheme = darkColorScheme(
    primary = Color(0xFF7C5CFC),
    secondary = Color(0xFF3ED9C4),
    background = Color(0xFF121214),
    surface = Color(0xFF1B1B1F),
    error = Color(0xFFFF5C7A)
)

@Composable
fun AppManagerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColorScheme, content = content)
}
