package com.brokenpip3.fatto.data.reports

import java.time.Instant

data class StatsReport(
    val total: Int,
    val pending: Int,
    val completed: Int,
    val deleted: Int,
    val waiting: Int,
    val active: Int,
    val projects: Int,
    val uniqueTags: Int,
    val annotations: Int,
    val taggedTasks: Int,
    val taggedPercent: Double,
    val oldestEntry: Instant?,
    val newestEntry: Instant?,
    val averageDescriptionCodePoints: Double,
    val projectTasks: Int,
    val projectPercent: Double,
)

fun calculateStats(
    tasks: List<ReportTask>,
    scope: ProjectScope,
    now: Instant,
): StatsReport {
    val selected = tasks.filter { matchesProject(it, scope) }
    val pending = selected.filter { it.status == ReportStatus.PENDING }
    val tagged = selected.count { it.userTags.isNotEmpty() }
    val assigned = selected.count { it.project != null }
    return StatsReport(
        selected.size, pending.size, selected.count { it.status == ReportStatus.COMPLETED },
        selected.count {
            it.status ==
                ReportStatus.DELETED
        },
        pending.count { it.wait != null && it.wait > now }, pending.count { it.start != null },
        selected.mapNotNull { it.project }.toSet().size, selected.flatMap { it.userTags }.toSet().size,
        selected.sumOf { it.annotationCount }, tagged, if (selected.isEmpty()) 0.0 else tagged * 100.0 / selected.size,
        selected.mapNotNull { it.entry }.minOrNull(), selected.mapNotNull { it.entry }.maxOrNull(),
        if (selected.isEmpty()) {
            0.0
        } else {
            selected.sumOf { it.description.codePointCount(0, it.description.length).toLong() }.toDouble() /
                selected.size
        },
        assigned, if (selected.isEmpty()) 0.0 else assigned * 100.0 / selected.size,
    )
}
