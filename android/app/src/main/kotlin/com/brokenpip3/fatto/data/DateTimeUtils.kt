package com.brokenpip3.fatto.data

import java.time.LocalDate

object DateTimeUtils {
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
