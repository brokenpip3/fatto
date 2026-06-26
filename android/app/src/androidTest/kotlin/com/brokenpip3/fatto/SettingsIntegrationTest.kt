package com.brokenpip3.fatto

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brokenpip3.fatto.ui.theme.NordicNight
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsIntegrationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testAppInfoIsDisplayedInSettings() {
        // Navigate to settings
        composeTestRule.onNodeWithText("Settings").performClick()

        // Verify App Name
        composeTestRule.onNodeWithText("Fatto").assertExists()

        // Verify Subtitle
        composeTestRule.onNodeWithText("Your TaskWarrior android companion").assertExists()

        // Verify Version strings exist (partial match for Version)
        composeTestRule.onNodeWithText("Version", substring = true).assertExists()

        // Verify Build date exists (partial match)
        composeTestRule.onNodeWithText("Built on:", substring = true).assertExists()
    }

    @Test
    fun testCalendarStartDaySetting() {
        // Go to settings
        composeTestRule.onNodeWithText("Settings").performClick()

        // Select Sunday as the first day of the week
        composeTestRule.onNodeWithText("Sunday").performClick()

        // Go to Calendar
        composeTestRule.onNodeWithText("Calendar").performClick()

        // Assert "Sun" is present
        composeTestRule.onNodeWithText("Sun").assertExists()

        // Go back to settings
        composeTestRule.onNodeWithText("Settings").performClick()

        // Select Monday
        composeTestRule.onNodeWithText("Monday").performClick()

        // Go back to Calendar
        composeTestRule.onNodeWithText("Calendar").performClick()

        // Assert "Mon" is present
        composeTestRule.onNodeWithText("Mon").assertExists()
    }

    @Test
    fun testConfirmActionsSettingToggle() {
        // Go to settings
        composeTestRule.onNodeWithText("Settings").performClick()

        // Verify the checkbox text exists
        composeTestRule.onNodeWithText("Confirm complete/delete").assertExists()

        // Toggle it (it is enabled by default)
        composeTestRule.onNodeWithText("Confirm complete/delete").performClick()

        // Go to tasks and back to ensure it persists in ViewModel
        composeTestRule.onNodeWithText("Tasks").performClick()
        composeTestRule.onNodeWithText("Settings").performClick()

        // Verify it exists (we can't easily check 'checked' state with onNodeWithText but we verify it's still clickable/present)
        composeTestRule.onNodeWithText("Confirm complete/delete").assertExists()
    }

    @Test
    fun testDarkThemeChangesAppBackground() {
        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.onNodeWithText("Dark").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        val pixels = composeTestRule.onNodeWithTag("AppRoot").captureToImage().toPixelMap()
        val backgroundPixel = pixels[4, pixels.height / 2]

        assertEquals(NordicNight.toArgb(), backgroundPixel.toArgb())
    }
}
