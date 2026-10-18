package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.DateTimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class CalendarDateTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun parseUtcZ() {
        assertEquals(
            LocalDate.of(2026, 4, 28),
            DateTimeUtils.parseToLocalDate("2026-04-28T00:00:00Z", utc),
        )
    }

    @Test
    fun parseUtcWithOffset() {
        assertEquals(
            LocalDate.of(2026, 4, 28),
            DateTimeUtils.parseToLocalDate("2026-04-28T00:00:00+00:00", utc),
        )
    }

    @Test
    fun parsePositiveOffset() {
        assertEquals(
            LocalDate.of(2026, 4, 28),
            DateTimeUtils.parseToLocalDate("2026-04-28T02:00:00+02:00", utc),
        )
    }

    @Test
    fun parseOffByOneScenario() {
        assertEquals(
            LocalDate.of(2026, 4, 27),
            DateTimeUtils.parseToLocalDate("2026-04-27T22:00:00Z", utc),
        )
    }

    @Test
    fun parseNull() {
        assertNull(DateTimeUtils.parseToLocalDate(null))
    }

    @Test
    fun parseBlank() {
        assertNull(DateTimeUtils.parseToLocalDate(""))
        assertNull(DateTimeUtils.parseToLocalDate("  "))
    }

    @Test
    fun isTodayReturnsTrueForToday() {
        val today = LocalDate.now()
        assertEquals(true, DateTimeUtils.isToday(today.toString()))
    }

    @Test
    fun isTodayReturnsFalseForYesterday() {
        val yesterday = LocalDate.now().minusDays(1)
        assertEquals(false, DateTimeUtils.isToday(yesterday.toString()))
    }

    @Test
    fun isTodayReturnsFalseForTomorrow() {
        val tomorrow = LocalDate.now().plusDays(1)
        assertEquals(false, DateTimeUtils.isToday(tomorrow.toString()))
    }

    @Test
    fun isOverdueReturnsTrueForYesterday() {
        val yesterday = LocalDate.now().minusDays(1)
        assertEquals(true, DateTimeUtils.isOverdue(yesterday.toString()))
    }

    @Test
    fun isOverdueReturnsFalseForTomorrow() {
        val tomorrow = LocalDate.now().plusDays(1)
        assertEquals(false, DateTimeUtils.isOverdue(tomorrow.toString()))
    }

    @Test
    fun isOverdueReturnsFalseForToday() {
        val today = LocalDate.now()
        assertEquals(false, DateTimeUtils.isOverdue(today.toString()))
    }

    @Test
    fun formatLocalDateReturnsYyyyMmDd() {
        assertEquals(
            "2026-04-28",
            DateTimeUtils.formatLocalDate("2026-04-28T00:00:00Z", utc),
        )
    }

    @Test
    fun formatLocalDateReturnsNullForNull() {
        assertNull(DateTimeUtils.formatLocalDate(null))
    }

    private val berlin = ZoneId.of("Europe/Berlin")
    private val newYork = ZoneId.of("America/New_York")
    private val timed = uniffi.taskchampion_android.DateDisplayMode.LOCAL_DATE_TIME
    private val calendar = uniffi.taskchampion_android.DateDisplayMode.CALENDAR_DATE

    private fun decode(
        raw: String,
        zone: ZoneId,
    ) = com.brokenpip3.fatto.data.TaskDateTime.decode(com.brokenpip3.fatto.data.TaskDateValue(raw, timed), zone)!!

    private fun store(
        date: LocalDate,
        time: LocalTime?,
        zone: ZoneId,
    ) = com.brokenpip3.fatto.data.TaskDateTime.resolve(com.brokenpip3.fatto.data.TaskDateSelection(date, time), zone).getOrThrow()

    @Test fun dateOnlyRoundTripsInEveryZoneIncludingMidnightDstAndSkippedDay() {
        val date = LocalDate.of(2026, 4, 28)
        for (zone in listOf(berlin, newYork, utc, ZoneId.of("Asia/Tokyo"), ZoneId.of("America/Santiago"))) {
            val stored = store(date, null, zone)
            assertEquals("2026-04-28T00:00:00Z", stored.timestamp)
            assertEquals(calendar, stored.mode)
            assertEquals(date, com.brokenpip3.fatto.data.TaskDateTime.localDate(stored, zone))
        }
    }

    @Test fun timeUsesSelectedZoneAndExplicitMidnightKeepsTimeEnabled() {
        assertEquals("2026-04-28T07:30:00Z", store(LocalDate.of(2026, 4, 28), LocalTime.of(9, 30), berlin).timestamp)
        val midnight = store(LocalDate.of(2026, 4, 28), LocalTime.MIDNIGHT, berlin)
        assertEquals(timed, midnight.mode)
        assertEquals(LocalTime.MIDNIGHT, decode(midnight.timestamp!!, berlin).time)
    }

    @Test fun timedValueResolvesDateAndTimeInZone() {
        val raw = "2026-04-28T22:30:00Z"
        assertEquals(LocalDate.of(2026, 4, 29), decode(raw, berlin).date)
        assertEquals(LocalTime.of(0, 30), decode(raw, berlin).time)
        assertEquals(LocalDate.of(2026, 4, 28), decode(raw, newYork).date)
    }

    @Test fun pickedTimeAndUtcMidnightRoundTripInNewYork() {
        for (time in listOf(LocalTime.of(23, 15), LocalTime.of(20, 0))) {
            val stored = store(LocalDate.of(2026, 4, 28), time, newYork)
            assertEquals(LocalDate.of(2026, 4, 28), decode(stored.timestamp!!, newYork).date)
            assertEquals(time, decode(stored.timestamp!!, newYork).time)
        }
    }

    @Test fun confirmingUnchangedOverlapKeepsOriginalOffsetButNewSelectionUsesEarlierOffset() {
        val current = com.brokenpip3.fatto.data.TaskDateValue("2026-11-01T06:30:00Z", timed)
        val selection = decode(current.timestamp!!, newYork)
        assertEquals(LocalTime.of(1, 30), selection.time)
        assertEquals(current, com.brokenpip3.fatto.data.TaskDateTime.resolve(selection, newYork, current).getOrThrow())
        assertEquals("2026-11-01T05:30:00Z", store(selection.date, selection.time, newYork).timestamp)
    }

    @Test fun formattingIncludesTimeOnlyForExplicitTimedValues() {
        val raw = "2026-04-28T07:30:00Z"
        assertEquals(
            "2026-04-28 09:30",
            com.brokenpip3.fatto.data.TaskDateTime.format(
                com.brokenpip3.fatto.data.TaskDateValue(
                    raw,
                    timed,
                ),
                berlin,
            ),
        )
        assertEquals(
            "2026-04-28",
            com.brokenpip3.fatto.data.TaskDateTime.format(
                com.brokenpip3.fatto.data.TaskDateValue(
                    raw,
                    calendar,
                ),
                berlin,
            ),
        )
    }
}
