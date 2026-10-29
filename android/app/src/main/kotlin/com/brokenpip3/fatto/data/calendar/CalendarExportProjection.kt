package com.brokenpip3.fatto.data.calendar

import com.brokenpip3.fatto.data.TaskDateTime
import com.brokenpip3.fatto.data.model.INTERNAL_TAGS
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.dateValue
import uniffi.taskchampion_android.TaskStatus
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

object CalendarExportProjection {
    fun project(
        tasks: List<Task>,
        config: ExportConfig,
        now: Instant,
        zone: ZoneId,
    ): Projection {
        val results = tasks.map { projectTask(it, config, now, zone) }
        return Projection(results.flatMap { it.events }, results.mapNotNull { it.nextRelease }.minOrNull(), results.flatMap { it.warnings })
    }

    @Suppress("ReturnCount")
    private fun projectTask(
        task: Task,
        config: ExportConfig,
        now: Instant,
        zone: ZoneId,
    ): Projection {
        if (task.status != TaskStatus.Pending || task.isRecurrenceTemplate) return Projection(emptyList(), null)
        val selection = TaskDateTime.decode(task.dateValue("scheduled"), zone) ?: return Projection(emptyList(), null)
        if (config.respectWait && (task.wait != null || task.nativeDates["wait"] != null)) {
            val wait =
                TaskDateTime.decode(task.dateValue("wait"), zone)
                    ?: return Projection(emptyList(), null, listOf("A scheduled task has an unreadable wait date and was withheld."))
            val release = if (wait.time == null) wait.date.atStartOfDay(zone).toInstant() else Instant.parse(task.wait)
            if (release > now) return Projection(emptyList(), release)
        }
        val allDay = selection.time == null
        val start = if (allDay) selection.date.atStartOfDay(ZoneOffset.UTC).toInstant() else Instant.parse(task.scheduled)
        val end = if (allDay) selection.date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() else start.plusSeconds(1800)
        val description =
            buildList {
                task.project?.let { add("Project: $it") }
                val tags = task.userTags.filterNot { it in INTERNAL_TAGS }
                if (tags.isNotEmpty()) add("Tags: ${tags.joinToString(", ")}")
            }.joinToString("\n")
        val event =
            ProjectedEvent(
                EventKey(config.namespace, task.uuid), task.description, description,
                start.toEpochMilli(), end.toEpochMilli(), allDay, if (allDay) "UTC" else zone.id,
            )
        return Projection(listOf(event.copy(fingerprint = event.managedFingerprint())), null)
    }
}
