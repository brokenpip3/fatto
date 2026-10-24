package com.brokenpip3.fatto.data

import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.dateValue
import uniffi.taskchampion_android.TaskStatus
import java.time.Instant
import java.time.ZoneId

/** Daily summaries retain calendar-day membership even for explicitly timed fields. */
object DailyTaskSelector {
    fun select(
        tasks: List<Task>,
        includeDue: Boolean,
        includeScheduled: Boolean,
        includeOverdue: Boolean,
        now: Instant,
        zone: ZoneId,
    ): List<Task> =
        tasks.filter { task ->
            task.status == TaskStatus.Pending &&
                task.isActionableStatus &&
                (
                    (includeDue && TaskDateTime.isToday(task.dateValue("due"), now, zone)) ||
                        (includeScheduled && TaskDateTime.isToday(task.dateValue("scheduled"), now, zone)) ||
                        (includeOverdue && TaskDateTime.isOverdue(task.dateValue("due"), now, zone))
                )
        }
}
