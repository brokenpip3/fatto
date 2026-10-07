@file:Suppress("ktlint:standard:filename")

package com.brokenpip3.fatto.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable

/** Nordic color roles for Material text fields, retaining Material's native disabled colors. */
object FattoFieldDefaults {
    @Composable
    fun filledColors(): TextFieldColors {
        val colors = MaterialTheme.colorScheme
        return TextFieldDefaults.colors(
            focusedTextColor = colors.onSurface,
            unfocusedTextColor = colors.onSurface,
            focusedContainerColor = colors.surfaceContainerLowest,
            unfocusedContainerColor = colors.surfaceContainerLowest,
            focusedLabelColor = colors.primary,
            unfocusedLabelColor = colors.onSurfaceVariant,
            focusedIndicatorColor = colors.primary,
            unfocusedIndicatorColor = colors.outlineVariant,
            cursorColor = colors.primary,
            errorTextColor = colors.error,
            errorLabelColor = colors.error,
            errorIndicatorColor = colors.error,
            errorCursorColor = colors.error,
        )
    }

    @Composable
    fun outlinedColors(): TextFieldColors {
        val colors = MaterialTheme.colorScheme
        return OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.onSurface,
            unfocusedTextColor = colors.onSurface,
            focusedContainerColor = colors.surfaceContainerLowest,
            unfocusedContainerColor = colors.surfaceContainerLowest,
            focusedLabelColor = colors.primary,
            unfocusedLabelColor = colors.onSurfaceVariant,
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = colors.outline,
            cursorColor = colors.primary,
            errorTextColor = colors.error,
            errorLabelColor = colors.error,
            errorBorderColor = colors.error,
            errorCursorColor = colors.error,
        )
    }
}
