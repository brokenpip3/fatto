package com.brokenpip3.fatto.ui.common

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.ui.captureLayoutScreenshot
import com.brokenpip3.fatto.ui.theme.NordicTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun longDescriptionsHaveSpaceBesideTheCheckbox() {
        val description = "write a blog post about the new hm podman module and how to use it for local setup"
        composeTestRule.setContent {
            NordicTheme(darkTheme = true) {
                TaskPickerDialog("Add blocked by", listOf(task("long", description, "writing.techblog")), {}, {})
            }
        }
        val row = rowWithText(description).fetchSemanticsNode().boundsInRoot
        val text = composeTestRule.onNodeWithText(description, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val minimumIndent = with(composeTestRule.density) { 36.dp.toPx() }
        assertTrue("Checkbox and text need breathing room", text.left - row.left >= minimumIndent - 1f)
        captureLayoutScreenshot("task-picker-long")
    }

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

        composeTestRule.onNodeWithTag("TaskPickerSearch", useUnmergedTree = true)
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

        composeTestRule.onNodeWithTag("TaskPickerSearch", useUnmergedTree = true)
            .performTextInput("work")
        composeTestRule.onNodeWithText("Alpha").assertExists()
        composeTestRule.onNodeWithText("Beta").assertDoesNotExist()

        composeTestRule.onNodeWithTag("TaskPickerSearch", useUnmergedTree = true)
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

        composeTestRule.onNodeWithTag("TaskPickerSearch", useUnmergedTree = true)
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
