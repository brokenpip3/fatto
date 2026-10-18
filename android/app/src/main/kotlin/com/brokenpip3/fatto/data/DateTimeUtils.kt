package com.brokenpip3.fatto.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

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

    /** Historical floating UTC calendar date; task fields with time use TaskDateTime explicitly. */
    @Suppress("UnusedParameter") // Floating dates deliberately ignore the device zone; keep the existing signature.
    fun parseToLocalDate(
        dateStr: String?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): LocalDate? {
        return runCatching { LocalDate.parse(dateStr?.take(10)) }.getOrNull()
    }

    fun formatLocalDate(
        dateStr: String?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String? = parseToLocalDate(dateStr, zone)?.toString()

    fun formatLocalDateTime(
        dateStr: String?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String? = formatLocalDate(dateStr, zone)

    fun toStoredTimestamp(
        date: LocalDate,
        time: LocalTime?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = TaskDateTime.resolve(TaskDateSelection(date, time), zone).getOrThrow().timestamp!!

    fun isToday(dateStr: String?): Boolean = parseToLocalDate(dateStr) == LocalDate.now()

    fun isOverdue(dateStr: String?): Boolean = parseToLocalDate(dateStr)?.isBefore(LocalDate.now()) == true
}
