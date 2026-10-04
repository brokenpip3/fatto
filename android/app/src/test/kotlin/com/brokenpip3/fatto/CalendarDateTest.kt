package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.DateTimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class CalendarDateTest {
    @Test
    fun parseUtcZ() {
        assertEquals(
            LocalDate.of(2026, 4, 28),
            DateTimeUtils.parseToLocalDate("2026-04-28T00:00:00Z"),
        )
    }

    @Test
    fun parseUtcWithOffset() {
        assertEquals(
            LocalDate.of(2026, 4, 28),
            DateTimeUtils.parseToLocalDate("2026-04-28T00:00:00+00:00"),
        )
    }

    @Test
    fun parsePositiveOffset() {
        assertEquals(
            LocalDate.of(2026, 4, 28),
            DateTimeUtils.parseToLocalDate("2026-04-28T02:00:00+02:00"),
        )
    }

    @Test
    fun parseOffByOneScenario() {
        assertEquals(
            LocalDate.of(2026, 4, 27),
            DateTimeUtils.parseToLocalDate("2026-04-27T22:00:00Z"),
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
            DateTimeUtils.formatLocalDate("2026-04-28T00:00:00Z"),
        )
    }

    @Test
    fun formatLocalDateReturnsNullForNull() {
        assertNull(DateTimeUtils.formatLocalDate(null))
    }

    private val berlin = ZoneId.of("Europe/Berlin")
    private val newYork = ZoneId.of("America/New_York")

    @Test
    fun toStoredTimestampWithoutTimeIsFloatingMidnightUtc() {
        assertEquals(
            "2026-04-28T00:00:00Z",
            DateTimeUtils.toStoredTimestamp(LocalDate.of(2026, 4, 28), null, berlin),
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
    fun hasTimeIsFalseForFloatingAndLocalMidnight() {
        assertEquals(false, DateTimeUtils.hasTime("2026-04-28T00:00:00+00:00", berlin))
        assertEquals(false, DateTimeUtils.hasTime("2026-04-27T22:00:00Z", berlin))
        assertEquals(false, DateTimeUtils.hasTime(null, berlin))
    }

    @Test
    fun hasTimeIsTrueForPickedTime() {
        assertEquals(true, DateTimeUtils.hasTime("2026-04-28T07:30:00Z", berlin))
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
        assertEquals("2026-04-28", DateTimeUtils.formatLocalDateTime("2026-04-28T00:00:00Z", berlin))
        assertEquals("2026-04-28 09:30", DateTimeUtils.formatLocalDateTime("2026-04-28T07:30:00Z", berlin))
    }

    @Test
    fun pickedTimeRoundTrips() {
        val stored = DateTimeUtils.toStoredTimestamp(LocalDate.of(2026, 4, 28), LocalTime.of(23, 15), newYork)
        assertEquals(LocalDate.of(2026, 4, 28), DateTimeUtils.parseToLocalDate(stored, newYork))
        assertEquals(LocalTime.of(23, 15), DateTimeUtils.parseToLocalTime(stored, newYork))
    }
}
