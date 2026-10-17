package com.brokenpip3.fatto.data.review

import com.brokenpip3.fatto.data.model.Task
import uniffi.taskchampion_android.TaskStatus
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

data class ReviewSelection(val tasks: List<Task>, val invalidReviewedTaskIds: Set<String>)

object ReviewPolicy {
    private val basicDate =
        DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT)

    fun parseReviewed(raw: String): Result<Instant> =
        runCatching {
            raw.toLongOrNull()?.let { return@runCatching Instant.ofEpochSecond(it) }
            runCatching { Instant.parse(raw) }.getOrElse {
                LocalDateTime.parse(raw, basicDate).toInstant(ZoneOffset.UTC)
            }
        }

    fun isPending(task: Task): Boolean = task.status == TaskStatus.Pending && !task.isRecurrenceTemplate

    fun select(
        tasks: List<Task>,
        settings: ReviewSettings,
        now: Instant,
    ): ReviewSelection {
        val cutoff = now.minus(settings.intervalDays.toLong(), ChronoUnit.DAYS)
        val invalid = mutableSetOf<String>()
        val candidates =
            tasks.filter { task ->
                if (!isPending(task)) return@filter false
                if (settings.project != null &&
                    task.project != settings.project &&
                    task.project?.startsWith("${settings.project}.") != true
                ) {
                    return@filter false
                }
                if (settings.tag != null && settings.tag !in task.userTags) return@filter false
                val raw = task.udas["reviewed"]?.takeIf { it.isNotEmpty() }
                if (raw == null) return@filter true
                val reviewed = parseReviewed(raw).getOrNull()
                if (reviewed == null) invalid += task.uuid
                reviewed?.isBefore(cutoff) == true
            }.sortedWith(
                compareBy<Task> { task -> task.udas["reviewed"]?.let { parseReviewed(it).getOrNull() } }
                    .thenBy { task -> task.modified?.let { parseReviewed(it).getOrNull() } }.thenBy { it.uuid },
            )
        return ReviewSelection(settings.sessionLimit?.let { candidates.take(it) } ?: candidates, invalid)
    }
}
