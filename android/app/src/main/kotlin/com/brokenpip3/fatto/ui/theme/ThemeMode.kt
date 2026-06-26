package com.brokenpip3.fatto.ui.theme

enum class ThemeMode(val storedValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun fromStoredValue(value: String?): ThemeMode {
            return entries.firstOrNull { it.storedValue == value } ?: SYSTEM
        }
    }
}
