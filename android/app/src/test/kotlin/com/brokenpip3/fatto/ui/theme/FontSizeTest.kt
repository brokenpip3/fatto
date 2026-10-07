package com.brokenpip3.fatto.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class FontSizeTest {
    @Test
    fun largerTextScalesFontAndLineHeightTogether() {
        val scaled = scaledTypography(150)
        assertEquals(24f, scaled.bodyLarge.fontSize.value, 0.001f)
        assertEquals(36f, scaled.bodyLarge.lineHeight.value, 0.001f)
        assertEquals(21f, scaled.labelLarge.fontSize.value, 0.001f)
        assertEquals(30f, scaled.labelLarge.lineHeight.value, 0.001f)
    }

    @Test
    fun smallerTextAndResetKeepProportions() {
        assertEquals(12.8f, scaledTypography(80).bodyLarge.fontSize.value, 0.001f)
        assertEquals(Typography, scaledTypography(100))
    }
}
