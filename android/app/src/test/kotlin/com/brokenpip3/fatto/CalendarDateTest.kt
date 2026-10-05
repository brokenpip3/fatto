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

    @Test
    fun toStoredTimestampWithoutTimeIsLocalMidnight() {
        // Taskwarrior: due:2026-04-28 is local midnight.
        assertEquals(
            "2026-04-27T22:00:00Z",
            DateTimeUtils.toStoredTimestamp(LocalDate.of(2026, 4, 28), null, berlin),
        )
        assertEquals(
            "2026-04-28T04:00:00Z",
            DateTimeUtils.toStoredTimestamp(LocalDate.of(2026, 4, 28), null, newYork),
        )
    }

    @Test
    fun toStoredTimestampWithTimeUsesZone() {
        assertEquals(
            "2026-04-28T07:30:00Z",
            DateTimeUtils.toStoredTimestamp(LocalDate.of(2026, 4, 28), LocalTime.of(9, 30), berlin),
        )
    }

    @Test
    fun hasTimeIsFalseOnlyAtLocalMidnight() {
        assertEquals(false, DateTimeUtils.hasTime("2026-04-27T22:00:00Z", berlin))
        assertEquals(false, DateTimeUtils.hasTime(null, berlin))
        assertEquals(true, DateTimeUtils.hasTime("2026-04-28T07:30:00Z", berlin))
        // UTC midnight is just an instant, not a date-only marker.
        assertEquals(true, DateTimeUtils.hasTime("2026-04-28T00:00:00Z", berlin))
    }

    @Test
    fun timedValueResolvesDateAndTimeInZone() {
        assertEquals(LocalDate.of(2026, 4, 29), DateTimeUtils.parseToLocalDate("2026-04-28T22:30:00Z", berlin))
        assertEquals(LocalTime.of(0, 30), DateTimeUtils.parseToLocalTime("2026-04-28T22:30:00Z", berlin))
        assertEquals(LocalDate.of(2026, 4, 28), DateTimeUtils.parseToLocalDate("2026-04-28T22:30:00Z", newYork))
    }

    @Test
    fun taskwarriorLocalMidnightResolvesToLocalDate() {
        assertEquals(LocalDate.of(2026, 4, 28), DateTimeUtils.parseToLocalDate("2026-04-27T22:00:00Z", berlin))
        assertNull(DateTimeUtils.parseToLocalTime("2026-04-27T22:00:00Z", berlin))
    }

    @Test
    fun formatLocalDateTimeAppendsTimeOnlyWhenSet() {
        assertEquals("2026-04-28", DateTimeUtils.formatLocalDateTime("2026-04-27T22:00:00Z", berlin))
        assertEquals("2026-04-28 09:30", DateTimeUtils.formatLocalDateTime("2026-04-28T07:30:00Z", berlin))
    }

    @Test
    fun dateOnlyRoundTripsInAnyZone() {
        for (zone in listOf(berlin, newYork, utc, ZoneId.of("Asia/Tokyo"))) {
            val stored = DateTimeUtils.toStoredTimestamp(LocalDate.of(2026, 4, 28), null, zone)
            assertEquals(LocalDate.of(2026, 4, 28), DateTimeUtils.parseToLocalDate(stored, zone))
            assertNull(DateTimeUtils.parseToLocalTime(stored, zone))
        }
    }

    @Test
    fun pickedTimeRoundTrips() {
        val stored = DateTimeUtils.toStoredTimestamp(LocalDate.of(2026, 4, 28), LocalTime.of(23, 15), newYork)
        assertEquals(LocalDate.of(2026, 4, 28), DateTimeUtils.parseToLocalDate(stored, newYork))
        assertEquals(LocalTime.of(23, 15), DateTimeUtils.parseToLocalTime(stored, newYork))
    }

    @Test
    fun timeAtUtcMidnightKeepsItsLocalDateAndTime() {
        // 20:00 in New York is 00:00Z the next day; it must stay April 28 20:00.
        val stored = DateTimeUtils.toStoredTimestamp(LocalDate.of(2026, 4, 28), LocalTime.of(20, 0), newYork)
        assertEquals(LocalDate.of(2026, 4, 28), DateTimeUtils.parseToLocalDate(stored, newYork))
        assertEquals(LocalTime.of(20, 0), DateTimeUtils.parseToLocalTime(stored, newYork))
    }

    @Test
    fun dateOnlyRoundTripsWhenDstSkipsMidnight() {
        // Santiago springs forward at midnight, so the day starts at 01:00.
        val santiago = ZoneId.of("America/Santiago")
        val date = LocalDate.of(2026, 9, 6)
        val stored = DateTimeUtils.toStoredTimestamp(date, null, santiago)
        assertEquals(false, DateTimeUtils.hasTime(stored, santiago))
        assertNull(DateTimeUtils.parseToLocalTime(stored, santiago))
        assertEquals(date, DateTimeUtils.parseToLocalDate(stored, santiago))
    }

    @Test
    fun confirmingUnchangedSelectionKeepsStoredValueInDstOverlap() {
        // 06:30Z on 2026-11-01 is 01:30 EST, the second 01:30 of the day in New York.
        val current = "2026-11-01T06:30:00Z"
        val date = DateTimeUtils.parseToLocalDate(current, newYork)!!
        val time = DateTimeUtils.parseToLocalTime(current, newYork)
        assertEquals(LocalTime.of(1, 30), time)
        assertEquals(current, DateTimeUtils.confirmedTimestamp(current, date, time, newYork))
        // Rebuilding from local fields alone would pick the earlier offset.
        assertEquals("2026-11-01T05:30:00Z", DateTimeUtils.toStoredTimestamp(date, time, newYork))
    }

    @Test
    fun confirmingChangedSelectionStoresNewValue() {
        val current = "2026-04-28T07:30:00Z"
        assertEquals(
            "2026-04-28T08:00:00Z",
            DateTimeUtils.confirmedTimestamp(current, LocalDate.of(2026, 4, 28), LocalTime.of(10, 0), berlin),
        )
        assertEquals(
            "2026-04-27T22:00:00Z",
            DateTimeUtils.confirmedTimestamp(null, LocalDate.of(2026, 4, 28), null, berlin),
        )
    }
}
