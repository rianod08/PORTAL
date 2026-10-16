package com.venuelink.studio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    secondary = Color(0xFF7C3AED),
    background = Color(0xFFEEF7FB),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onBackground = Color(0xFF0F172A),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF22D3EE),
    secondary = Color(0xFFA78BFA),
    background = Color(0xFF06111F),
    surface = Color(0xFF111827),
    onPrimary = Color(0xFF06111F),
    onBackground = Color.White,
)

@Composable
fun VenueLinkStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightScheme, typography = MaterialTheme.typography, content = content)
}
