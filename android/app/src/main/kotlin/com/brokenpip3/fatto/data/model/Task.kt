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
    val modified: String? = null,
    val dateModes: uniffi.taskchampion_android.DateDisplayModes = calendarDateModes(),
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
        modified = modified,
        dateModes = dateModes,
        annotations = annotations.map { Annotation(entry = it.entry, description = it.description) },
    )

/** Only dirty fields are sent; the editor's original snapshot is the conflict baseline. */
fun Task.toEditPatch(
    original: Task,
    autoWaiting: Boolean = false,
    explicitDateClears: Set<String> = emptySet(),
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

    val source =
        listOf("due" to due, "scheduled" to scheduled)
            .mapNotNull { (field, raw) ->
                com.brokenpip3.fatto.data.DateTimeUtils.parseToInstant(raw)?.let { Triple(field, raw, it) }
            }.minByOrNull { it.third }
    val derivedWait =
        if (autoWaiting &&
            !isRecurrenceTemplate &&
            wait.isNullOrBlank() &&
            original.nativeDates["wait"] == null &&
            "wait" !in explicitDateClears &&
            (dateChanged(original.due, due) || dateChanged(original.scheduled, scheduled))
        ) {
            source?.third?.minus(7, java.time.temporal.ChronoUnit.DAYS)?.takeIf { it.isAfter(java.time.Instant.now()) }
        } else {
            null
        }
    val effectiveWait = derivedWait?.toString() ?: wait
    val effectiveModes = if (derivedWait != null) dateModes.withField("wait", dateModes.forField(source!!.first)) else dateModes

    fun modeChanged(key: String): Boolean = effectiveModes.forField(key) != original.dateModes.forField(key)

    fun date(
        old: String?,
        new: String?,
        key: String,
    ): uniffi.taskchampion_android.StringEdit {
        val checkedSource = derivedWait != null && key in setOf("due", "scheduled")
        val modeEdit = key in setOf("due", "scheduled", "wait") && modeChanged(key)
        return when {
            dateChanged(old, new) || key in explicitDateClears || modeEdit ->
                uniffi.taskchampion_android.StringEdit.Replace(original.nativeDates[key] ?: old, new)
            checkedSource -> uniffi.taskchampion_android.StringEdit.Check(original.nativeDates[key] ?: old)
            else -> uniffi.taskchampion_android.StringEdit.Unchanged
        }
    }
    val dueEdit = date(original.due, due, "due")
    val waitEdit = date(original.wait, effectiveWait, "wait")
    val scheduledEdit = date(original.scheduled, scheduled, "scheduled")

    fun mode(
        key: String,
        edit: uniffi.taskchampion_android.StringEdit,
    ): uniffi.taskchampion_android.DateModeEdit {
        val baseline = original.dateDisplayBaseline(key)
        return when {
            key in explicitDateClears || (edit is uniffi.taskchampion_android.StringEdit.Replace && edit.value == null) ->
                uniffi.taskchampion_android.DateModeEdit.Replace(baseline, uniffi.taskchampion_android.DateDisplayMode.CALENDAR_DATE)
            modeChanged(key) ->
                uniffi.taskchampion_android.DateModeEdit.Replace(baseline, effectiveModes.forField(key))
            edit != uniffi.taskchampion_android.StringEdit.Unchanged -> uniffi.taskchampion_android.DateModeEdit.Check(baseline)
            else -> uniffi.taskchampion_android.DateModeEdit.Unchanged
        }
    }
    return uniffi.taskchampion_android.TaskEditPatch(
        dateModes =
            uniffi.taskchampion_android.DateDisplayModesPatch(
                mode(
                    "due",
                    dueEdit,
                ),
                mode(
                    "scheduled",
                    scheduledEdit,
                ),
                mode(
                    "wait",
                    waitEdit,
                ),
            ),
        uuid = uuid, expectedStatus = original.status,
        description = scalar(original.description, description), project = scalar(original.project, project),
        priority = scalar(original.priority, priority),
        due = dueEdit,
        wait = waitEdit,
        scheduled = scheduledEdit, start = date(original.start, start, "start"),
        tags = set(original.userTags, userTags), dependencies = set(original.dependencies, dependencies),
    )
}
