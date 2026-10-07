package com.brokenpip3.fatto.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.brokenpip3.fatto.R

val InterFontFamily =
    FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )

private fun interStyle(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    letterSpacing: Float = 0f,
) = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

val Typography =
    Typography(
        displayLarge = interStyle(57, 64, FontWeight.Normal, -0.25f),
        displayMedium = interStyle(45, 52, FontWeight.Normal),
        displaySmall = interStyle(36, 44, FontWeight.Normal),
        headlineLarge = interStyle(32, 40, FontWeight.SemiBold),
        headlineMedium = interStyle(28, 36, FontWeight.SemiBold),
        headlineSmall = interStyle(24, 32, FontWeight.SemiBold, -0.02f),
        titleLarge = interStyle(22, 28, FontWeight.SemiBold),
        titleMedium = interStyle(16, 24, FontWeight.SemiBold, 0.15f),
        titleSmall = interStyle(14, 20, FontWeight.Bold, 0.1f),
        bodyLarge = interStyle(16, 24, letterSpacing = 0.01f),
        bodyMedium = interStyle(14, 20, letterSpacing = 0.25f),
        bodySmall = interStyle(12, 16, letterSpacing = 0.4f),
        labelLarge = interStyle(14, 20, FontWeight.Medium, 0.1f),
        labelMedium = interStyle(12, 16, FontWeight.Medium, 0.5f),
        labelSmall = interStyle(12, 16, FontWeight.Medium, 0.5f),
    )
