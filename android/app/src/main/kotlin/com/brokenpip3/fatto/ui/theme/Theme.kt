package com.brokenpip3.fatto.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme =
    lightColorScheme(
        primary = NordicSlate,
        onPrimary = Color.White,
        primaryContainer = NordicIce,
        onPrimaryContainer = NordicMidnight,
        secondary = NordicMoss,
        onSecondary = Color.White,
        background = NordicFrost,
        onBackground = NordicMidnight,
        surface = Color.White,
        onSurface = NordicMidnight,
        surfaceVariant = NordicIce,
        onSurfaceVariant = NordicGrey,
        outline = NordicGrey,
        inverseSurface = NordicMidnight,
        inverseOnSurface = NordicFrost,
    )

@Composable
fun NordicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content,
    )
}
