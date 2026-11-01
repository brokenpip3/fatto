package com.brokenpip3.fatto.data.reports

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

fun monthRange(
    month: YearMonth,
    zone: ZoneId,
): ReportRange =
    ReportRange(
        month.atDay(1).atStartOfDay(zone).toInstant(), month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant(),
    )

fun timesheetRange(
    today: LocalDate,
    firstDay: DayOfWeek,
    windowOffset: Int,
    zone: ZoneId,
): ReportRange {
    require(windowOffset <= 0)
    val end = today.with(TemporalAdjusters.previousOrSame(firstDay)).plusWeeks(1 + windowOffset.toLong() * 4)
    return ReportRange(end.minusWeeks(4).atStartOfDay(zone).toInstant(), end.atStartOfDay(zone).toInstant())
}

fun reportBuckets(
    range: ReportRange,
    grouping: ReportGrouping,
    zone: ZoneId,
    firstDay: DayOfWeek,
): List<ReportBucket> {
    val result = mutableListOf<ReportBucket>()
    var cursor = range.start
    while (cursor < range.endExclusive) {
        val date = cursor.atZone(zone).toLocalDate()
        val nextDate =
            when (grouping) {
                ReportGrouping.DAY -> date.plusDays(1)
                ReportGrouping.WEEK -> date.with(TemporalAdjusters.next(firstDay))
                ReportGrouping.MONTH -> date.withDayOfMonth(1).plusMonths(1)
                ReportGrouping.YEAR -> date.withDayOfYear(1).plusYears(1)
            }
        val next = minOf(nextDate.atStartOfDay(zone).toInstant(), range.endExclusive)
        require(next > cursor) { "Calendar bucket did not advance" }
        result += ReportBucket(ReportRange(cursor, next), date, next.atZone(zone).toLocalDate())
        cursor = next
    }
    return result
}

/** Annual comparison includes retained dated events through the current calendar year. */
fun annualHistoryRange(
    tasks: List<ReportTask>,
    scope: ProjectScope,
    today: LocalDate,
    zone: ZoneId,
): ReportRange {
    val oldest =
        tasks.asSequence().filter { matchesProject(it, scope) }
            .flatMap { task -> sequenceOf(task.entry, if (task.status == ReportStatus.PENDING) null else task.end) }
            .filterNotNull().minOrNull()
    val firstYear = minOf(oldest?.atZone(zone)?.year ?: today.year, today.year)
    return ReportRange(
        LocalDate.of(firstYear, 1, 1).atStartOfDay(zone).toInstant(),
        today.withDayOfYear(1).plusYears(1).atStartOfDay(zone).toInstant(),
    )
}
