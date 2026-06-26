package com.brokenpip3.fatto.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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
        inversePrimary = NordicIce,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = NordicIceBlue,
        onPrimary = NordicNight,
        primaryContainer = NordicNightSurfaceVariant,
        onPrimaryContainer = NordicMist,
        secondary = NordicDarkMoss,
        onSecondary = NordicNight,
        tertiary = NordicHeather,
        onTertiary = NordicNight,
        tertiaryContainer = Color(0xFF33283D),
        onTertiaryContainer = NordicMist,
        background = NordicNight,
        onBackground = NordicMist,
        surface = NordicNightSurface,
        onSurface = NordicMist,
        surfaceVariant = NordicNightSurfaceVariant,
        onSurfaceVariant = NordicBlueGrey,
        outline = NordicDarkOutline,
        inverseSurface = NordicMist,
        inverseOnSurface = NordicNight,
        inversePrimary = NordicSlate,
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
    )

@Composable
fun NordicTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
