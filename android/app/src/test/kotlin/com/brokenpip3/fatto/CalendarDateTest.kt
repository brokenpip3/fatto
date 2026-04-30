package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.DateTimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

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
}
