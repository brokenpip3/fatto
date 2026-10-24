package com.brokenpip3.fatto.ui.tasklist

import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import com.brokenpip3.fatto.data.TaskDateValue
import com.brokenpip3.fatto.ui.assertTextFits
import com.brokenpip3.fatto.ui.theme.NordicTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import uniffi.taskchampion_android.DateDisplayMode
import java.util.Calendar
import java.util.TimeZone

class TaskDatePickerLayoutTest {
    @get:Rule val dateDisplayConsent = com.brokenpip3.fatto.DateDisplayConsentTestRule(accepted = false)

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var previousZone: TimeZone
    private var previousClock: String? = null
    private var previousWidth = 0
    private var previousFontScale = 1f
    private val fields = hasSetTextAction() and hasAnyAncestor(hasTestTag("TaskDateTimeInput"))

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command))
            .use { it.readBytes() }
    }

    @Before fun before() {
        previousZone = TimeZone.getDefault()
        previousClock = Settings.System.getString(compose.activity.contentResolver, Settings.System.TIME_12_24)
        previousFontScale = compose.activity.resources.configuration.fontScale
        previousWidth = compose.activity.resources.configuration.screenWidthDp
    }

    @After fun after() {
        TimeZone.setDefault(previousZone)
        if (previousClock == null) shell("settings delete system time_12_24") else shell("settings put system time_12_24 $previousClock")
        shell("settings put system font_scale $previousFontScale")
        shell("wm size reset")
        compose.waitUntil(5000) {
            runCatching {
                val configuration = compose.activity.resources.configuration
                configuration.fontScale == previousFontScale && configuration.screenWidthDp == previousWidth
            }.getOrDefault(false)
        }
    }

    private fun largePicker(
        dark: Boolean,
        clock: String,
    ) {
        shell("settings put system time_12_24 $clock")
        shell("wm size 312x640")
        shell("settings put system font_scale 2.0")
        compose.waitUntil(5000) { compose.activity.resources.configuration.fontScale == 2f }
        TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/ComodRivadavia"))
        compose.setContent {
            NordicTheme(darkTheme = dark, fontSizePercent = 150) {
                DateTimePickerDialog(
                    TaskDateValue("2030-01-02T13:30:45Z", DateDisplayMode.LOCAL_DATE_TIME), Calendar.MONDAY,
                    onConfirm = {}, onClear = {}, onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("Set time").performScrollTo().assertIsDisplayed()
        val first = compose.onAllNodes(fields)[0]
        first.performScrollTo().performClick()
        assertTextFits(first)
        first.performTextReplacement("10")
        compose.onAllNodes(fields)[1].performScrollTo().performClick()
        assertTextFits(compose.onAllNodes(fields)[1])
        val zoneLabel = compose.onNodeWithText("America/Argentina/ComodRivadavia", substring = true)
        // Native IME inset animations can continue after Compose becomes idle.
        var scrollFailure: Throwable? = null
        try {
            compose.waitUntil(5000) {
                runCatching { zoneLabel.performScrollTo().assertIsDisplayed() }
                    .onFailure { scrollFailure = it }.isSuccess
            }
        } catch (failure: Throwable) {
            throw AssertionError("Timezone scroll failed: ${zoneLabel.fetchSemanticsNode()}", scrollFailure ?: failure)
        }
        assertTextFits(zoneLabel)
        for (label in listOf("OK", "Cancel", "Clear")) {
            compose.onNodeWithText(label).assertIsDisplayed()
            assertTextFits(compose.onNodeWithText(label))
        }
    }

    @Test fun largeLightPickerKeepsClockAndActionsReachableWithKeyboard() = largePicker(false, "24")

    @Test fun largeDarkPickerKeepsClockAndActionsReachableInTwelveHourMode() = largePicker(true, "12")

    @Test fun daylightSavingGapShowsErrorAndCancelRemainsReachable() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Rome"))
        shell("settings put system time_12_24 24")
        var confirmed = 0
        var cancelled = 0
        compose.setContent {
            NordicTheme {
                DateTimePickerDialog(
                    TaskDateValue("2026-03-29T00:30:45Z", DateDisplayMode.LOCAL_DATE_TIME), Calendar.MONDAY,
                    onConfirm = { confirmed++ }, onClear = {}, onDismiss = { cancelled++ },
                )
            }
        }
        compose.onAllNodes(fields)[0].performScrollTo().performTextReplacement("02")
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("This clock time does not exist", substring = true).performScrollTo().assertIsDisplayed()
        assertEquals(0, confirmed)
        compose.onAllNodes(fields)[0].performScrollTo().performTextReplacement("04")
        compose.onNodeWithText("Europe/Rome (+02:00)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, cancelled)
    }

    @Test fun importedYear9999CanBeCancelledWithoutChangingItsValue() {
        var writes = 0
        var cancelled = 0
        compose.setContent {
            NordicTheme {
                DateTimePickerDialog(
                    TaskDateValue("9999-12-31T23:59:45Z", DateDisplayMode.CALENDAR_DATE), Calendar.MONDAY,
                    onConfirm = { writes++ }, onClear = { writes++ }, onDismiss = { cancelled++ },
                )
            }
        }
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, writes)
        assertEquals(1, cancelled)
    }

    private fun largeNotice(dark: Boolean) {
        shell("wm size 312x640")
        shell("settings put system font_scale 2.0")
        compose.waitUntil(5000) { compose.activity.resources.configuration.fontScale == 2f }
        compose.setContent {
            NordicTheme(darkTheme = dark, fontSizePercent = 150) {
                DateTimePickerDialog(
                    TaskDateValue("2030-01-02T00:00:00Z", DateDisplayMode.CALENDAR_DATE), Calendar.MONDAY,
                    onConfirm = {}, onClear = {}, onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("Set time").performScrollTo().performClick()
        compose.onNodeWithText("Continue").assertIsDisplayed()
        assertTextFits(compose.onNodeWithText("Continue"))
        val cancel =
            compose.onNode(
                androidx.compose.ui.test.hasText("Cancel") and
                    hasAnyAncestor(hasTestTag("DateDisplayUdaNotice")),
            )
        cancel.assertIsDisplayed()
        assertTextFits(cancel)
        compose.onNodeWithText("Learn more").performScrollTo().assertIsDisplayed()
        assertTextFits(compose.onNodeWithText("Learn more"))
        cancel.performClick()
        compose.onNodeWithText("Set time").assertExists()
    }

    @Test fun largeLightNoticeKeepsTheLinkAndDecisionButtonsReachable() = largeNotice(false)

    @Test fun largeDarkNoticeKeepsTheLinkAndDecisionButtonsReachable() = largeNotice(true)
}
