package com.brokenpip3.fatto.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Shared Nordic surface corners. Prefer [androidx.compose.material3.MaterialTheme.shapes] in UI. */
object FattoShapes {
    val small = RoundedCornerShape(8.dp)
    val medium = RoundedCornerShape(12.dp)
    val large = RoundedCornerShape(20.dp)
    val extraLarge = RoundedCornerShape(28.dp)
}

val NordicShapes =
    Shapes(
        extraSmall = FattoShapes.small,
        small = FattoShapes.small,
        medium = FattoShapes.medium,
        large = FattoShapes.large,
        extraLarge = FattoShapes.extraLarge,
    )
