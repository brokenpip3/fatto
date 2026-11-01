package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.reports.EventCounts
import com.brokenpip3.fatto.data.reports.ProjectScope
import com.brokenpip3.fatto.data.reports.ReportGrouping
import com.brokenpip3.fatto.data.reports.ReportStatus
import com.brokenpip3.fatto.data.reports.ReportTask
import com.brokenpip3.fatto.data.reports.calculateHistory
import com.brokenpip3.fatto.data.reports.calculateStats
import com.brokenpip3.fatto.data.reports.calculateTimesheet
import com.brokenpip3.fatto.data.reports.monthRange
import com.brokenpip3.fatto.data.reports.reportBuckets
import com.brokenpip3.fatto.data.reports.timesheetRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class ReportCalculationsTest {
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-10-23T12:00:00Z")

    private fun task(
        id: String,
        status: ReportStatus = ReportStatus.PENDING,
        entry: String? = "2026-10-01T00:00:00Z",
        end: String? = null,
        start: String? = null,
        project: String? = "Work",
        wait: String? = null,
        description: String = "A😀",
        tags: Set<String> = emptySet(),
    ) = ReportTask(
        id,
        description,
        status,
        project,
        tags,
        1,
        entry?.let(Instant::parse),
        start?.let(Instant::parse),
        end?.let(Instant::parse),
        wait?.let(Instant::parse),
    )

    private fun buckets(month: Int = 10) =
        reportBuckets(monthRange(YearMonth.of(2026, month), zone), ReportGrouping.WEEK, zone, DayOfWeek.MONDAY)

    @Test fun historyCountsEventsIndependentlyAndKeepsEmptyBuckets() {
        val tasks =
            listOf(
                task("a"),
                task(
                    "b",
                    ReportStatus.COMPLETED,
                    "2026-09-30T00:00:00Z",
                    "2026-10-02T00:00:00Z",
                ),
                task(
                    "c",
                    ReportStatus.DELETED,
                    "2026-10-03T00:00:00Z",
                    "2026-10-04T00:00:00Z",
                ),
            )
        val result = calculateHistory(tasks, ProjectScope.All, buckets())
        assertEquals(EventCounts(2, 1, 1), result.totals)
        assertEquals(0, result.totals.net)
        assertEquals(5, result.rows.size)
        assertEquals(EventCounts(0, 0, 0), result.rows.last().counts)
        assertEquals(EventCounts(1, 0, 0), calculateHistory(tasks, ProjectScope.All, buckets(9)).totals)
    }

    @Test fun restoredAndDeletedTasksDoNotKeepPriorCompletions() {
        val r =
            calculateHistory(
                listOf(
                    task(
                        "a",
                        end = "2026-10-02T00:00:00Z",
                    ),
                    task(
                        "b",
                        ReportStatus.DELETED,
                        end = "2026-10-03T00:00:00Z",
                    ),
                ),
                ProjectScope.All,
                buckets(),
            )
        assertEquals(EventCounts(2, 0, 1), r.totals)
    }

    @Test fun missingDatesSkipOnlyTheirContribution() {
        val r =
            calculateHistory(
                listOf(
                    task(
                        "a",
                        ReportStatus.COMPLETED,
                        entry = null,
                        end = "2026-10-02T00:00:00Z",
                    ),
                    task(
                        "b",
                        ReportStatus.DELETED,
                    ),
                ),
                ProjectScope.All,
                buckets(),
            )
        assertEquals(EventCounts(1, 1, 0), r.totals)
        assertEquals(1, r.missingEntryCount)
        assertEquals(1, r.missingEndCount)
    }

    @Test fun timesheetUsesCurrentStatusAndWaitBoundaryWithStableTies() {
        val tasks =
            listOf(
                task(
                    "b",
                    start = "2026-10-22T10:00:00Z",
                ),
                task(
                    "a",
                    start = "2026-10-22T10:00:00Z",
                    wait = now.toString(),
                ),
                task(
                    "waiting",
                    start = "2026-10-22T10:00:00Z",
                    wait = "2026-10-24T00:00:00Z",
                ),
                task(
                    "done",
                    ReportStatus.COMPLETED,
                    end = "2026-10-22T00:00:00Z",
                    start = "2026-10-22T10:00:00Z",
                ),
                task("stopped"),
            )
        val range = timesheetRange(LocalDate.of(2026, 10, 23), DayOfWeek.MONDAY, 0, zone)
        val r = calculateTimesheet(tasks, ProjectScope.All, reportBuckets(range, ReportGrouping.WEEK, zone, DayOfWeek.MONDAY), now)
        assertEquals(4, r.weeks.size)
        assertEquals(listOf("a", "b"), r.weeks.last().started.map { it.uuid })
        assertEquals(listOf("done"), r.weeks.last().completed.map { it.uuid })
        assertEquals(1, r.missingStartCount)
    }

    @Test fun projectFilterUsesHierarchyBoundary() {
        val tasks = listOf(task("a"), task("b", project = "Work.Android"), task("c", project = "Workshop"), task("d", project = null))
        assertEquals(2, calculateStats(tasks, ProjectScope.Named("Work"), now).total)
        assertEquals(1, calculateStats(tasks, ProjectScope.Unassigned, now).total)
    }

    @Test fun statsUseExclusiveStatusesSubsetsAndUnicodeCodePoints() {
        val r =
            calculateStats(
                listOf(
                    task(
                        "a",
                        start = now.toString(),
                        wait = "2026-10-24T00:00:00Z",
                        tags = setOf("android"),
                    ),
                    task(
                        "b",
                        ReportStatus.COMPLETED,
                        description = "BC",
                        tags =
                            setOf(
                                "android",
                                "release",
                            ),
                    ),
                    task(
                        "c",
                        ReportStatus.DELETED,
                        project = null,
                    ),
                ),
                ProjectScope.All,
                now,
            )
        assertEquals(3, r.total)
        assertEquals(3, r.pending + r.completed + r.deleted)
        assertEquals(1, r.waiting)
        assertEquals(1, r.active)
        assertEquals(2, r.uniqueTags)
        assertEquals(1, r.projects)
        assertEquals(3, r.annotations)
        assertEquals(200.0 / 3, r.taggedPercent, 0.0001)
        assertEquals(2, r.projectTasks)
        assertEquals(200.0 / 3, r.projectPercent, 0.0001)
        assertEquals(2.0, r.averageDescriptionCodePoints, 0.0)
    }

    @Test fun emptyStatsNeverProduceNanOrInventDates() {
        val r = calculateStats(emptyList(), ProjectScope.All, now)
        assertEquals(0, r.total)
        assertEquals(0.0, r.taggedPercent, 0.0)
        assertEquals(0, r.projectTasks)
        assertEquals(0.0, r.projectPercent, 0.0)
        assertEquals(0.0, r.averageDescriptionCodePoints, 0.0)
        assertNull(r.oldestEntry)
    }

    @Test fun calendarUsesLocalBoundariesAcrossDstAndPartialWeeks() {
        val rome = ZoneId.of("Europe/Rome")
        val spring = reportBuckets(monthRange(YearMonth.of(2026, 3), rome), ReportGrouping.DAY, rome, DayOfWeek.MONDAY)
        assertEquals(23, Duration.between(spring[28].range.start, spring[28].range.endExclusive).toHours().toInt())
        val autumn = reportBuckets(monthRange(YearMonth.of(2026, 10), rome), ReportGrouping.DAY, rome, DayOfWeek.MONDAY)
        assertEquals(25, Duration.between(autumn[24].range.start, autumn[24].range.endExclusive).toHours().toInt())
        assertEquals(LocalDate.of(2026, 10, 5), buckets().first().endDateExclusive)
        val end = task("end", entry = "2026-11-01T00:00:00Z")
        assertEquals(0, calculateHistory(listOf(end), ProjectScope.All, buckets()).totals.added)
    }
}
