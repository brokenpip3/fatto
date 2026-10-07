package com.brokenpip3.fatto.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle

object FontSize {
    const val DEFAULT_PERCENT = 100
    const val MIN_PERCENT = 80
    const val MAX_PERCENT = 150

    fun normalize(percent: Int): Int = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)
}

internal val LocalAppFontMultiplier = staticCompositionLocalOf { 1f }

/** Keep layout adaptations aware of both the Android preference and the app adjustment. */
@Composable
fun effectiveFontScale(): Float = LocalDensity.current.fontScale * LocalAppFontMultiplier.current

internal fun scaledTypography(percent: Int): androidx.compose.material3.Typography {
    val multiplier = FontSize.normalize(percent) / 100f
    if (multiplier == 1f) return Typography

    fun TextStyle.scaled() =
        copy(
            fontSize = fontSize * multiplier,
            lineHeight = lineHeight * multiplier,
            letterSpacing = letterSpacing * multiplier,
        )
    return Typography.copy(
        displayLarge = Typography.displayLarge.scaled(),
        displayMedium = Typography.displayMedium.scaled(),
        displaySmall = Typography.displaySmall.scaled(),
        headlineLarge = Typography.headlineLarge.scaled(),
        headlineMedium = Typography.headlineMedium.scaled(),
        headlineSmall = Typography.headlineSmall.scaled(),
        titleLarge = Typography.titleLarge.scaled(),
        titleMedium = Typography.titleMedium.scaled(),
        titleSmall = Typography.titleSmall.scaled(),
        bodyLarge = Typography.bodyLarge.scaled(),
        bodyMedium = Typography.bodyMedium.scaled(),
        bodySmall = Typography.bodySmall.scaled(),
        labelLarge = Typography.labelLarge.scaled(),
        labelMedium = Typography.labelMedium.scaled(),
        labelSmall = Typography.labelSmall.scaled(),
    )
}
