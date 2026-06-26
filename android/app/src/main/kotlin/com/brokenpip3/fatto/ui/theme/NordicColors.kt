package com.brokenpip3.fatto.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import kotlin.math.absoluteValue

private val NordicPalette =
    listOf(
        NordicRose, NordicMoss, NordicStorm, NordicSand,
        NordicOchre, NordicIndigo, NordicClay, NordicPine,
        NordicHeather, NordicAsh, NordicMutedBlue, NordicMutedGreen,
    )

private val NordicDarkPalette =
    listOf(
        Color(0xFFE3A6A6), Color(0xFFB6C8A8), Color(0xFFAFC1DC), Color(0xFFE0D08F),
        Color(0xFFE5B85C), Color(0xFFC7B2E8), Color(0xFFD69A73), Color(0xFF6FCFC3),
        Color(0xFFCAB4DE), Color(0xFFB5C6CF), Color(0xFFAEC1D1), Color(0xFF8BDBA8),
    )

@Composable
fun String.toNordicColor(): Color {
    if (this.isEmpty()) return NordicSlate
    val palette = if (MaterialTheme.colorScheme.background == NordicNight) NordicDarkPalette else NordicPalette
    val index = this.hashCode().absoluteValue % palette.size
    return palette[index]
}
