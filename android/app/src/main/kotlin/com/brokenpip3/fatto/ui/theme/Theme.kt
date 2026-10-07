package com.brokenpip3.fatto.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

private val LightColorScheme =
    lightColorScheme(
        primary = NordicSlate,
        onPrimary = Color.White,
        primaryContainer = NordicIce,
        onPrimaryContainer = NordicMidnight,
        secondary = NordicMoss,
        onSecondary = Color.White,
        secondaryContainer = NordicSecondaryContainerLight,
        onSecondaryContainer = NordicOnSecondaryContainerLight,
        tertiary = NordicStorm,
        onTertiary = Color.White,
        tertiaryContainer = NordicIce,
        onTertiaryContainer = NordicMidnight,
        background = NordicFrost,
        onBackground = NordicMidnight,
        surface = Color.White,
        onSurface = NordicMidnight,
        surfaceVariant = NordicIce,
        surfaceContainerLowest = NordicSurfaceContainerLowestLight,
        surfaceContainerLow = NordicSurfaceContainerLowLight,
        surfaceContainer = NordicSurfaceContainerLight,
        surfaceContainerHigh = NordicSurfaceContainerHighLight,
        surfaceContainerHighest = NordicSurfaceContainerHighestLight,
        surfaceBright = NordicSurfaceBrightLight,
        surfaceDim = NordicSurfaceDimLight,
        surfaceTint = NordicSurfaceTintLight,
        onSurfaceVariant = NordicGrey,
        outline = NordicGrey,
        outlineVariant = NordicOutlineVariantLight,
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        inverseSurface = NordicMidnight,
        inverseOnSurface = NordicFrost,
        inversePrimary = NordicIce,
        scrim = Color.Black,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = NordicIceBlue,
        onPrimary = NordicNight,
        primaryContainer = NordicNightSurfaceVariant,
        onPrimaryContainer = NordicMist,
        secondary = NordicDarkMoss,
        onSecondary = NordicNight,
        secondaryContainer = NordicSecondaryContainerDark,
        onSecondaryContainer = NordicOnSecondaryContainerDark,
        tertiary = NordicTertiaryDark,
        onTertiary = NordicNight,
        tertiaryContainer = Color(0xFF33283D),
        onTertiaryContainer = NordicMist,
        background = NordicNight,
        onBackground = NordicMist,
        surface = NordicNightSurface,
        onSurface = NordicMist,
        surfaceVariant = NordicNightSurfaceVariant,
        surfaceContainerLowest = NordicSurfaceContainerLowestDark,
        surfaceContainerLow = NordicSurfaceContainerLowDark,
        surfaceContainer = NordicSurfaceContainerDark,
        surfaceContainerHigh = NordicSurfaceContainerHighDark,
        surfaceContainerHighest = NordicSurfaceContainerHighestDark,
        surfaceBright = NordicSurfaceBrightDark,
        surfaceDim = NordicSurfaceDimDark,
        surfaceTint = NordicSurfaceTintDark,
        onSurfaceVariant = NordicBlueGrey,
        outline = NordicDarkOutline,
        outlineVariant = NordicOutlineVariantDark,
        inverseSurface = NordicMist,
        inverseOnSurface = NordicNight,
        inversePrimary = NordicSlate,
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        scrim = Color.Black,
    )

@Composable
fun NordicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontSizePercent: Int = FontSize.DEFAULT_PERCENT,
    content: @Composable () -> Unit,
) {
    val typography = remember(fontSizePercent) { scaledTypography(fontSizePercent) }
    CompositionLocalProvider(LocalAppFontMultiplier provides (FontSize.normalize(fontSizePercent) / 100f)) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = typography,
            shapes = NordicShapes,
            content = content,
        )
    }
}
