package com.brokenpip3.fatto.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal fun spacing(value: Int): Dp = value.dp

/** Shared spacing values, in 4dp increments. */
object FattoSpacing {
    val none = spacing(0)
    val xSmall = spacing(4)
    val small = spacing(8)
    val medium = spacing(12)
    val large = spacing(16)
    val extraLarge = spacing(24)
    val xxLarge = spacing(32)
}
