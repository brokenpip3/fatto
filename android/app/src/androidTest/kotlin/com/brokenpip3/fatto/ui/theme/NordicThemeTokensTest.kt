package com.brokenpip3.fatto.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NordicThemeTokensTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun lightAndDarkThemesExposeTheSharedShapeTypeAndContrastTokens() {
        var light: ThemeSnapshot? = null
        var dark: ThemeSnapshot? = null
        val paletteColors = mutableMapOf<Boolean, List<Color>>()

        composeTestRule.setContent {
            NordicTheme(darkTheme = false) {
                val lightShapes = MaterialTheme.shapes
                val lightTypography = MaterialTheme.typography
                val lightColors = MaterialTheme.colorScheme
                val lightPaletteColors = auditedPalette.map { it.toNordicColor() }
                SideEffect {
                    light = ThemeSnapshot(lightShapes, lightTypography, lightColors)
                    paletteColors[false] = lightPaletteColors
                }
                NordicTheme(darkTheme = true) {
                    val darkShapes = MaterialTheme.shapes
                    val darkTypography = MaterialTheme.typography
                    val darkColors = MaterialTheme.colorScheme
                    val darkPaletteColors = auditedPalette.map { it.toNordicColor() }
                    SideEffect {
                        dark = ThemeSnapshot(darkShapes, darkTypography, darkColors)
                        paletteColors[true] = darkPaletteColors
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(4.dp, FattoSpacing.xSmall)
        assertEquals(8.dp, FattoSpacing.small)
        assertEquals(12.dp, FattoSpacing.medium)
        assertEquals(16.dp, FattoSpacing.large)
        assertEquals(24.dp, FattoSpacing.extraLarge)
        assertEquals(0.dp, FattoSpacing.none)
        assertEquals(32.dp, FattoSpacing.xxLarge)

        assertEquals(Color(0xFFFFFFFF), light!!.colors.surfaceContainerLowest)
        assertEquals(Color(0xFFF4F7F9), light!!.colors.surfaceContainerLow)
        assertEquals(Color(0xFFEDF2F5), light!!.colors.surfaceContainer)
        assertEquals(Color(0xFFE7EDF2), light!!.colors.surfaceContainerHigh)
        assertEquals(Color(0xFFE0E6ED), light!!.colors.surfaceContainerHighest)
        assertEquals(Color(0xFFFFFFFF), light!!.colors.surfaceBright)
        assertEquals(Color(0xFFE0E6ED), light!!.colors.surfaceDim)
        assertEquals(Color(0xFF4A6274), light!!.colors.surfaceTint)
        assertEquals(Color(0xFFC2CCD4), light!!.colors.outlineVariant)
        assertEquals(Color(0xFFE1EBDD), light!!.colors.secondaryContainer)
        assertEquals(Color(0xFF263624), light!!.colors.onSecondaryContainer)
        assertEquals(Color(0xFFCAB4DE), dark!!.colors.tertiary)
        assertEquals(Color(0xFF0B1117), dark!!.colors.onTertiary)
        assertEquals(Color(0xFF0B1117), dark!!.colors.surfaceContainerLowest)
        assertEquals(Color(0xFF121A22), dark!!.colors.surfaceContainerLow)
        assertEquals(Color(0xFF17212B), dark!!.colors.surfaceContainer)
        assertEquals(Color(0xFF1B2630), dark!!.colors.surfaceContainerHigh)
        assertEquals(Color(0xFF253340), dark!!.colors.surfaceContainerHighest)
        assertEquals(Color(0xFF253340), dark!!.colors.surfaceBright)
        assertEquals(Color(0xFF0B1117), dark!!.colors.surfaceDim)
        assertEquals(Color(0xFF8FB8D8), dark!!.colors.surfaceTint)
        assertEquals(Color(0xFF3C4A57), dark!!.colors.outlineVariant)
        assertEquals(Color(0xFF27382B), dark!!.colors.secondaryContainer)
        assertEquals(Color(0xFFDCEAD8), dark!!.colors.onSecondaryContainer)

        listOfNotNull(light, dark).forEach { theme ->
            assertEquals(RoundedCornerShape(8.dp), theme.shapes.small)
            assertEquals(RoundedCornerShape(12.dp), theme.shapes.medium)
            assertEquals(RoundedCornerShape(20.dp), theme.shapes.large)
            assertEquals(RoundedCornerShape(28.dp), theme.shapes.extraLarge)
            assertEquals(12.sp, theme.typography.bodySmall.fontSize)
            assertEquals(12.sp, theme.typography.labelSmall.fontSize)
            assertTrue(theme.typography.allStyles().all { it.fontFamily == InterFontFamily })
            assertThemeTextContrast(theme.colors)
        }
        assertTrue("Both color modes must be captured", light != null && dark != null)
        listOf(false, true).forEach { darkTheme ->
            val surface = if (darkTheme) dark!!.colors.surface else light!!.colors.surface
            paletteColors.getValue(darkTheme).forEach { foreground ->
                assertTrue(
                    "${foreground.toArgbHex()} must meet 4.5:1 contrast on ${surface.toArgbHex()}",
                    contrastRatio(foreground, surface) >= 4.5,
                )
            }
        }
    }

    private fun assertThemeTextContrast(colors: ColorScheme) {
        listOf(
            colors.onPrimary to colors.primary,
            colors.onPrimaryContainer to colors.primaryContainer,
            colors.onSecondary to colors.secondary,
            colors.onTertiary to colors.tertiary,
            colors.onSecondaryContainer to colors.secondaryContainer,
            colors.onBackground to colors.background,
            colors.onSurface to colors.surface,
            colors.onSurface to colors.surfaceContainerLowest,
            colors.onSurface to colors.surfaceContainerLow,
            colors.onSurface to colors.surfaceContainer,
            colors.onSurface to colors.surfaceContainerHigh,
            colors.onSurface to colors.surfaceContainerHighest,
            colors.onSurfaceVariant to colors.surface,
            colors.onSurfaceVariant to colors.surfaceVariant,
            colors.onError to colors.error,
            colors.onErrorContainer to colors.errorContainer,
        ).forEach { (foreground, background) ->
            assertTrue(
                "${foreground.toArgbHex()} on ${background.toArgbHex()} must meet 4.5:1 text contrast",
                contrastRatio(foreground, background) >= 4.5,
            )
        }
    }

    private fun Typography.allStyles() =
        listOf(
            displayLarge,
            displayMedium,
            displaySmall,
            headlineLarge,
            headlineMedium,
            headlineSmall,
            titleLarge,
            titleMedium,
            titleSmall,
            bodyLarge,
            bodyMedium,
            bodySmall,
            labelLarge,
            labelMedium,
            labelSmall,
        )

    private val auditedPalette =
        listOf("rose", "moss", "storm", "sand", "ochre", "indigo", "clay", "pine", "heather", "ash", "blue", "green")

    private fun contrastRatio(
        foreground: Color,
        background: Color,
    ): Double {
        val first = foreground.luminance().toDouble()
        val second = background.luminance().toDouble()
        val lighter = maxOf(first, second)
        val darker = minOf(first, second)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun Color.toArgbHex(): String = "#%08X".format(toArgb())

    private data class ThemeSnapshot(
        val shapes: Shapes,
        val typography: Typography,
        val colors: ColorScheme,
    )
}
