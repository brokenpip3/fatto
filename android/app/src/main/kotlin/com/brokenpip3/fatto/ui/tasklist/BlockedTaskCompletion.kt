package com.brokenpip3.fatto.ui.tasklist

import com.brokenpip3.fatto.data.model.Task
import uniffi.taskchampion_android.TaskStatus

private const val GENERIC_COMPLETION_CONFIRMATION =
    "Are you sure you want to mark this task as completed?"

fun unresolvedDependencyUuids(
    task: Task,
    tasks: List<Task>,
): List<String> =
    if (!task.isBlocked) {
        emptyList()
    } else {
        task.dependencies.filter { dependencyUuid ->
            tasks.any { it.uuid == dependencyUuid && it.status == TaskStatus.PENDING }
        }.distinct()
    }

fun completionConfirmationMessage(
    task: Task,
    tasks: List<Task>,
): String {
    if (!task.isBlocked) return GENERIC_COMPLETION_CONFIRMATION
    val count = unresolvedDependencyUuids(task, tasks).size
    val dependencyText = if (count == 0) "one or more unfinished tasks" else "$count tasks"
    return "This task is currently blocked by $dependencyText. Are you sure you want to mark it as completed?"
}
