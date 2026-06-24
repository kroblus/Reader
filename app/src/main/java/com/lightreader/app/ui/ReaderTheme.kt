package com.lightreader.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ReaderColors = lightColorScheme(
    primary = Color(0xFF3D654C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5EAD9),
    onPrimaryContainer = Color(0xFF0D2B19),
    secondary = Color(0xFF6A5C45),
    background = Color(0xFFFAF9F6),
    surface = Color(0xFFFAF9F6),
    surfaceVariant = Color(0xFFECEAE4),
    onSurface = Color(0xFF242421),
)

@Composable
fun LightReaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ReaderColors, content = content)
}
