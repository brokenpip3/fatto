package com.brokenpip3.fatto.data.reports

import java.time.Instant
import java.time.LocalDate

enum class ReportStatus { PENDING, COMPLETED, DELETED }

data class ReportTask(
    val uuid: String,
    val description: String,
    val status: ReportStatus,
    val project: String?,
    val userTags: Set<String>,
    val annotationCount: Int,
    val entry: Instant?,
    val start: Instant?,
    val end: Instant?,
    val wait: Instant?,
)

sealed interface ProjectScope {
    data object All : ProjectScope

    data object Unassigned : ProjectScope

    data class Named(val name: String) : ProjectScope
}

fun matchesProject(
    task: ReportTask,
    scope: ProjectScope,
): Boolean =
    when (scope) {
        ProjectScope.All -> true
        ProjectScope.Unassigned -> task.project == null
        is ProjectScope.Named -> task.project == scope.name || task.project?.startsWith("${scope.name}.") == true
    }

data class ReportRange(val start: Instant, val endExclusive: Instant) {
    init {
        require(start < endExclusive)
    }

    operator fun contains(instant: Instant): Boolean = instant >= start && instant < endExclusive
}

data class ReportBucket(val range: ReportRange, val startDate: LocalDate, val endDateExclusive: LocalDate)

enum class ReportGrouping { DAY, WEEK, MONTH, YEAR }
