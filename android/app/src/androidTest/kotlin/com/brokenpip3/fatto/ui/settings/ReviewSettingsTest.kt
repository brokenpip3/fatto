package com.brokenpip3.fatto.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.brokenpip3.fatto.data.review.ReviewSettings
import com.brokenpip3.fatto.ui.theme.NordicTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReviewSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun customReminderAcceptsNumbersStartingWithSeven() {
        var settings by mutableStateOf(ReviewSettings())
        compose.setContent {
            NordicTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ReviewSettingsSection(settings, emptyList(), emptyList(), { settings = it })
                }
            }
        }
        compose.onNodeWithText("Remind me to review").performScrollTo().performClick()
        compose.onNodeWithText("Custom").performClick()
        compose.onNodeWithText("Reminder interval (days)").performScrollTo().performTextInput("7")
        compose.onNodeWithText("Reminder interval (days)").performTextInput("5")
        assertEquals(75, settings.reminderDays)
    }
}
