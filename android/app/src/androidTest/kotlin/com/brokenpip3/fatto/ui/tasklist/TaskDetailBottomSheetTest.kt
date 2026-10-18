package com.brokenpip3.fatto.ui.tasklist

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
    fun unchangedRawDateConfirmationDoesNotRepairTheNativeValue() {
        val original = task().copy(nativeDates = mapOf("due" to "2030-01-02T00:00:00Z"))
        var saved: uniffi.taskchampion_android.TaskEditPatch? = null
        composeTestRule.setContent {
            TaskDetailBottomSheet(original, onDismiss = {}, onSave = {
                saved = it
                com.brokenpip3.fatto.data.model.TaskEditResult.Success
            }, availableProjects = emptyList(), availableTags = emptyList())
        }
        composeTestRule.onNode(hasContentDescription("Due") and hasClickAction()).performScrollTo().performClick()
        composeTestRule.onNodeWithText("OK").performClick()
        composeTestRule.onNodeWithTag("CloseButton", useUnmergedTree = true).performScrollTo().performClick()
        composeTestRule.runOnIdle {
            assertEquals(uniffi.taskchampion_android.StringEdit.Unchanged, saved?.due)
            assertEquals(uniffi.taskchampion_android.DateModeEdit.Unchanged, saved?.dateModes?.due)
        }
    }

    @Test
    fun malformedDateClearsSurviveReopenCancelAndFinalSave() {
        var saved: uniffi.taskchampion_android.TaskEditPatch? = null
        val original = task().copy(nativeDates = mapOf("due" to "bad-due", "scheduled" to "bad-scheduled", "wait" to "bad-wait"))
        composeTestRule.setContent {
            TaskDetailBottomSheet(original, onDismiss = {}, onSave = {
                saved = it
                com.brokenpip3.fatto.data.model.TaskEditResult.Success
            }, availableProjects = emptyList(), availableTags = emptyList())
        }
        for (label in listOf("Due", "Sch", "Wait")) {
            composeTestRule.onNode(hasContentDescription(label) and hasClickAction()).performScrollTo().performClick()
            composeTestRule.onNodeWithText("Clear").performClick()
            composeTestRule.onNode(hasContentDescription(label) and hasClickAction()).performScrollTo().performClick()
            composeTestRule.onNodeWithText("Cancel").performClick()
        }
        composeTestRule.onNodeWithTag("CloseButton", useUnmergedTree = true).performScrollTo().performClick()
        composeTestRule.runOnIdle {
            assertEquals(uniffi.taskchampion_android.StringEdit.Replace("bad-due", null), saved?.due)
            assertEquals(uniffi.taskchampion_android.StringEdit.Replace("bad-scheduled", null), saved?.scheduled)
            assertEquals(uniffi.taskchampion_android.StringEdit.Replace("bad-wait", null), saved?.wait)
        }
    }

    @Test
    fun tagInputShowsAvailableTagSuggestions() {
        composeTestRule.setContent {
            TaskDetailBottomSheet(
                task = task(tags = listOf("home")),
                onDismiss = {},
                onSave = { com.brokenpip3.fatto.data.model.TaskEditResult.Success },
                availableProjects = emptyList(),
                availableTags = listOf("urgent", "work", "home"),
            )
        }

        composeTestRule.onNode(
            hasTestTag("TagInput") and hasAnyAncestor(hasTestTag("TaskDetailBottomSheet")),
            useUnmergedTree = true,
        ).performTextInput("ur")

        composeTestRule.onNode(
            hasText("urgent") and hasAnyAncestor(hasTestTag("TaskDetailBottomSheet")),
        ).assertExists()
    }

    @Test
    fun tagPickerKeepsHiddenInternalTagsWhenReplacingVisibleTags() {
        var savedTask: uniffi.taskchampion_android.TaskEditPatch? = null
        composeTestRule.setContent {
            TaskDetailBottomSheet(
                task = task(tags = listOf("home", "BLOCKING")),
                onDismiss = {},
                onSave = {
                    savedTask = it
                    com.brokenpip3.fatto.data.model.TaskEditResult.Success
                },
                availableProjects = emptyList(),
                availableTags = listOf("home", "urgent"),
                showInternalTags = false,
            )
        }

        composeTestRule.onNodeWithContentDescription("Select tags", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("TagPickerOption-home").performClick()
        composeTestRule.onNodeWithTag("TagPickerOption-urgent").performClick()
        composeTestRule.onNodeWithTag("TagPickerConfirmButton").performClick()
        composeTestRule.onNodeWithTag("CloseButton", useUnmergedTree = true)
            .performScrollTo()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                setOf("BLOCKING", "urgent"),
                (savedTask?.tags as? uniffi.taskchampion_android.StringSetEdit.Replace)?.value?.toSet(),
            )
        }
    }

    @Test
    fun tagPickerKeepsHiddenInternalTagsWhenClearingVisibleTags() {
        var savedTask: uniffi.taskchampion_android.TaskEditPatch? = null
        composeTestRule.setContent {
            TaskDetailBottomSheet(
                task = task(tags = listOf("home", "BLOCKING")),
                onDismiss = {},
                onSave = {
                    savedTask = it
                    com.brokenpip3.fatto.data.model.TaskEditResult.Success
                },
                availableProjects = emptyList(),
                availableTags = listOf("home", "urgent"),
                showInternalTags = false,
            )
        }

        composeTestRule.onNodeWithContentDescription("Select tags", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("TagPickerOption-home").performClick()
        composeTestRule.onNodeWithTag("TagPickerConfirmButton").performClick()
        composeTestRule.onNodeWithTag("CloseButton", useUnmergedTree = true)
            .performScrollTo()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(setOf("BLOCKING"), (savedTask?.tags as? uniffi.taskchampion_android.StringSetEdit.Replace)?.value?.toSet())
        }
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
                onSave = { com.brokenpip3.fatto.data.model.TaskEditResult.Success },
                availableProjects = emptyList(),
                availableTags = emptyList(),
                allTasks = listOf(open, blocker),
                onAddDependencies = { uuid, deps ->
                    addedUuid = uuid
                    addedDeps = deps
                },
            )
        }

        composeTestRule.onNodeWithText("Blocked by").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("AddBlockedByButton", useUnmergedTree = true)
            .performScrollTo()
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
                onSave = { com.brokenpip3.fatto.data.model.TaskEditResult.Success },
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
                onSave = { com.brokenpip3.fatto.data.model.TaskEditResult.Success },
                availableProjects = emptyList(),
                availableTags = emptyList(),
                allTasks = listOf(open, blocker, other),
            )
        }

        composeTestRule.onNodeWithTag("AddBlockedByButton", useUnmergedTree = true)
            .performScrollTo()
            .performClick()

        // Self is excluded
        composeTestRule.onNodeWithTag("TaskPickerSearch", useUnmergedTree = true)
            .performTextInput("Edit me")
        composeTestRule.onNodeWithText("No tasks found").assertExists()

        // Existing dependency is excluded
        composeTestRule.onNodeWithTag("TaskPickerSearch", useUnmergedTree = true)
            .performTextReplacement("Existing blocker")
        composeTestRule.onNodeWithText("No tasks found").assertExists()

        // Unrelated task is selectable
        composeTestRule.onNodeWithTag("TaskPickerSearch", useUnmergedTree = true)
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
                onSave = { com.brokenpip3.fatto.data.model.TaskEditResult.Success },
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
    fun blockedByPickerExcludesCompletedWhenShowCompletedIsFalse() {
        val open = task()
        val completed = task(uuid = "task-2", description = "Done task", status = TaskStatus.Completed)
        val other = task(uuid = "task-3", description = "Open task")
        composeTestRule.setContent {
            TaskDetailBottomSheet(
                task = open,
                onDismiss = {},
                onSave = { com.brokenpip3.fatto.data.model.TaskEditResult.Success },
                availableProjects = emptyList(),
                availableTags = emptyList(),
                allTasks = listOf(open, completed, other),
                showCompleted = false,
            )
        }

        composeTestRule.onNodeWithText("Blocked by").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("AddBlockedByButton", useUnmergedTree = true)
            .performScrollTo()
            .performClick()

        composeTestRule.onNodeWithTag("TaskPickerSearch", useUnmergedTree = true)
            .performTextInput("Done task")
        composeTestRule.onNodeWithText("No tasks found").assertExists()

        composeTestRule.onNodeWithTag("TaskPickerSearch", useUnmergedTree = true)
            .performTextReplacement("Open task")
        composeTestRule.onNode(
            hasAnyDescendant(hasText("Open task")) and hasClickAction(),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun blockedByPickerShowsCompletedWhenShowCompletedIsTrue() {
        val open = task()
        val completed = task(uuid = "task-2", description = "Done task", status = TaskStatus.Completed)
        composeTestRule.setContent {
            TaskDetailBottomSheet(
                task = open,
                onDismiss = {},
                onSave = { com.brokenpip3.fatto.data.model.TaskEditResult.Success },
                availableProjects = emptyList(),
                availableTags = emptyList(),
                allTasks = listOf(open, completed),
                showCompleted = true,
            )
        }

        composeTestRule.onNodeWithText("Blocked by").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("AddBlockedByButton", useUnmergedTree = true)
            .performScrollTo()
            .performClick()

        composeTestRule.onNode(
            hasAnyDescendant(hasText("Done task")) and hasClickAction(),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun blockingPickerExcludesCompletedWhenShowCompletedIsFalse() {
        val open = task()
        val completed = task(uuid = "task-2", description = "Done task", status = TaskStatus.Completed)
        val other = task(uuid = "task-3", description = "Open task")
        composeTestRule.setContent {
            TaskDetailBottomSheet(
                task = open,
                onDismiss = {},
                onSave = { com.brokenpip3.fatto.data.model.TaskEditResult.Success },
                availableProjects = emptyList(),
                availableTags = emptyList(),
                allTasks = listOf(open, completed, other),
                showCompleted = false,
            )
        }

        composeTestRule.onNodeWithText("Blocking").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("AddBlockingButton", useUnmergedTree = true)
            .performScrollTo()
            .performClick()

        composeTestRule.onNodeWithTag("TaskPickerSearch", useUnmergedTree = true)
            .performTextInput("Done task")
        composeTestRule.onNodeWithText("No tasks found").assertExists()

        composeTestRule.onNodeWithTag("TaskPickerSearch", useUnmergedTree = true)
            .performTextReplacement("Open task")
        composeTestRule.onNode(
            hasAnyDescendant(hasText("Open task")) and hasClickAction(),
            useUnmergedTree = true,
        ).assertExists()
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
                onSave = { com.brokenpip3.fatto.data.model.TaskEditResult.Success },
                availableProjects = emptyList(),
                availableTags = emptyList(),
                allTasks = listOf(open, candidate),
                onAddDependencies = { uuid, deps ->
                    added = uuid to deps
                },
            )
        }

        composeTestRule.onNodeWithText("Blocking").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("AddBlockingButton", useUnmergedTree = true)
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
        status: TaskStatus = TaskStatus.Pending,
    ): Task =
        Task(
            uuid = uuid,
            description = description,
            status = status,
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
