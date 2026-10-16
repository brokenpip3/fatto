package com.brokenpip3.fatto.data.model

import uniffi.taskchampion_android.TaskData
import uniffi.taskchampion_android.TaskStatus

data class Task(
    val uuid: String,
    val description: String,
    val status: TaskStatus,
    val tags: List<String>,
    val due: String?,
    val entry: String?,
    val project: String?,
    val wait: String?,
    val scheduled: String?,
    val start: String?,
    val priority: String?,
    val urgency: Float,
    val isBlocked: Boolean,
    val isBlocking: Boolean,
    val dependencies: List<String>,
    val udas: Map<String, String>,
    val annotations: List<Annotation> = emptyList(),
    val recurrence: uniffi.taskchampion_android.RecurrenceData? = null,
    val nativeDates: Map<String, String> = emptyMap(),
) {
    val isRecurrenceTemplate: Boolean get() =
        status == TaskStatus.Recurring ||
            (recurrence?.let { it.parent == null && it.imask == null && (it.recur != null || it.mask != null) } == true)
    val isRecurringOccurrence: Boolean get() =
        recurrence?.let {
            runCatching { java.util.UUID.fromString(it.parent) }.isSuccess &&
                it.imask?.let { raw -> raw.isNotEmpty() && raw.all(Char::isDigit) && raw.toLongOrNull() != null } == true
        } == true
    val isActionableStatus: Boolean get() =
        !isRecurrenceTemplate &&
            (status == TaskStatus.Pending || status == TaskStatus.Completed || status == TaskStatus.Deleted)
    val userTags: List<String>
        get() = tags.filter { !isSynthetic(it) }.sorted()
}

val INTERNAL_TAGS = setOf("BLOCKING", "ACTIVE", "BLOCKED", "WAITING")

data class Annotation(
    val entry: String,
    val description: String,
)

fun isSynthetic(tag: String): Boolean {
    return tag == "PENDING" || tag == "COMPLETED" || tag == "DELETED" || tag == "UNBLOCKED"
}

fun TaskData.toModel(): Task =
    Task(
        uuid = uuid,
        description = description,
        status = status,
        tags = tags,
        due = due,
        entry = entry,
        project = project,
        wait = wait,
        scheduled = scheduled,
        start = start,
        priority = priority,
        urgency = urgency,
        isBlocked = isBlocked,
        isBlocking = isBlocking,
        dependencies = dependencies,
        udas = udas.associate { it.key to it.value },
        recurrence = recurrence,
        nativeDates = nativeDates.associate { it.key to it.value },
        annotations = annotations.map { Annotation(entry = it.entry, description = it.description) },
    )

/** Only dirty fields are sent; the editor's original snapshot is the conflict baseline. */
fun Task.toEditPatch(
    original: Task,
    autoWaiting: Boolean = false,
): uniffi.taskchampion_android.TaskEditPatch {
    require(uuid == original.uuid)

    fun scalar(
        old: String?,
        new: String?,
    ): uniffi.taskchampion_android.StringEdit =
        if (old == new) {
            uniffi.taskchampion_android.StringEdit.Unchanged
        } else {
            uniffi.taskchampion_android.StringEdit.Replace(old, new)
        }

    fun set(
        old: List<String>,
        new: List<String>,
    ): uniffi.taskchampion_android.StringSetEdit =
        if (old.toSet() == new.toSet()) {
            uniffi.taskchampion_android.StringSetEdit.Unchanged
        } else {
            uniffi.taskchampion_android.StringSetEdit.Replace(old.distinct().sorted(), new.distinct().sorted())
        }

    fun dateChanged(
        old: String?,
        new: String?,
    ): Boolean {
        return if (old == new) {
            false
        } else {
            val oldInstant = com.brokenpip3.fatto.data.DateTimeUtils.parseToInstant(old)
            oldInstant == null || oldInstant != com.brokenpip3.fatto.data.DateTimeUtils.parseToInstant(new)
        }
    }

    val effectiveWait =
        if (autoWaiting &&
            !isRecurrenceTemplate &&
            wait.isNullOrBlank() &&
            (dateChanged(original.due, due) || dateChanged(original.scheduled, scheduled))
        ) {
            val target =
                listOfNotNull(
                    com.brokenpip3.fatto.data.DateTimeUtils.parseToInstant(due),
                    com.brokenpip3.fatto.data.DateTimeUtils.parseToInstant(scheduled),
                ).minOrNull()?.minus(7, java.time.temporal.ChronoUnit.DAYS)
            if (target?.isAfter(java.time.Instant.now()) == true) target.toString() else wait
        } else {
            wait
        }

    fun date(
        old: String?,
        new: String?,
        key: String,
    ): uniffi.taskchampion_android.StringEdit =
        if (!dateChanged(old, new)) {
            uniffi.taskchampion_android.StringEdit.Unchanged
        } else {
            uniffi.taskchampion_android.StringEdit.Replace(original.nativeDates[key] ?: old, new)
        }

    // Derived Wait depends on both dates: check even an unchanged source date
    // without rewriting it. Rust treats equal instants as a no-op.
    fun sourceDate(
        old: String?,
        new: String?,
        key: String,
    ): uniffi.taskchampion_android.StringEdit =
        if (effectiveWait != wait) {
            uniffi.taskchampion_android.StringEdit.Replace(original.nativeDates[key] ?: old, new)
        } else {
            date(old, new, key)
        }
    return uniffi.taskchampion_android.TaskEditPatch(
        uuid = uuid, expectedStatus = original.status,
        description = scalar(original.description, description), project = scalar(original.project, project),
        priority = scalar(original.priority, priority),
        due = sourceDate(original.due, due, "due"),
        wait = date(original.wait, effectiveWait, "wait"),
        scheduled = sourceDate(original.scheduled, scheduled, "scheduled"), start = date(original.start, start, "start"),
        tags = set(original.userTags, userTags), dependencies = set(original.dependencies, dependencies),
    )
}
