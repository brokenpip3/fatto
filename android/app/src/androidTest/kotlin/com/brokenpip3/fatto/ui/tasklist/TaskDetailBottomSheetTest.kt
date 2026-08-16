package com.brokenpip3.fatto.ui.tasklist

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

    @Test
    fun blockedByPickerAddsDependency() {
        var addedUuid: String? = null
        var addedDeps: List<String>? = null
        val open = task()
        val blocker = task(uuid = "blocker-1", description = "Blocker One")
        composeTestRule.setContent {
            TaskDetailBottomSheet(
                task = open,
                onDismiss = {},
                onSave = {},
                availableProjects = emptyList(),
                availableTags = emptyList(),
                allTasks = listOf(open, blocker),
                onAddDependencies = { uuid, deps ->
                    addedUuid = uuid
                    addedDeps = deps
                },
            )
        }

        composeTestRule.onNodeWithText("Blocked by").performClick()
        composeTestRule.onNodeWithContentDescription("AddBlockedByButton", useUnmergedTree = true)
            .performClick()

        composeTestRule.onNode(
            hasAnyDescendant(hasText("Blocker One")) and hasClickAction(),
            useUnmergedTree = true,
        ).performClick()
        composeTestRule.onNodeWithText("Add (1)").performClick()

        composeTestRule.runOnIdle {
            assertEquals("task-1", addedUuid)
            assertEquals(listOf("blocker-1"), addedDeps)
        }
        // Row appears in the sheet after local state update
        composeTestRule.onNode(
            hasText("Blocker One") and hasAnyAncestor(hasTestTag("TaskDetailBottomSheet")),
        ).assertExists()
    }

    @Test
    fun blockedByRemoveCallsCallback() {
        var removed: Pair<String, String>? = null
        val open = task(dependencies = listOf("blocker-1"))
        val blocker = task(uuid = "blocker-1", description = "Blocker One")
        composeTestRule.setContent {
            TaskDetailBottomSheet(
                task = open,
                onDismiss = {},
                onSave = {},
                availableProjects = emptyList(),
                availableTags = emptyList(),
                allTasks = listOf(open, blocker),
                onRemoveDependency = { uuid, dep ->
                    removed = uuid to dep
                },
            )
        }

        // Accordion auto-expanded because dependencies is non-empty
        composeTestRule.onNodeWithContentDescription("Remove dependency", useUnmergedTree = true)
            .performScrollTo()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals("task-1" to "blocker-1", removed)
        }
    }

    @Test
    fun blockedByPickerExcludesSelfAndExistingDeps() {
        val open = task(dependencies = listOf("blocker-1"))
        val blocker = task(uuid = "blocker-1", description = "Existing blocker")
        val other = task(uuid = "task-2", description = "Another task")
        composeTestRule.setContent {
            TaskDetailBottomSheet(
                task = open,
                onDismiss = {},
                onSave = {},
                availableProjects = emptyList(),
                availableTags = emptyList(),
                allTasks = listOf(open, blocker, other),
            )
        }

        composeTestRule.onNodeWithContentDescription("AddBlockedByButton", useUnmergedTree = true)
            .performScrollTo()
            .performClick()

        // Self is excluded
        composeTestRule.onNodeWithContentDescription("TaskPickerSearch", useUnmergedTree = true)
            .performTextInput("Edit me")
        composeTestRule.onNodeWithText("No tasks found").assertExists()

        // Existing dependency is excluded
        composeTestRule.onNodeWithContentDescription("TaskPickerSearch", useUnmergedTree = true)
            .performTextReplacement("Existing blocker")
        composeTestRule.onNodeWithText("No tasks found").assertExists()

        // Unrelated task is selectable
        composeTestRule.onNodeWithContentDescription("TaskPickerSearch", useUnmergedTree = true)
            .performTextReplacement("Another task")
        composeTestRule.onNode(
            hasAnyDescendant(hasText("Another task")) and hasClickAction(),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun blockingSectionShowsDependentsAndRemoves() {
        var removed: Pair<String, String>? = null
        val open = task()
        val dependent = task(uuid = "task-2", description = "Depends on me", dependencies = listOf("task-1"))
        composeTestRule.setContent {
            TaskDetailBottomSheet(
                task = open,
                onDismiss = {},
                onSave = {},
                availableProjects = emptyList(),
                availableTags = emptyList(),
                allTasks = listOf(open, dependent),
                onRemoveDependency = { uuid, dep ->
                    removed = uuid to dep
                },
            )
        }

        composeTestRule.onNodeWithText("Blocking").performScrollTo().performClick()
        composeTestRule.onNode(
            hasText("Depends on me") and hasAnyAncestor(hasTestTag("TaskDetailBottomSheet")),
        ).performScrollTo().assertExists()

        composeTestRule.onNodeWithContentDescription("Remove dependency", useUnmergedTree = true)
            .performScrollTo()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals("task-2" to "task-1", removed)
        }
    }

    @Test
    fun blockingPickerAddsOpenTaskToPickedTask() {
        var added: Pair<String, List<String>>? = null
        val open = task()
        val candidate = task(uuid = "task-2", description = "Candidate task")
        composeTestRule.setContent {
            TaskDetailBottomSheet(
                task = open,
                onDismiss = {},
                onSave = {},
                availableProjects = emptyList(),
                availableTags = emptyList(),
                allTasks = listOf(open, candidate),
                onAddDependencies = { uuid, deps ->
                    added = uuid to deps
                },
            )
        }

        composeTestRule.onNodeWithText("Blocking").performScrollTo().performClick()
        composeTestRule.onNodeWithContentDescription("AddBlockingButton", useUnmergedTree = true)
            .performScrollTo()
            .performClick()

        composeTestRule.onNode(
            hasAnyDescendant(hasText("Candidate task")) and hasClickAction(),
            useUnmergedTree = true,
        ).performClick()
        composeTestRule.onNodeWithText("Add (1)").performClick()

        composeTestRule.runOnIdle {
            assertEquals("task-2" to listOf("task-1"), added)
        }
    }

    private fun task(
        uuid: String = "task-1",
        description: String = "Edit me",
        tags: List<String> = emptyList(),
        dependencies: List<String> = emptyList(),
    ): Task =
        Task(
            uuid = uuid,
            description = description,
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
            dependencies = dependencies,
            udas = emptyMap(),
        )
}
