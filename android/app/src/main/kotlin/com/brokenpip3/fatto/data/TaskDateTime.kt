package com.brokenpip3.fatto.data

import uniffi.taskchampion_android.DateDisplayMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class TaskDateValue(val timestamp: String?, val mode: DateDisplayMode)

data class TaskDateSelection(val date: LocalDate, val time: LocalTime?)

/** Explicit presentation keeps existing floating calendar dates independent of the device zone. */
object TaskDateTime {
    @Suppress("ReturnCount")
    fun decode(
        value: TaskDateValue,
        zone: ZoneId,
    ): TaskDateSelection? {
        val instant = DateTimeUtils.parseToInstant(value.timestamp) ?: return null
        if (value.mode == DateDisplayMode.LOCAL_DATE_TIME) {
            val local = instant.atZone(zone)
            return TaskDateSelection(local.toLocalDate(), local.toLocalTime())
        }
        val date = DateTimeUtils.parseToLocalDate(value.timestamp) ?: return null
        return TaskDateSelection(date, null)
    }

    fun localDate(
        value: TaskDateValue,
        zone: ZoneId = ZoneId.systemDefault(),
    ): LocalDate? = decode(value, zone)?.date

    /** Raw fallback is presentation only: unchanged OK keeps the unreadable native value untouched. */
    fun confirmEditorSelection(
        typed: String?,
        presented: TaskDateValue,
        selected: TaskDateValue,
    ): TaskDateValue = if (typed == null && selected == presented) selected.copy(timestamp = null) else selected

    fun isToday(
        value: TaskDateValue,
        now: java.time.Instant = java.time.Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean = localDate(value, zone) == now.atZone(zone).toLocalDate()

    fun isOverdue(
        value: TaskDateValue,
        now: java.time.Instant = java.time.Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean = localDate(value, zone)?.isBefore(now.atZone(zone).toLocalDate()) == true

    fun resolve(
        selection: TaskDateSelection,
        zone: ZoneId,
        original: TaskDateValue? = null,
    ): Result<TaskDateValue> =
        runCatching {
            val mode = if (selection.time == null) DateDisplayMode.CALENDAR_DATE else DateDisplayMode.LOCAL_DATE_TIME
            val previous = original?.let { decode(it, zone) }
            if (original?.mode == mode && previous == selection) return@runCatching original
            val oldInstant = DateTimeUtils.parseToInstant(original?.timestamp)
            val timestamp =
                if (selection.time == null) {
                    val clock = if (original?.mode == mode) oldInstant?.atOffset(ZoneOffset.UTC)?.toLocalTime() else null
                    selection.date.atTime(clock ?: LocalTime.MIDNIGHT).toInstant(ZoneOffset.UTC)
                } else {
                    val local = selection.date.atTime(selection.time)
                    val offsets = zone.rules.getValidOffsets(local)
                    require(offsets.isNotEmpty()) { "This clock time does not exist in $zone. Choose another time." }
                    val oldOffset = oldInstant?.atZone(zone)?.offset
                    val offset = oldOffset?.takeIf { original?.mode == mode && it in offsets } ?: offsets.first()
                    ZonedDateTime.ofStrict(local, offset, zone).toInstant()
                }
            TaskDateValue(timestamp.toString(), mode)
        }

    @Suppress("ReturnCount")
    fun format(
        value: TaskDateValue,
        zone: ZoneId = ZoneId.systemDefault(),
        use24Hour: Boolean = true,
        locale: Locale = Locale.getDefault(),
    ): String? {
        val selection = decode(value, zone) ?: return null
        val time = selection.time ?: return selection.date.toString()
        val seconds = if (time.second != 0) ":ss" else ""
        val pattern = if (use24Hour) "HH:mm$seconds" else "h:mm$seconds a"
        return "${selection.date} ${time.format(DateTimeFormatter.ofPattern(pattern, locale))}"
    }
}
