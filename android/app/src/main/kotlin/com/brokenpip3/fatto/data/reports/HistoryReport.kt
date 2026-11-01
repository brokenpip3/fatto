package com.brokenpip3.fatto.data.reports

data class EventCounts(val added: Int = 0, val completed: Int = 0, val deleted: Int = 0) {
    val net: Int get() = added - completed - deleted
}

data class HistoryRow(val bucket: ReportBucket, val counts: EventCounts)

data class HistoryReport(val rows: List<HistoryRow>, val totals: EventCounts, val missingEntryCount: Int, val missingEndCount: Int)

fun calculateHistory(
    tasks: List<ReportTask>,
    scope: ProjectScope,
    buckets: List<ReportBucket>,
): HistoryReport {
    val counts = Array(buckets.size) { IntArray(3) }
    val selected = tasks.filter { matchesProject(it, scope) }
    val missingEntry = selected.count { it.entry == null }
    val missingEnd = selected.count { it.status != ReportStatus.PENDING && it.end == null }

    fun recordEvent(
        event: java.time.Instant?,
        column: Int,
    ) {
        if (event == null) return
        val index = buckets.indexOfFirst { event in it.range }
        if (index >= 0) counts[index][column]++
    }
    for (task in selected) {
        recordEvent(task.entry, 0)
        when (task.status) {
            ReportStatus.COMPLETED -> recordEvent(task.end, 1)
            ReportStatus.DELETED -> recordEvent(task.end, 2)
            ReportStatus.PENDING -> Unit
        }
    }
    val rows = buckets.mapIndexed { i, bucket -> HistoryRow(bucket, EventCounts(counts[i][0], counts[i][1], counts[i][2])) }
    return HistoryReport(
        rows,
        EventCounts(
            rows.sumOf {
                it.counts.added
            },
            rows.sumOf { it.counts.completed }, rows.sumOf { it.counts.deleted },
        ),
        missingEntry, missingEnd,
    )
}
