package com.brokenpip3.fatto

import com.brokenpip3.fatto.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {
    @Test
    fun parseStoredValueReturnsMatchingMode() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStoredValue("system"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStoredValue("light"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStoredValue("dark"))
    }

    @Test
    fun parseStoredValueFallsBackToSystemForUnknownValues() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStoredValue(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStoredValue(""))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStoredValue("amoled"))
    }
}
