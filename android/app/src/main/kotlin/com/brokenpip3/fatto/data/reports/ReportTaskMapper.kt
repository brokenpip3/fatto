package com.brokenpip3.fatto.data.reports

import com.brokenpip3.fatto.data.model.INTERNAL_TAGS
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.isSynthetic
import uniffi.taskchampion_android.TaskStatus
import java.time.Instant

/** Projection only: report dates are instants and never inherit editor date-display modes. */
fun mapReportTasks(tasks: List<Task>): List<ReportTask> =
    tasks.mapNotNull { task ->
        if (task.isRecurrenceTemplate) return@mapNotNull null
        val status =
            when (task.status) {
                TaskStatus.Pending -> ReportStatus.PENDING
                TaskStatus.Completed -> ReportStatus.COMPLETED
                TaskStatus.Deleted -> ReportStatus.DELETED
                else -> return@mapNotNull null
            }

        fun parse(raw: String?): Instant? = raw?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ReportTask(
            task.uuid, task.description, status, task.project?.trim()?.takeIf { it.isNotEmpty() },
            task.tags.filterNot { isSynthetic(it) || it.uppercase() in INTERNAL_TAGS }.toSet(),
            task.annotations.size, parse(task.entry), parse(task.start), parse(task.end), parse(task.wait),
        )
    }
