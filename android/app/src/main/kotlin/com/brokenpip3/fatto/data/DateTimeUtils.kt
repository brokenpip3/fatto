package com.brokenpip3.fatto.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

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

    private fun isUtcMidnight(instant: Instant): Boolean = instant.atZone(ZoneOffset.UTC).toLocalTime() == LocalTime.MIDNIGHT

    private fun isLocalMidnight(
        instant: Instant,
        zone: ZoneId,
    ): Boolean = instant.atZone(zone).toLocalTime() == LocalTime.MIDNIGHT

    /**
     * True when the value carries a meaningful time of day. Midnight UTC is the app's
     * "floating" date-only form and midnight local is what Taskwarrior stores for a
     * date-only value, so both count as date-only.
     */
    fun hasTime(
        dateStr: String?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        val instant = parseToInstant(dateStr) ?: return false
        return !isUtcMidnight(instant) && !isLocalMidnight(instant, zone)
    }

    /**
     * Extracts the calendar date from an ISO-8601 string.
     * Date-only values written by this app are "floating" (midnight UTC) - if it says
     * April 28 in UTC, it's April 28 for the user, regardless of their local timezone
     * offset. Any other instant (a picked time, or Taskwarrior's local midnight) is
     * resolved in [zone].
     */
    fun parseToLocalDate(
        dateStr: String?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): LocalDate? {
        if (dateStr.isNullOrBlank()) return null
        val instant = parseToInstant(dateStr)
        if (instant != null && !isUtcMidnight(instant)) {
            return instant.atZone(zone).toLocalDate()
        }
        return try {
            // Simply take the first 10 characters (YYYY-MM-DD)
            LocalDate.parse(dateStr.take(10))
        } catch (e: Exception) {
            null
        }
    }

    /** Local time of day, or null for date-only values. */
    fun parseToLocalTime(
        dateStr: String?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): LocalTime? {
        if (!hasTime(dateStr, zone)) return null
        return parseToInstant(dateStr)?.atZone(zone)?.toLocalTime()?.withSecond(0)?.withNano(0)
    }

    fun formatLocalDate(dateStr: String?): String? {
        return parseToLocalDate(dateStr)?.toString()
    }

    /** "yyyy-MM-dd", with " HH:mm" appended when the value has a time of day. */
    fun formatLocalDateTime(
        dateStr: String?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String? {
        val date = parseToLocalDate(dateStr, zone) ?: return null
        val time = parseToLocalTime(dateStr, zone) ?: return date.toString()
        return "$date ${TIME_FORMAT.format(time)}"
    }

    /**
     * Builds the RFC-3339 string stored for a picked date and optional time.
     * Without a time the date stays floating (midnight UTC); with one it is an
     * instant in [zone], like Taskwarrior's own `due:2026-04-28T09:00`.
     */
    fun toStoredTimestamp(
        date: LocalDate,
        time: LocalTime?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val instant =
            if (time == null) {
                date.atStartOfDay(ZoneOffset.UTC).toInstant()
            } else {
                date.atTime(time).atZone(zone).toInstant()
            }
        return instant.toString()
    }

    fun isToday(dateStr: String?): Boolean {
        val date = parseToLocalDate(dateStr) ?: return false
        return date == LocalDate.now()
    }

    fun isOverdue(dateStr: String?): Boolean {
        if (hasTime(dateStr)) {
            return parseToInstant(dateStr)?.isBefore(Instant.now()) == true
        }
        val date = parseToLocalDate(dateStr) ?: return false
        return date.isBefore(LocalDate.now())
    }

    private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
}
