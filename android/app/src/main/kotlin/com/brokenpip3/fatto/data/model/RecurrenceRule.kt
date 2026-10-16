package com.brokenpip3.fatto.data.model

import uniffi.taskchampion_android.RecurrenceData
import uniffi.taskchampion_android.RecurrenceRulePatch
import uniffi.taskchampion_android.StringEdit
import java.time.Instant

/** Creation input; edits always compare against the exact native rule snapshot. */
data class RecurrenceRule(val interval: String, val until: String? = null) {
    fun toNative() = uniffi.taskchampion_android.RecurrenceRule(interval, until)

    fun creationError(due: String?): String? {
        val preset = interval in listOf("daily", "weekdays", "weekly", "monthly", "yearly")
        val custom = Regex("([0-9]+)(days|weeks|months|years)").matchEntire(interval)
        val validInterval = preset || custom?.groupValues?.get(1)?.toLongOrNull()?.let { it in 1..Int.MAX_VALUE.toLong() } == true
        val dueInstant = due?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val end = until?.let { runCatching { Instant.parse(it) }.getOrNull() }
        return when {
            !validInterval -> "Choose a repeat interval or a positive number of days, weeks, months or years"
            dueInstant == null -> "A recurring series requires Due"
            until != null && end == null -> "Choose a valid Until date"
            end != null && end < dueInstant -> "Until cannot be earlier than Due"
            else -> null
        }
    }

    fun toPatch(baseline: RecurrenceData): RecurrenceRulePatch {
        val originalUntil = baseline.until ?: baseline.untilRaw
        val equalUntil =
            until == originalUntil ||
                (
                    until != null &&
                        originalUntil != null &&
                        runCatching { Instant.parse(until) == Instant.parse(originalUntil) }.getOrDefault(false)
                )
        return RecurrenceRulePatch(
            interval = if (interval == baseline.recur) StringEdit.Unchanged else StringEdit.Replace(baseline.recur, interval),
            until = if (equalUntil) StringEdit.Unchanged else StringEdit.Replace(baseline.untilRaw, until),
        )
    }
}
