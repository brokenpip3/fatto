package com.brokenpip3.fatto.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
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

    /**
     * True when the value carries a time of day. Like Taskwarrior (and taskwarrior-tui),
     * a date is just an instant: `due:2026-04-28` is stored as the start of that local
     * day, so that instant means "no time set" and anything else is shown with its time.
     * Comparing with the start-of-day instant (not 00:00) also covers zones where a DST
     * transition skips midnight.
     */
    fun hasTime(
        dateStr: String?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        val instant = parseToInstant(dateStr) ?: return false
        val startOfDay = instant.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        return instant != startOfDay
    }

    /**
     * Calendar date of the instant in [zone], as Taskwarrior resolves it. A bare
     * "yyyy-MM-dd" (no time or offset) is taken as that calendar date.
     */
    fun parseToLocalDate(
        dateStr: String?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): LocalDate? {
        if (dateStr.isNullOrBlank()) return null
        parseToInstant(dateStr)?.let { return it.atZone(zone).toLocalDate() }
        return try {
            LocalDate.parse(dateStr.trim())
        } catch (e: Exception) {
            null
        }
    }

    /** Local time of day, or null when the value is the start of its local day. */
    fun parseToLocalTime(
        dateStr: String?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): LocalTime? {
        if (!hasTime(dateStr, zone)) return null
        return parseToInstant(dateStr)?.atZone(zone)?.toLocalTime()?.withSecond(0)?.withNano(0)
    }

    fun formatLocalDate(
        dateStr: String?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String? {
        return parseToLocalDate(dateStr, zone)?.toString()
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
     * Builds the RFC-3339 string stored for a picked date and optional time, matching
     * Taskwarrior: without a time it is local midnight (`due:2026-04-28`), with one it
     * is that time in [zone] (`due:2026-04-28T09:00`).
     */
    fun toStoredTimestamp(
        date: LocalDate,
        time: LocalTime?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val instant =
            if (time == null) {
                date.atStartOfDay(zone).toInstant()
            } else {
                date.atTime(time).atZone(zone).toInstant()
            }
        return instant.toString()
    }

    /**
     * The value to save when the picker is confirmed. If the selected date and time match
     * what [current] already shows, [current] is kept as is: rebuilding it from local
     * fields could pick the other offset in a DST overlap and move it by an hour.
     */
    fun confirmedTimestamp(
        current: String?,
        date: LocalDate,
        time: LocalTime?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        if (current != null &&
            parseToLocalDate(current, zone) == date &&
            parseToLocalTime(current, zone) == time
        ) {
            return current
        }
        return toStoredTimestamp(date, time, zone)
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
