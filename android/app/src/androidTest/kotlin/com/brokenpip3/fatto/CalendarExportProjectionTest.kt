package com.brokenpip3.fatto

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brokenpip3.fatto.data.calendar.CalendarExportProjection
import com.brokenpip3.fatto.data.calendar.ExportConfig
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.calendarDateModes
import com.brokenpip3.fatto.data.model.withField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.taskchampion_android.DateDisplayMode
import uniffi.taskchampion_android.TaskStatus
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class CalendarExportProjectionTest {
    private val now = Instant.parse("2026-10-29T10:00:00Z")
    private val zone = ZoneId.of("Europe/Rome")
    private val config = ExportConfig("replica", true, true, null, 1)

    private fun task(
        scheduled: String? = null,
        wait: String? = null,
    ) = Task(
        "00000000-0000-0000-0000-000000000001", "Plan work", TaskStatus.Pending, emptyList(),
        "2026-10-30T00:00:00Z", null, "work", wait, scheduled, null, null, 0f,
        false, false, emptyList(), emptyMap(),
    )

    @Test fun onlyScheduledTasksProduceEvents() {
        assertTrue(CalendarExportProjection.project(listOf(task(), task(wait = now.toString())), config, now, zone).events.isEmpty())
        val result = CalendarExportProjection.project(listOf(task("2026-10-30T00:00:00Z")), config, now, zone)
        assertEquals(1, result.events.size)
        val event = result.events.single()
        assertTrue(event.allDay)
        assertEquals(Instant.parse("2026-10-30T00:00:00Z").toEpochMilli(), event.startMillis)
        assertEquals(86_400_000L, event.endMillis - event.startMillis)
    }

    @Test fun timedScheduleIsThirtyMinutesAndDueDoesNotDuplicateIt() {
        val task =
            task("2026-10-30T14:00:00Z").copy(
                dateModes =
                    calendarDateModes().withField(
                        "scheduled",
                        DateDisplayMode.LOCAL_DATE_TIME,
                    ),
            )
        val event = CalendarExportProjection.project(listOf(task), config, now, zone).events.single()
        assertFalse(event.allDay)
        assertEquals(1_800_000L, event.endMillis - event.startMillis)
        assertEquals(Instant.parse("2026-10-30T14:00:00Z").toEpochMilli(), event.startMillis)
    }

    @Test fun futureWaitIsOptionalAndSchedulesAutomaticRelease() {
        val task = task("2026-10-28T00:00:00Z", "2026-10-30T00:00:00Z")
        val held = CalendarExportProjection.project(listOf(task), config, now, zone)
        assertTrue(held.events.isEmpty())
        assertEquals(Instant.parse("2026-10-29T23:00:00Z"), held.nextRelease)
        assertEquals(1, CalendarExportProjection.project(listOf(task), config.copy(respectWait = false), now, zone).events.size)
        assertEquals(1, CalendarExportProjection.project(listOf(task), config, Instant.parse("2026-10-29T23:00:00Z"), zone).events.size)
    }

    @Test fun timedWaitUsesInstantAndInvalidWaitDoesNotLeakTask() {
        val timed =
            task(
                "2026-10-30T00:00:00Z",
                "2026-10-29T10:01:00Z",
            ).copy(
                dateModes =
                    calendarDateModes().withField(
                        "wait",
                        DateDisplayMode.LOCAL_DATE_TIME,
                    ),
            )
        assertEquals(Instant.parse("2026-10-29T10:01:00Z"), CalendarExportProjection.project(listOf(timed), config, now, zone).nextRelease)
        val invalid = task("2026-10-30T00:00:00Z", "bad")
        assertTrue(CalendarExportProjection.project(listOf(invalid), config, now, zone).events.isEmpty())
        assertFalse(CalendarExportProjection.project(listOf(invalid), config, now, zone).warnings.isEmpty())
    }

    @Test fun completedAndTemplatesAreExcludedButBlockedTasksRemain() {
        val base = task("2026-10-30T00:00:00Z")
        assertTrue(
            CalendarExportProjection.project(
                listOf(
                    base.copy(status = TaskStatus.Completed),
                    base.copy(status = TaskStatus.Recurring),
                ),
                config,
                now,
                zone,
            ).events.isEmpty(),
        )
        assertEquals(1, CalendarExportProjection.project(listOf(base.copy(isBlocked = true)), config, now, zone).events.size)
    }

    @Test fun allDayDatesStayOnTheirDayAcrossDstZones() {
        val base = task("2026-03-29T00:00:00Z")
        val event = CalendarExportProjection.project(listOf(base), config, now, ZoneId.of("America/Los_Angeles")).events.single()
        assertEquals(Instant.parse("2026-03-29T00:00:00Z").toEpochMilli(), event.startMillis)
        assertEquals(Instant.parse("2026-03-30T00:00:00Z").toEpochMilli(), event.endMillis)
    }
}
