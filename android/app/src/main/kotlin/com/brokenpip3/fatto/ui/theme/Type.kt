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

val Typography =
    Typography(
        headlineSmall =
            TextStyle(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                letterSpacing = (-0.02).sp,
                color = NordicMidnight,
            ),
        titleLarge =
            TextStyle(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.15.sp,
                color = NordicMidnight,
            ),
        titleSmall =
            TextStyle(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
                color = NordicMidnight,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.01.sp,
                color = NordicMidnight,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.25.sp,
                color = NordicMidnight,
            ),
        labelSmall =
            TextStyle(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.sp,
                color = NordicGrey,
            ),
    )
