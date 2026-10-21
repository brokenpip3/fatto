package com.brokenpip3.fatto.ui.tasklist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.brokenpip3.fatto.data.model.RecurrenceRule
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.TaskEditResult
import com.brokenpip3.fatto.ui.theme.NordicTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uniffi.taskchampion_android.RecurrenceData
import uniffi.taskchampion_android.TaskStatus

class RecurrenceControlsTest {
    @get:Rule val compose = createComposeRule()

    private fun task() =
        Task(
            "10000000-0000-4000-8000-000000000001", "A task", TaskStatus.Pending, emptyList(), "2030-01-01T00:00:00Z",
            null, null, null, null, null, null, 0f, false, false, emptyList(), emptyMap(),
        )

    @Test fun creationShowsDatesAndRepeatValuesUnderControlsWithoutSummaryRows() {
        compose.setContent {
            NordicTheme {
                TaskDateAndPriorityControls(
                    priority = null, due = "2030-01-01T00:00:00Z", scheduled = null, wait = null,
                    onPriorityChange = {}, onDateClick = {},
                    trailingControl = { RecurrenceFields(RecurrenceRule("weekly"), "2030-01-01T00:00:00Z", {}, null) },
                )
            }
        }
        compose.onNodeWithText("2030-01-01").assertIsDisplayed()
        compose.onNodeWithText("Weekly").assertIsDisplayed()
        compose.onNodeWithText("Due:", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Repeat: Weekly").assertDoesNotExist()
    }

    @Test fun ordinaryTaskRepeatDraftSavesThroughAtomicConversion() {
        var converted: RecurrenceRule? = null
        var dismissed = false
        compose.setContent {
            NordicTheme {
                TaskDetailBottomSheet(
                    task(), onDismiss = { dismissed = true }, onSave = { error("Must use conversion") },
                    availableProjects = emptyList(), availableTags = emptyList(),
                    seriesActions =
                        SeriesEditorActions(
                            save = { _, _, _ -> error("Not an existing series") }, stop = { error("Not stopping") },
                            convert = { patch, rule ->
                                assertEquals(task().uuid, patch.uuid)
                                converted = rule
                                TaskEditResult.Success
                            },
                        ),
                )
            }
        }
        compose.onNodeWithTag("RepeatControl").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Repeat: None").performClick()
        compose.onNodeWithText("Weekly", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Apply").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(null, converted) }
        compose.onNodeWithText("Weekly").assertExists()
        compose.onNodeWithText("Save task").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(RecurrenceRule("weekly"), converted)
            assertTrue(dismissed)
        }
    }

    @Test fun invalidConversionDraftCanBeDiscardedWithoutSaving() {
        var dismissed = false
        compose.setContent {
            NordicTheme {
                TaskDetailBottomSheet(
                    task(), onDismiss = { dismissed = true }, onSave = { error("Must discard") },
                    availableProjects = emptyList(), availableTags = emptyList(),
                    seriesActions =
                        SeriesEditorActions(
                            save = { _, _, _ -> error("Must discard") }, stop = { error("Must discard") },
                            convert = { _, _ -> error("Must discard") },
                        ),
                )
            }
        }
        compose.onNodeWithTag("RepeatControl").performScrollTo().performClick()
        compose.onNodeWithText("Repeat: None").performClick()
        compose.onNodeWithText("Weekly", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Apply").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Due", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithTag("CloseButton").performScrollTo().performClick()
        compose.onNodeWithText("Discard unsaved changes?").assertIsDisplayed()
        compose.onNodeWithText("Discard changes").performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }

    @Test fun occurrenceRepeatIconOpensItsParentSeries() {
        val parent =
            task().copy(
                status = TaskStatus.Recurring,
                recurrence = RecurrenceData("weekly", "periodic", null, null, "-", null, null, null, null),
            )
        val occurrence =
            task().copy(
                uuid = "10000000-0000-4000-8000-000000000002",
                recurrence = RecurrenceData(null, "periodic", parent.uuid, "0", null, null, null, null, null),
            )
        compose.setContent {
            NordicTheme {
                TaskDetailBottomSheet(
                    occurrence, onDismiss = {}, onSave = { TaskEditResult.Success },
                    availableProjects = emptyList(), availableTags = emptyList(), allTasks = listOf(parent, occurrence),
                    seriesActions = SeriesEditorActions(save = { _, _, _ -> TaskEditResult.Success }, stop = { TaskEditResult.Success }),
                )
            }
        }
        compose.onNodeWithText("Edit series").assertDoesNotExist()
        compose.onNodeWithText("Weekly").assertExists()
        compose.onNodeWithTag("RepeatControl").performScrollTo().performClick()
        compose.onNodeWithText("Edit series").assertIsDisplayed()
    }
}
