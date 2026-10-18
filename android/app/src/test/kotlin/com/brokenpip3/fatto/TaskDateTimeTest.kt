package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.TaskDateSelection
import com.brokenpip3.fatto.data.TaskDateTime
import com.brokenpip3.fatto.data.TaskDateValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.taskchampion_android.DateDisplayMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

class TaskDateTimeTest {
    private val rome = ZoneId.of("Europe/Rome")
    private val calendar = DateDisplayMode.CALENDAR_DATE
    private val timed = DateDisplayMode.LOCAL_DATE_TIME

    private fun value(
        raw: String?,
        mode: DateDisplayMode = calendar,
    ) = TaskDateValue(raw, mode)

    @Test fun unchangedRawPresentationDoesNotRepairANullTypedDate() {
        val raw = value("2026-04-28T00:00:00Z")
        assertEquals(value(null), TaskDateTime.confirmEditorSelection(null, raw, raw))
        val repaired = value("2026-04-29T00:00:00Z")
        assertEquals(repaired, TaskDateTime.confirmEditorSelection(null, raw, repaired))
        val optedIn = raw.copy(mode = timed)
        assertEquals(optedIn, TaskDateTime.confirmEditorSelection(null, raw, optedIn))
        assertEquals(raw, TaskDateTime.confirmEditorSelection(null, value(null), raw))
    }

    @Test fun legacyDatesRetainUtcDayAndClockWhenCalendarChanges() {
        val original = value("2026-04-28T22:30:45+00:00")
        assertEquals(TaskDateSelection(LocalDate.of(2026, 4, 28), null), TaskDateTime.decode(original, rome))
        assertEquals(original, TaskDateTime.resolve(TaskDateTime.decode(original, rome)!!, rome, original).getOrThrow())
        assertEquals(
            "2026-04-29T22:30:45Z",
            TaskDateTime.resolve(
                TaskDateSelection(
                    LocalDate.of(
                        2026,
                        4,
                        29,
                    ),
                    null,
                ),
                rome,
                original,
            ).getOrThrow().timestamp,
        )
    }

    @Test fun explicitMidnightAndSecondsSurviveReopeningAndCalendarEdits() {
        val original = value("2026-04-28T22:00:45Z", timed)
        val selection = TaskDateTime.decode(original, rome)!!
        assertEquals(LocalTime.of(0, 0, 45), selection.time)
        assertEquals(original, TaskDateTime.resolve(selection, rome, original).getOrThrow())
        assertEquals(
            "2026-04-29T22:00:45Z",
            TaskDateTime.resolve(
                selection.copy(date = selection.date.plusDays(1)),
                rome,
                original,
            ).getOrThrow().timestamp,
        )
        val midnight = value("2026-04-28T22:00:00Z", timed)
        assertEquals(LocalTime.MIDNIGHT, TaskDateTime.decode(midnight, rome)!!.time)
    }

    @Test fun deliberateClockChangeResetsSecondsAndOptOutUsesUtcMidnight() {
        val original = value("2026-04-28T07:30:45Z", timed)
        assertEquals(
            "2026-04-28T08:00:00Z",
            TaskDateTime.resolve(
                TaskDateSelection(
                    LocalDate.of(
                        2026,
                        4,
                        28,
                    ),
                    LocalTime.of(
                        10,
                        0,
                    ),
                ),
                rome,
                original,
            ).getOrThrow().timestamp,
        )
        assertEquals(
            value("2026-04-28T00:00:00Z"),
            TaskDateTime.resolve(
                TaskDateSelection(
                    LocalDate.of(
                        2026,
                        4,
                        28,
                    ),
                    null,
                ),
                rome,
                original,
            ).getOrThrow(),
        )
    }

    @Test fun modeCanChangeWithoutChangingInstant() {
        val utc = ZoneId.of("UTC")
        val original = value("2026-04-28T00:00:00Z")
        assertEquals(
            value(
                original.timestamp,
                timed,
            ),
            TaskDateTime.resolve(
                TaskDateSelection(
                    LocalDate.of(
                        2026,
                        4,
                        28,
                    ),
                    LocalTime.MIDNIGHT,
                ),
                utc,
                original,
            ).getOrThrow(),
        )
    }

    @Test fun gapIsRejectedAndOverlapKeepsOriginalOffset() {
        assertTrue(TaskDateTime.resolve(TaskDateSelection(LocalDate.of(2026, 3, 29), LocalTime.of(2, 30)), rome).isFailure)
        val later = value("2026-10-25T01:30:00Z", timed)
        val selection = TaskDateTime.decode(later, rome)!!
        assertEquals(later, TaskDateTime.resolve(selection, rome, later).getOrThrow())
        assertEquals("2026-10-25T00:30:00Z", TaskDateTime.resolve(selection, rome).getOrThrow().timestamp)
    }

    @Test fun calendarDatesExistEvenWhenLocalZoneSkipsTheDayAndMalformedValuesStayNull() {
        val skipped = LocalDate.of(2011, 12, 30)
        assertEquals(
            value("2011-12-30T00:00:00Z"),
            TaskDateTime.resolve(
                TaskDateSelection(
                    skipped,
                    null,
                ),
                ZoneId.of("Pacific/Apia"),
            ).getOrThrow(),
        )
        assertNull(TaskDateTime.decode(value("garbage"), rome))
        assertNull(TaskDateTime.decode(value("2026-04-28"), rome))
        assertEquals(LocalDate.of(9999, 12, 31), TaskDateTime.localDate(value("9999-12-31T00:00:00Z"), rome))
    }

    @Test fun formattingShowsSecondsAndRespectsClockPreference() {
        val original = value("2026-04-28T07:30:45Z", timed)
        assertEquals("2026-04-28 09:30:45", TaskDateTime.format(original, rome, true, Locale.US))
        assertEquals("2026-04-28 9:30:45 AM", TaskDateTime.format(original, rome, false, Locale.US))
        assertEquals("2026-04-28", TaskDateTime.format(value(original.timestamp), rome, true, Locale.US))
    }
}
