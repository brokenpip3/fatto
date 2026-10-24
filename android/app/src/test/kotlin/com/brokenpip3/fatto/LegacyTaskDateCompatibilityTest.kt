package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.DateTimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

class LegacyTaskDateCompatibilityTest {
    @Test
    fun genericFloatingDatesRetainTheOriginalDatePrefixEvenWithANonUtcOffset() {
        assertEquals("2026-04-28", DateTimeUtils.formatLocalDate("2026-04-28T00:30:00+02:00"))
    }

    @Test
    fun existingDatesKeepTheirUtcCalendarDayInEveryDeviceZone() {
        val originalZone = TimeZone.getDefault()
        try {
            for (zone in listOf("Europe/Rome", "America/New_York", "UTC")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                for (raw in listOf("2026-04-28T00:00:00Z", "2026-04-28T22:30:45+00:00")) {
                    assertEquals("Legacy date moved in $zone: $raw", "2026-04-28", DateTimeUtils.formatLocalDate(raw))
                }
            }
        } finally {
            TimeZone.setDefault(originalZone)
        }
    }

    @Test
    fun newDateOnlySelectionsKeepTheExistingUtcMidnightEncoding() {
        for (zone in listOf("Europe/Rome", "America/New_York", "UTC")) {
            assertEquals(
                "2026-04-28T00:00:00Z",
                DateTimeUtils.toStoredTimestamp(LocalDate.of(2026, 4, 28), null, ZoneId.of(zone)),
            )
        }
    }

    @Test
    fun earlierClockTimeTodayDoesNotEnterAnOverdueOnlySummary() {
        val originalZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val now = Instant.now()
            val earlier = now.minusSeconds(2).atOffset(ZoneOffset.UTC)
            // Avoid the two-second midnight boundary; the production assertion is day-based.
            assumeTrue(earlier.toLocalDate() == now.atOffset(ZoneOffset.UTC).toLocalDate())
            assumeTrue(earlier.toLocalTime() != LocalTime.MIDNIGHT)
            assertEquals(false, DateTimeUtils.isOverdue(earlier.toInstant().toString()))
        } finally {
            TimeZone.setDefault(originalZone)
        }
    }
}
