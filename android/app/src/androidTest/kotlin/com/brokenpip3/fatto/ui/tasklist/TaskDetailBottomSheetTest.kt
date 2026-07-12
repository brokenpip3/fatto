package com.brokenpip3.fatto.ui.tasklist

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brokenpip3.fatto.data.model.Task
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.taskchampion_android.TaskStatus

@RunWith(AndroidJUnit4::class)
class TaskDetailBottomSheetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun tagInputShowsAvailableTagSuggestions() {
        composeTestRule.setContent {
            TaskDetailBottomSheet(
                task = task(tags = listOf("home")),
                onDismiss = {},
                onSave = {},
                availableProjects = emptyList(),
                availableTags = listOf("urgent", "work", "home"),
            )
        }

        composeTestRule.onNode(
            hasContentDescription("TagInput") and hasAnyAncestor(hasTestTag("TaskDetailBottomSheet")),
            useUnmergedTree = true,
        ).performTextInput("ur")

        composeTestRule.onNode(
            hasText("urgent") and hasAnyAncestor(hasTestTag("TaskDetailBottomSheet")),
        ).assertExists()
    }

    private fun task(tags: List<String> = emptyList()): Task =
        Task(
            uuid = "task-1",
            description = "Edit me",
            status = TaskStatus.PENDING,
            tags = tags,
            due = null,
            entry = null,
            project = null,
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
}
