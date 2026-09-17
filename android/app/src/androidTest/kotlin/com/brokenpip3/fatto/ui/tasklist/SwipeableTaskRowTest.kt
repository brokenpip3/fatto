package com.brokenpip3.fatto.ui.tasklist

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brokenpip3.fatto.data.TaskSwipeAction
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.ui.theme.NordicTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.taskchampion_android.TaskStatus

@RunWith(AndroidJUnit4::class)
class SwipeableTaskRowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val completedTask =
        Task(
            uuid = "completed-task",
            description = "Completed task",
            status = TaskStatus.COMPLETED,
            tags = emptyList(),
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

    private val pendingTask =
        completedTask.copy(
            uuid = "pending-task",
            description = "Pending task",
            status = TaskStatus.PENDING,
        )

    private val activeTask = pendingTask.copy(start = "2026-09-16T10:00:00Z")

    @Test
    fun completedTaskWithRestoreCallbackShowsRestoreAndInvokesIt() {
        var restores = 0

        composeTestRule.setContent {
            NordicTheme {
                TaskItem(
                    task = completedTask,
                    onClick = {},
                    onComplete = {},
                    onDelete = {},
                    onRestore = { restores++ },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Restore").performClick()

        composeTestRule.runOnIdle { assertEquals(1, restores) }
    }

    @Test
    fun completedTaskWithoutRestoreCallbackDoesNotShowRestore() {
        composeTestRule.setContent {
            NordicTheme {
                TaskItem(
                    task = completedTask,
                    onClick = {},
                    onComplete = {},
                    onDelete = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Restore").assertDoesNotExist()
    }

    @Test
    fun pendingTaskRightToggleCompletesOnly() {
        var completes = 0
        var restores = 0
        var deletes = 0

        setSwipeableTaskRow(
            task = pendingTask,
            startToEndAction = TaskSwipeAction.COMPLETE,
            endToStartAction = TaskSwipeAction.NONE,
            onComplete = { completes++ },
            onDelete = { deletes++ },
        )

        swipeRowFor(pendingTask).performTouchInput { swipeRight() }

        composeTestRule.runOnIdle {
            assertEquals(1, completes)
            assertEquals(0, restores)
            assertEquals(0, deletes)
        }
    }

    @Test
    fun consecutiveRightTogglesDispatchOncePerGesture() {
        var completes = 0

        setSwipeableTaskRow(
            task = pendingTask,
            startToEndAction = TaskSwipeAction.COMPLETE,
            endToStartAction = TaskSwipeAction.NONE,
            onComplete = { completes++ },
            onDelete = {},
        )

        swipeRowFor(pendingTask).performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()
        swipeRowFor(pendingTask).performTouchInput { swipeRight() }

        composeTestRule.runOnIdle { assertEquals(2, completes) }
    }

    @Test
    fun completedTaskCompleteDoesNotRestore() {
        var completes = 0
        var restores = 0
        var deletes = 0

        setSwipeableTaskRow(
            task = completedTask,
            startToEndAction = TaskSwipeAction.COMPLETE,
            endToStartAction = TaskSwipeAction.NONE,
            onComplete = { completes++ },
            onDelete = { deletes++ },
        )

        swipeRowFor(completedTask).performTouchInput { swipeRight() }

        composeTestRule.runOnIdle {
            assertEquals(0, completes)
            assertEquals(0, restores)
            assertEquals(0, deletes)
        }
    }

    @Test
    fun completedTaskStartStopDoesNotDispatch() {
        var startStops = 0

        setSwipeableTaskRow(
            task = completedTask,
            startToEndAction = TaskSwipeAction.START_STOP,
            endToStartAction = TaskSwipeAction.NONE,
            onComplete = {},
            onDelete = {},
            onStartStop = { startStops++ },
        )

        swipeRowFor(completedTask).performTouchInput { swipeRight() }

        composeTestRule.runOnIdle { assertEquals(0, startStops) }
    }

    @Test
    fun recomposedCallbacksAreUsedForSwipe() {
        val showCompletedTask = mutableStateOf(false)
        var initialEdits = 0
        var updatedEdits = 0

        composeTestRule.setContent {
            val task = if (showCompletedTask.value) completedTask else pendingTask

            NordicTheme {
                SwipeableTaskRow(
                    task = task,
                    startToEndAction = TaskSwipeAction.EDIT,
                    endToStartAction = TaskSwipeAction.NONE,
                    onComplete = {},
                    onDelete = {},
                    onEdit = if (showCompletedTask.value) ({ updatedEdits++ }) else ({ initialEdits++ }),
                ) {
                    Text(
                        text = task.description,
                        modifier = Modifier.fillMaxWidth().height(72.dp),
                    )
                }
            }
        }

        composeTestRule.runOnIdle { showCompletedTask.value = true }
        swipeRowFor(completedTask).performTouchInput { swipeRight() }

        composeTestRule.runOnIdle {
            assertEquals(0, initialEdits)
            assertEquals(1, updatedEdits)
        }
    }

    @Test
    fun leftDeleteDeletesOnly() {
        var completes = 0
        var restores = 0
        var deletes = 0

        setSwipeableTaskRow(
            task = pendingTask,
            startToEndAction = TaskSwipeAction.NONE,
            endToStartAction = TaskSwipeAction.DELETE,
            onComplete = { completes++ },
            onDelete = { deletes++ },
        )

        swipeRowFor(pendingTask).performTouchInput { swipeLeft() }

        composeTestRule.runOnIdle {
            assertEquals(0, completes)
            assertEquals(0, restores)
            assertEquals(1, deletes)
        }
    }

    @Test
    fun disabledDirectionsDoNotRunActions() {
        var completes = 0
        var restores = 0
        var deletes = 0

        setSwipeableTaskRow(
            task = pendingTask,
            startToEndAction = TaskSwipeAction.NONE,
            endToStartAction = TaskSwipeAction.NONE,
            onComplete = { completes++ },
            onDelete = { deletes++ },
        )

        swipeRowFor(pendingTask).performTouchInput { swipeRight() }
        swipeRowFor(pendingTask).performTouchInput { swipeLeft() }

        composeTestRule.runOnIdle {
            assertEquals(0, completes)
            assertEquals(0, restores)
            assertEquals(0, deletes)
        }
    }

    @Test
    fun actionLabelsDescribeTheAction() {
        assertEquals("Complete", swipeActionLabel(TaskSwipeAction.COMPLETE))
        assertEquals("Edit", swipeActionLabel(TaskSwipeAction.EDIT))
        assertEquals("Start", swipeActionLabel(TaskSwipeAction.START_STOP, isActive = false))
        assertEquals("Stop", swipeActionLabel(TaskSwipeAction.START_STOP, isActive = true))
        assertEquals("Delete", swipeActionLabel(TaskSwipeAction.DELETE))
    }

    @Test
    fun editAndStartStopActionsDispatchTheirCallbacks() {
        var edits = 0
        var starts = 0

        setSwipeableTaskRow(
            task = pendingTask,
            startToEndAction = TaskSwipeAction.EDIT,
            endToStartAction = TaskSwipeAction.START_STOP,
            onComplete = {},
            onDelete = {},
            onEdit = { edits++ },
            onStartStop = { starts++ },
        )

        swipeRowFor(pendingTask).performTouchInput { swipeRight() }
        swipeRowFor(pendingTask).performTouchInput { swipeLeft() }

        composeTestRule.runOnIdle {
            assertEquals(1, edits)
            assertEquals(1, starts)
        }
    }

    @Test
    fun pendingToggleRendersCompleteDuringSwipe() {
        assertActionLabelDuringSwipe(
            task = pendingTask,
            startToEndAction = TaskSwipeAction.COMPLETE,
            endToStartAction = TaskSwipeAction.NONE,
            swipe = { swipeRight() },
            label = "Complete",
        )
    }

    @Test
    fun completedCompleteRendersCompleteDuringSwipe() {
        assertActionLabelDuringSwipe(
            task = completedTask,
            startToEndAction = TaskSwipeAction.COMPLETE,
            endToStartAction = TaskSwipeAction.NONE,
            swipe = { swipeRight() },
            label = "Complete",
        )
    }

    @Test
    fun deleteRendersDeleteDuringSwipe() {
        assertActionLabelDuringSwipe(
            task = pendingTask,
            startToEndAction = TaskSwipeAction.NONE,
            endToStartAction = TaskSwipeAction.DELETE,
            swipe = { swipeLeft() },
            label = "Delete",
        )
    }

    @Test
    fun activeStartStopRendersStopDuringSwipe() {
        assertActionLabelDuringSwipe(
            task = activeTask,
            startToEndAction = TaskSwipeAction.START_STOP,
            endToStartAction = TaskSwipeAction.NONE,
            swipe = { swipeRight() },
            label = "Stop",
        )
    }

    private fun setSwipeableTaskRow(
        task: Task,
        startToEndAction: TaskSwipeAction,
        endToStartAction: TaskSwipeAction,
        onComplete: () -> Unit,
        onDelete: () -> Unit,
        onEdit: () -> Unit = {},
        onStartStop: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            NordicTheme {
                SwipeableTaskRow(
                    task = task,
                    startToEndAction = startToEndAction,
                    endToStartAction = endToStartAction,
                    onComplete = onComplete,
                    onDelete = onDelete,
                    onEdit = onEdit,
                    onStartStop = onStartStop,
                ) {
                    Text(
                        text = task.description,
                        modifier = Modifier.fillMaxWidth().height(72.dp),
                    )
                }
            }
        }
    }

    private fun swipeRowFor(task: Task) =
        composeTestRule.onNode(
            hasTestTag("SwipeableTaskRow") and hasAnyDescendant(hasText(task.description)),
        )

    private fun assertActionLabelDuringSwipe(
        task: Task,
        startToEndAction: TaskSwipeAction,
        endToStartAction: TaskSwipeAction,
        swipe: androidx.compose.ui.test.TouchInjectionScope.() -> Unit,
        label: String,
    ) {
        composeTestRule.mainClock.autoAdvance = false
        try {
            setSwipeableTaskRow(
                task = task,
                startToEndAction = startToEndAction,
                endToStartAction = endToStartAction,
                onComplete = {},
                onDelete = {},
            )

            swipeRowFor(task).performTouchInput(swipe)

            composeTestRule.onNodeWithText(label).assertIsDisplayed()
        } finally {
            composeTestRule.mainClock.autoAdvance = true
        }
    }
}
