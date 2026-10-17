package com.brokenpip3.fatto.data.review

import java.time.Instant
import java.time.temporal.ChronoUnit

data class ReviewReminderState(val nextDueEpochMillis: Long? = null, val generation: Long = 0)

/** Serializes preference changes and notification delivery in the app's single process. */
object ReviewPreferenceLock {
    val lock = Any()
}

object ReviewReminderPolicy {
    fun nextDue(
        now: Instant,
        intervalDays: Int,
    ): Instant {
        require(intervalDays > 0) { "Enter a positive reminder interval" }
        return now.plus(intervalDays.toLong(), ChronoUnit.DAYS).also { it.toEpochMilli() }
    }
}
