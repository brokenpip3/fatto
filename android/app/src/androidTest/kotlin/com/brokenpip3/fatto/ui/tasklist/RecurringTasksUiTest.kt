package com.brokenpip3.fatto.ui.tasklist

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.TaskEditResult
import com.brokenpip3.fatto.ui.theme.NordicTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.taskchampion_android.RecurrenceData
import uniffi.taskchampion_android.StringEdit
import uniffi.taskchampion_android.TaskEditPatch
import uniffi.taskchampion_android.TaskStatus

@RunWith(AndroidJUnit4::class)
class RecurringTasksUiTest {
    @get:Rule val compose = createComposeRule()

    private fun task() =
        Task(
            uuid = "10000000-0000-4000-8000-000000000002", description = "Actual occurrence",
            status = TaskStatus.Pending, tags = emptyList(), due = null, entry = null, project = null,
            wait = null, scheduled = null, start = null, priority = null, urgency = 0f,
            isBlocked = false, isBlocking = false, dependencies = emptyList(), udas = emptyMap(),
            recurrence = RecurrenceData("daily", "periodic", "10000000-0000-4000-8000-000000000001", "0", null, null, null, null, null),
        )

    @Test fun failedSaveRetainsDraftAndBaselineUntilExplicitReload() {
        val current = mutableStateOf(task())
        var dismissed = false
        var submitted: TaskEditPatch? = null
        compose.setContent {
            NordicTheme {
                TaskDetailBottomSheet(current.value, onDismiss = { dismissed = true }, onSave = {
                    submitted = it
                    TaskEditResult.Failure("Task changed during sync")
                }, availableProjects = emptyList(), availableTags = emptyList())
            }
        }
        compose.onNodeWithTag("TaskDescriptionInput").performTextReplacement("My draft")
        compose.runOnIdle { current.value = current.value.copy(description = "Remote title") }
        compose.onNodeWithTag("TaskDescriptionInput").assertTextContains("My draft")
        compose.onNodeWithText("Save task").performScrollTo().performClick()
        compose.onNodeWithText("Task changed during sync").assertExists()
        compose.runOnIdle {
            assertFalse(dismissed)
            assertEquals(StringEdit.Replace("Actual occurrence", "My draft"), submitted?.description)
        }
        compose.onNodeWithText("Reload latest task").performScrollTo().performClick()
        compose.onNodeWithTag("TaskDescriptionInput").assertTextContains("Remote title")
    }

    @Test fun tagInputIsDisabledWhileTheCapturedDraftIsSaving() {
        val release = CompletableDeferred<Unit>()
        compose.setContent {
            NordicTheme {
                TaskDetailBottomSheet(task(), onDismiss = {}, onSave = {
                    release.await()
                    TaskEditResult.Success
                }, availableProjects = emptyList(), availableTags = emptyList())
            }
        }
        try {
            compose.onNodeWithTag("CloseButton", useUnmergedTree = true).performScrollTo().performClick()
            compose.onNodeWithText("Saving…").assertExists()
            compose.onNodeWithTag("TagInput").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithContentDescription("Select tags").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Set Priority").performScrollTo().assertIsNotEnabled()
        } finally {
            release.complete(Unit)
        }
    }

    @Test fun seriesDetailsRemainReadableWithLargeFontsInBothThemes() {
        val dark = mutableStateOf(false)
        val child = task().copy(description = "A long occurrence description that wraps across several lines")
        val interval = "an imported interval with a long unsupported description"
        val parent =
            child.copy(
                uuid = child.recurrence!!.parent!!, description = "Long recurring series",
                status = TaskStatus.Recurring,
                recurrence = child.recurrence.copy(parent = null, imask = null, recur = interval, mask = "+"),
            )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                NordicTheme(darkTheme = dark.value, fontSizePercent = 150) {
                    RecurringTasksSheet(listOf(parent), listOf(parent, child), emptyList(), {}, {})
                }
            }
        }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark.value = theme }
            compose.onNodeWithText("$interval").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Occurrences").performScrollTo().performClick()
            compose.onNodeWithText("${child.description} · No date · Pending").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Occurrences").performScrollTo().performClick()
        }
    }

    @Test fun seriesSheetShowsActualChildrenInExpandableHistory() {
        val child = task().copy(status = TaskStatus.Completed)
        val parent =
            child.copy(
                uuid = child.recurrence!!.parent!!, description = "Daily series", status = TaskStatus.Recurring,
                recurrence = child.recurrence.copy(parent = null, imask = null, mask = "+"),
            )
        var selected: Task? = null
        compose.setContent {
            NordicTheme {
                RecurringTasksSheet(listOf(parent), listOf(parent, child), emptyList(), {}, { selected = it })
            }
        }
        compose.onNodeWithText("Daily").assertExists()
        compose.onNodeWithText("Occurrences").performScrollTo().performClick()
        compose.onNodeWithText("Actual occurrence · No date · Completed").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(child, selected) }
    }
}
