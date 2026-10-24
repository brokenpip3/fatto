package com.brokenpip3.fatto.ui.tasklist

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.TaskEditResult
import com.brokenpip3.fatto.ui.theme.NordicTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.taskchampion_android.RecurrenceData
import uniffi.taskchampion_android.RecurrenceRulePatch
import uniffi.taskchampion_android.StringEdit
import uniffi.taskchampion_android.TaskStatus

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class RecurrenceTemplateUiTest {
    @get:Rule val dateDisplayConsent = com.brokenpip3.fatto.DateDisplayConsentTestRule()

    @get:Rule val compose = createComposeRule()

    private fun template() =
        Task(
            "10000000-0000-4000-8000-000000000001", "Daily series", TaskStatus.Recurring, emptyList(), "2030-01-01T00:00:00Z",
            null, null, null, null, null, null, 0f, false, false, emptyList(), emptyMap(),
            recurrence = RecurrenceData("daily", "periodic", null, null, "--", null, null, null, null),
        )

    @Test fun unchangedRawOptionalDateConfirmationDoesNotRepairTheSeries() {
        val original =
            template().copy(
                recurrence = template().recurrence!!.copy(mask = null),
                nativeDates = mapOf("scheduled" to "2030-01-02T00:00:00Z"),
            )
        var saved: uniffi.taskchampion_android.TaskEditPatch? = null
        compose.setContent {
            NordicTheme {
                RecurrenceTemplateSheet(original, listOf(original), {}, { patch, _, _ ->
                    saved = patch
                    TaskEditResult.Success
                }, { TaskEditResult.Success })
            }
        }
        compose.onNodeWithText("Change schedule").performScrollTo().performClick()
        compose.onNodeWithText("Scheduled").performScrollTo().performClick()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("Save schedule").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(StringEdit.Unchanged, saved?.scheduled)
            assertEquals(uniffi.taskchampion_android.DateModeEdit.Unchanged, saved?.dateModes?.scheduled)
        }
    }

    @Test fun modeOnlyEditDoesNotReplaceGeneratedSeries() {
        val previousZone = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
        try {
            val original =
                template().copy(
                    dateModes =
                        com.brokenpip3.fatto.data.model.calendarDateModes().copy(
                            due = uniffi.taskchampion_android.DateDisplayMode.LOCAL_DATE_TIME,
                        ),
                    udas = mapOf("fatto_display_due" to "v1:local-datetime"),
                )
            var replaced: Boolean? = null
            var submitted: uniffi.taskchampion_android.TaskEditPatch? = null
            var dirty = false
            compose.setContent {
                NordicTheme {
                    RecurrenceTemplateSheet(original, listOf(original), {}, { patch, _, replace ->
                        replaced = replace
                        submitted = patch
                        TaskEditResult.Success
                    }, { TaskEditResult.Success }, onStateChange = { _, changed -> dirty = changed })
                }
            }
            compose.onNodeWithText("Change schedule").performScrollTo().performClick()
            compose.onNodeWithText("First due date").performScrollTo().performClick()
            compose.onNodeWithText("Set time").performScrollTo().performClick()
            compose.onNodeWithText("OK").performClick()
            compose.runOnIdle { assertTrue("Display mode is part of the unsaved draft", dirty) }
            compose.onNodeWithText("Save schedule").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(false, replaced)
                assertEquals(StringEdit.Replace(original.due, original.due), submitted?.due)
                assertEquals(
                    uniffi.taskchampion_android.DateModeEdit.Replace(
                        "v1:local-datetime",
                        uniffi.taskchampion_android.DateDisplayMode.CALENDAR_DATE,
                    ),
                    submitted?.dateModes?.due,
                )
            }
        } finally {
            java.util.TimeZone.setDefault(previousZone)
        }
    }

    @Test fun malformedOptionalDatesClearAcrossReopenCancelAndSeriesSave() {
        var saved: uniffi.taskchampion_android.TaskEditPatch? = null
        val original =
            template().copy(
                recurrence = template().recurrence!!.copy(mask = null),
                nativeDates = mapOf("scheduled" to "bad-scheduled", "wait" to "bad-wait"),
            )
        compose.setContent {
            NordicTheme {
                RecurrenceTemplateSheet(original, listOf(original), {}, { patch, _, _ ->
                    saved = patch
                    TaskEditResult.Success
                }, { TaskEditResult.Success })
            }
        }
        compose.onNodeWithText("Change schedule").performScrollTo().performClick()
        for (label in listOf("Scheduled", "Wait")) {
            compose.onNodeWithText(label).performScrollTo().performClick()
            compose.onNodeWithText("Clear").performClick()
            compose.onNodeWithText(label).performScrollTo().performClick()
            compose.onNodeWithTag("TaskDatePickerCancel").performClick()
        }
        compose.onNodeWithText("Save schedule").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(StringEdit.Replace("bad-scheduled", null), saved?.scheduled)
            assertEquals(StringEdit.Replace("bad-wait", null), saved?.wait)
        }
    }

    @Test fun generatedScheduleRequiresExplicitReplacementAndRetainsOldOccurrences() {
        var replaced = false
        compose.setContent {
            NordicTheme {
                RecurrenceTemplateSheet(template(), listOf(template()), onDismiss = {}, onSave = { _, _, replace ->
                    replaced =
                        replace
                    ;TaskEditResult.Success
                }, onStop = { TaskEditResult.Success })
            }
        }
        compose.onNodeWithText("2030-01-01").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Change schedule").performScrollTo().performClick()
        compose.onNodeWithText("This starts a replacement series. Tasks already generated keep their current dates.").assertExists()
        compose.onNodeWithText("Replace series").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(replaced) }
    }

    @Test fun failedExplicitSaveKeepsSeriesDraft() {
        compose.setContent {
            NordicTheme {
                RecurrenceTemplateSheet(template(), listOf(template()), onDismiss = {}, onSave = {
                    _,
                    _,
                    _,
                    ->
                    TaskEditResult.Failure("Series changed elsewhere")
                }, onStop = { TaskEditResult.Success })
            }
        }
        compose.onNodeWithTag("SeriesDescriptionInput").performTextReplacement("My series draft")
        compose.onNodeWithText("Save series").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Series changed elsewhere"), 5000)
        compose.onNodeWithText("Series changed elsewhere").assertExists()
        compose.onNodeWithText("My series draft").assertExists()
    }

    @Test fun stoppingRequiresExplicitConfirmation() {
        var stopped = false
        compose.setContent {
            NordicTheme {
                RecurrenceTemplateSheet(template(), listOf(template()), onDismiss = {}, onSave = {
                    _,
                    _,
                    _,
                    ->
                    TaskEditResult.Success
                }, onStop = {
                    stopped =
                        true
                    ;TaskEditResult.Success
                })
            }
        }
        compose.onNodeWithText("Stop series").performScrollTo().performClick()
        compose.onNodeWithText("No new occurrences will be generated. Existing occurrences remain available.").assertExists()
        compose.onNodeWithTag("ConfirmStopSeries").performClick()
        compose.runOnIdle { assertTrue(stopped) }
    }

    @Test fun cancelReplacementRestoresScheduleAndKeepsContentDraft() {
        var submitted: RecurrenceRulePatch? = null
        compose.setContent {
            NordicTheme {
                RecurrenceTemplateSheet(template(), listOf(template()), {}, { _, rule, _ ->
                    submitted = rule
                    TaskEditResult.Success
                }, { TaskEditResult.Success })
            }
        }
        compose.onNodeWithTag("SeriesDescriptionInput").performTextReplacement("Keep content draft")
        compose.onNodeWithText("Change schedule").performScrollTo().performClick()
        compose.onNodeWithText("Daily").performScrollTo().performClick()
        compose.onNodeWithText("Weekly", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Save series"), 5000)
        compose.onNodeWithText("Keep content draft").assertExists()
        compose.onNodeWithText("Save series").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(StringEdit.Unchanged, submitted?.interval) }
    }

    @Test fun longImportedScheduleAndActionsRemainReadableAtCombinedFontScaleInBothThemes() {
        val dark = mutableStateOf(false)
        val imported =
            template().copy(
                recurrence = template().recurrence!!.copy(recur = "an imported long interval that remains unchanged"),
            )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                NordicTheme(darkTheme = dark.value, fontSizePercent = 150) {
                    RecurrenceTemplateSheet(imported, listOf(imported), {
                    }, { _, _, _ -> TaskEditResult.Success }, { TaskEditResult.Success })
                }
            }
        }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark.value = theme }
            compose.onNodeWithText("an imported long interval that remains unchanged").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Change schedule").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Save series").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Stop series").performScrollTo().assertIsDisplayed()
        }
    }
}
