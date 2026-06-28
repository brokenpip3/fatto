package com.brokenpip3.fatto.data

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

object DateTimeUtils {
    /**
     * Parses an RFC-3339 / ISO-8601 timestamp into an [Instant], tolerating both
     * a "Z" suffix and an explicit numeric offset (e.g. "+00:00").
     *
     * The Rust layer emits timestamps via chrono's `to_rfc3339()`, which renders
     * UTC as "+00:00" rather than "Z". `Instant.parse` only accepts "Z" and would
     * throw a `DateTimeParseException`, crashing on any task that carries a
     * wait/due/scheduled date (common after syncing real Taskwarrior data).
     * Returns null when the value is missing or unparseable.
     */
    fun parseToInstant(dateStr: String?): Instant? {
        if (dateStr.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(dateStr).toInstant()
        } catch (e: Exception) {
            try {
                Instant.parse(dateStr)
            } catch (e2: Exception) {
                null
            }
        }
    }

    /**
     * Extracts the date portion (YYYY-MM-DD) from an ISO-8601 string.
     * We treat dates as "floating" - if it says April 28 in UTC, it's April 28 for the user,
     * regardless of their local timezone offset.
     */
    fun parseToLocalDate(dateStr: String?): LocalDate? {
        if (dateStr.isNullOrBlank()) return null
        return try {
            // Simply take the first 10 characters (YYYY-MM-DD)
            LocalDate.parse(dateStr.take(10))
        } catch (e: Exception) {
            null
        }
    }

    fun formatLocalDate(dateStr: String?): String? {
        return parseToLocalDate(dateStr)?.toString()
    }

    fun isToday(dateStr: String?): Boolean {
        val date = parseToLocalDate(dateStr) ?: return false
        return date == LocalDate.now()
    }

    fun isOverdue(dateStr: String?): Boolean {
        val date = parseToLocalDate(dateStr) ?: return false
        return date.isBefore(LocalDate.now())
    }
}
