package com.brokenpip3.fatto.data

import com.brokenpip3.fatto.data.model.Task
import uniffi.taskchampion_android.TaskStatus

/**
 * Pure selection logic for the home-screen widget: pending tasks that have a
 * parseable due date, ordered by due date ascending (overdue naturally first),
 * limited to [limit] items. Stable — tasks with equal due dates keep input order.
 */
object NextTasksSelector {
    fun nextTasks(
        tasks: List<Task>,
        limit: Int,
    ): List<Task> =
        tasks
            .filter { it.status == TaskStatus.PENDING }
            .mapNotNull { task -> DateTimeUtils.parseToInstant(task.due)?.let { task to it } }
            .sortedBy { (_, due) -> due }
            .take(limit)
            .map { (task, _) -> task }
}
