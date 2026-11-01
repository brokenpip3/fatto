package com.brokenpip3.fatto

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
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class ReportCompatibilityTest {
    private fun resource(name: String): String = javaClass.getResource("/reports/$name")!!.readText()

    @Test fun retainedRecordsMatchTaskwarriorReferenceFixture() {
        val raw = JSONArray(resource("tasks.json"))
        val expected = JSONObject(resource("expected.json"))
        val dateFormat = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmssX").withZone(ZoneOffset.UTC)
        val tasks =
            (0 until raw.length()).map { index ->
                val task = raw.getJSONObject(index)

                fun date(name: String): Instant? = if (task.has(name)) Instant.from(dateFormat.parse(task.getString(name))) else null
                val tags = task.optJSONArray("tags") ?: JSONArray()
                ReportTask(
                    task.getString("uuid"), task.getString("description"), ReportStatus.valueOf(task.getString("status").uppercase()),
                    task.optString("project").takeIf { it.isNotEmpty() },
                    (0 until tags.length()).map { tags.getString(it) }.toSet(), 0, date("entry"), date("start"), date("end"), null,
                )
            }
        val zone = ZoneOffset.UTC
        val history =
            calculateHistory(
                tasks,
                ProjectScope.All,
                reportBuckets(
                    monthRange(
                        YearMonth.of(
                            2026,
                            10,
                        ),
                        zone,
                    ),
                    ReportGrouping.MONTH,
                    zone,
                    DayOfWeek.MONDAY,
                ),
            )
        assertEquals(expected.getInt("added"), history.totals.added)
        assertEquals(expected.getInt("completed"), history.totals.completed)
        assertEquals(expected.getInt("deleted"), history.totals.deleted)
        val now = Instant.parse(expected.getString("capturedAt"))
        val stats = calculateStats(tasks, ProjectScope.All, now)
        assertEquals(expected.getInt("total"), stats.total)
        assertEquals(expected.getInt("pending"), stats.pending)
        assertEquals(expected.getInt("projects"), stats.projects)
        assertEquals(expected.getInt("tags"), stats.uniqueTags)
        val range = timesheetRange(LocalDate.of(2026, 10, 30), DayOfWeek.MONDAY, 0, zone)
        val timesheet = calculateTimesheet(tasks, ProjectScope.All, reportBuckets(range, ReportGrouping.WEEK, zone, DayOfWeek.MONDAY), now)
        assertEquals(listOf(expected.getString("completedUuid")), timesheet.weeks.flatMap { it.completed }.map { it.uuid })
        assertEquals(listOf(expected.getString("startedUuid")), timesheet.weeks.flatMap { it.started }.map { it.uuid })
    }
}
