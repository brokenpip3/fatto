package com.brokenpip3.fatto.data.reports

import java.time.Instant

data class TimesheetItem(val uuid: String, val description: String, val project: String?, val event: Instant)

data class TimesheetWeek(val bucket: ReportBucket, val completed: List<TimesheetItem>, val started: List<TimesheetItem>)

data class TimesheetReport(val weeks: List<TimesheetWeek>, val missingEndCount: Int, val missingStartCount: Int)

fun calculateTimesheet(
    tasks: List<ReportTask>,
    scope: ProjectScope,
    weeks: List<ReportBucket>,
    now: Instant,
): TimesheetReport {
    val selected = tasks.filter { matchesProject(it, scope) }
    val completed = selected.filter { it.status == ReportStatus.COMPLETED }
    val pending = selected.filter { it.status == ReportStatus.PENDING && (it.wait == null || it.wait <= now) }

    fun datedItems(
        records: List<ReportTask>,
        date: (ReportTask) -> Instant?,
    ): List<TimesheetItem> =
        records.mapNotNull { task ->
            date(task)?.let { TimesheetItem(task.uuid, task.description, task.project, it) }
        }.sortedWith(compareByDescending<TimesheetItem> { it.event }.thenBy { it.uuid })
    val completedItems = datedItems(completed) { it.end }
    val startedItems = datedItems(pending) { it.start }
    return TimesheetReport(
        weeks.map { bucket ->
            TimesheetWeek(bucket, completedItems.filter { it.event in bucket.range }, startedItems.filter { it.event in bucket.range })
        },
        completed.count { it.end == null },
        pending.count { it.start == null },
    )
}
