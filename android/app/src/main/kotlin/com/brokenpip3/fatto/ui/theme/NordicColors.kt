package com.brokenpip3.fatto.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.absoluteValue

val NordicPalette =
    listOf(
        NordicRose, NordicMoss, NordicStorm, NordicSand,
        NordicOchre, NordicIndigo, NordicClay, NordicPine,
        NordicHeather, NordicAsh, NordicMutedBlue, NordicMutedGreen,
    )

fun String.toNordicColor(): Color {
    if (this.isEmpty()) return NordicSlate
    val index = this.hashCode().absoluteValue % NordicPalette.size
    return NordicPalette[index]
}
