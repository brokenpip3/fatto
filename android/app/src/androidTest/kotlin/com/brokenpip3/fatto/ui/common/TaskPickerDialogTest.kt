package com.brokenpip3.fatto.ui.common

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brokenpip3.fatto.data.model.Task
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.taskchampion_android.TaskStatus

@RunWith(AndroidJUnit4::class)
class TaskPickerDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun task(
        uuid: String,
        description: String,
        project: String? = null,
        tags: List<String> = emptyList(),
        status: TaskStatus = TaskStatus.PENDING,
    ): Task =
        Task(
            uuid = uuid,
            description = description,
            status = status,
            tags = tags,
            due = null,
            entry = null,
            project = project,
            wait = null,
            scheduled = null,
            start = null,
            priority = null,
            urgency = 0f,
            isBlocked = false,
            isBlocking = false,
            dependencies = emptyList(),
            udas = emptyMap(),
        )

    // Row nodes carry the click action (not the inner text), so match the row
    // by descendant text + click action on the unmerged tree.
    private fun rowWithText(text: String) =
        composeTestRule.onNode(
            hasAnyDescendant(hasText(text)) and hasClickAction(),
            useUnmergedTree = true,
        )

    @Test
    fun showsTitleAndTasks() {
        composeTestRule.setContent {
            TaskPickerDialog(
                title = "Add blocked by",
                tasks = listOf(task("1", "Alpha"), task("2", "Beta")),
                onDismiss = {},
                onConfirm = {},
            )
        }

        composeTestRule.onNodeWithText("Add blocked by").assertExists()
        composeTestRule.onNodeWithText("Alpha").assertExists()
        composeTestRule.onNodeWithText("Beta").assertExists()
    }

    @Test
    fun searchFiltersByDescription() {
        composeTestRule.setContent {
            TaskPickerDialog(
                title = "T",
                tasks = listOf(task("1", "Alpha task"), task("2", "Beta task")),
                onDismiss = {},
                onConfirm = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("TaskPickerSearch", useUnmergedTree = true)
            .performTextInput("alp")

        composeTestRule.onNodeWithText("Alpha task").assertExists()
        composeTestRule.onNodeWithText("Beta task").assertDoesNotExist()
    }

    @Test
    fun searchFiltersByProjectAndTags() {
        composeTestRule.setContent {
            TaskPickerDialog(
                title = "T",
                tasks =
                    listOf(
                        task("1", "Alpha", project = "work"),
                        task("2", "Beta", tags = listOf("urgent")),
                    ),
                onDismiss = {},
                onConfirm = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("TaskPickerSearch", useUnmergedTree = true)
            .performTextInput("work")
        composeTestRule.onNodeWithText("Alpha").assertExists()
        composeTestRule.onNodeWithText("Beta").assertDoesNotExist()

        composeTestRule.onNodeWithContentDescription("TaskPickerSearch", useUnmergedTree = true)
            .performTextReplacement("urgent")
        composeTestRule.onNodeWithText("Beta").assertExists()
        composeTestRule.onNodeWithText("Alpha").assertDoesNotExist()
    }

    @Test
    fun multiSelectCallsConfirmWithPickedTasks() {
        var confirmed: List<Task>? = null
        val alpha = task("1", "Alpha")
        val beta = task("2", "Beta")
        composeTestRule.setContent {
            TaskPickerDialog(
                title = "T",
                tasks = listOf(alpha, beta, task("3", "Gamma")),
                onDismiss = {},
                onConfirm = { confirmed = it },
            )
        }

        rowWithText("Alpha").performClick()
        rowWithText("Beta").performClick()
        composeTestRule.onNodeWithText("Add (2)").performClick()

        assertEquals(listOf(alpha, beta), confirmed)
    }

    @Test
    fun confirmDisabledWhenNothingSelected() {
        composeTestRule.setContent {
            TaskPickerDialog(
                title = "T",
                tasks = listOf(task("1", "Alpha")),
                onDismiss = {},
                onConfirm = {},
            )
        }

        composeTestRule.onNodeWithText("Add (0)").assertIsNotEnabled()
    }

    @Test
    fun emptyStateWhenNoMatches() {
        composeTestRule.setContent {
            TaskPickerDialog(
                title = "T",
                tasks = listOf(task("1", "Alpha")),
                onDismiss = {},
                onConfirm = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("TaskPickerSearch", useUnmergedTree = true)
            .performTextInput("zzz")

        composeTestRule.onNodeWithText("No tasks found").assertExists()
    }

    @Test
    fun cancelCallsDismiss() {
        var dismissed = false
        composeTestRule.setContent {
            TaskPickerDialog(
                title = "T",
                tasks = listOf(task("1", "Alpha")),
                onDismiss = { dismissed = true },
                onConfirm = {},
            )
        }

        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.runOnIdle { assertEquals(true, dismissed) }
    }
}
