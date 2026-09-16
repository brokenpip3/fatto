package com.brokenpip3.fatto.ui.tasklist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.brokenpip3.fatto.data.TaskSwipeAction
import com.brokenpip3.fatto.data.model.Task
import uniffi.taskchampion_android.TaskStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeableTaskRow(
    task: Task,
    startToEndAction: TaskSwipeAction,
    endToStartAction: TaskSwipeAction,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit = {},
    onStartStop: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    var actionDispatched by remember { mutableStateOf(false) }
    val currentTask by rememberUpdatedState(task)
    val currentStartToEndAction by rememberUpdatedState(startToEndAction)
    val currentEndToStartAction by rememberUpdatedState(endToStartAction)
    val currentOnComplete by rememberUpdatedState(onComplete)
    val currentOnDelete by rememberUpdatedState(onDelete)
    val currentOnEdit by rememberUpdatedState(onEdit)
    val currentOnStartStop by rememberUpdatedState(onStartStop)

    fun runAction(action: TaskSwipeAction) {
        when (action) {
            TaskSwipeAction.NONE -> Unit
            TaskSwipeAction.DELETE -> currentOnDelete()
            TaskSwipeAction.COMPLETE ->
                if (currentTask.status != TaskStatus.COMPLETED) currentOnComplete()
            TaskSwipeAction.EDIT -> currentOnEdit()
            TaskSwipeAction.START_STOP ->
                if (currentTask.status != TaskStatus.COMPLETED) currentOnStartStop()
        }
    }

    val state =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { target ->
                val action =
                    when (target) {
                        SwipeToDismissBoxValue.StartToEnd -> currentStartToEndAction
                        SwipeToDismissBoxValue.EndToStart -> currentEndToStartAction
                        SwipeToDismissBoxValue.Settled -> TaskSwipeAction.NONE
                    }
                if (action != TaskSwipeAction.NONE && !actionDispatched) {
                    actionDispatched = true
                    runAction(action)
                }
                false
            },
        )

    LaunchedEffect(state.dismissDirection) {
        if (state.dismissDirection == SwipeToDismissBoxValue.Settled) {
            actionDispatched = false
        }
    }

    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val action =
                when (state.dismissDirection) {
                    SwipeToDismissBoxValue.StartToEnd -> startToEndAction
                    SwipeToDismissBoxValue.EndToStart -> endToStartAction
                    SwipeToDismissBoxValue.Settled -> TaskSwipeAction.NONE
                }
            SwipeActionBackground(
                action = action,
                isActive = task.start != null,
                direction = state.dismissDirection,
            )
        },
        enableDismissFromStartToEnd = startToEndAction != TaskSwipeAction.NONE,
        enableDismissFromEndToStart = endToStartAction != TaskSwipeAction.NONE,
        modifier = Modifier.testTag("SwipeableTaskRow"),
        content = { content() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeActionBackground(
    action: TaskSwipeAction,
    isActive: Boolean,
    direction: SwipeToDismissBoxValue,
) {
    val alignment =
        if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
    val horizontalPadding =
        if (direction == SwipeToDismissBoxValue.StartToEnd) {
            Modifier.padding(start = 24.dp)
        } else {
            Modifier.padding(end = 24.dp)
        }
    val icon =
        when (action) {
            TaskSwipeAction.NONE -> null
            TaskSwipeAction.DELETE -> Icons.Default.Delete
            TaskSwipeAction.COMPLETE -> Icons.Default.Check
            TaskSwipeAction.EDIT -> Icons.Default.Edit
            TaskSwipeAction.START_STOP -> if (isActive) Icons.Default.Stop else Icons.Default.PlayArrow
        }
    val color =
        when (action) {
            TaskSwipeAction.NONE -> Color.Transparent
            TaskSwipeAction.DELETE -> MaterialTheme.colorScheme.errorContainer
            TaskSwipeAction.COMPLETE,
            TaskSwipeAction.EDIT,
            TaskSwipeAction.START_STOP,
            -> MaterialTheme.colorScheme.primaryContainer
        }

    Box(
        modifier = Modifier.fillMaxSize().background(color).then(horizontalPadding),
        contentAlignment = alignment,
    ) {
        if (icon != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                )
                Text(
                    text =
                        swipeActionLabel(
                            action = action,
                            isActive = isActive,
                        ),
                )
            }
        }
    }
}

internal fun swipeActionLabel(
    action: TaskSwipeAction,
    isActive: Boolean = false,
): String =
    when (action) {
        TaskSwipeAction.NONE -> ""
        TaskSwipeAction.DELETE -> "Delete"
        TaskSwipeAction.COMPLETE -> "Complete"
        TaskSwipeAction.EDIT -> "Edit"
        TaskSwipeAction.START_STOP -> if (isActive) "Stop" else "Start"
    }
