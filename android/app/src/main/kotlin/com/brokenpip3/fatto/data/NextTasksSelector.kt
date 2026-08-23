package com.brokenpip3.fatto.data

import com.brokenpip3.fatto.data.model.Task
import uniffi.taskchampion_android.TaskStatus
import java.time.Instant

/**
 * Pure selection logic for the home-screen widget: pending tasks that have a
 * parseable due date, ordered by due date ascending (overdue naturally first),
 * limited to [limit] items. Stable — tasks with equal due dates keep input order.
 * Tasks whose `wait` date is still in the future (waiting tasks) are excluded;
 * future `scheduled` dates are kept, so scheduled tasks show up with their due
 * date like any other task.
 */
object NextTasksSelector {
    fun nextTasks(
        tasks: List<Task>,
        limit: Int,
    ): List<Task> =
        tasks
            .filter { it.status == TaskStatus.PENDING }
            .filterNot { isInFuture(it.wait) }
            .mapNotNull { task -> DateTimeUtils.parseToInstant(task.due)?.let { task to it } }
            .sortedBy { (_, due) -> due }
            .take(limit)
            .map { (task, _) -> task }

    private fun isInFuture(dateStr: String?): Boolean = DateTimeUtils.parseToInstant(dateStr)?.isAfter(Instant.now()) == true
}
