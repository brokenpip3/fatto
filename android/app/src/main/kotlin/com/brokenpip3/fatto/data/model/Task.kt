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
) {
    val userTags: List<String>
        get() = tags.filter { !isSynthetic(it) }.sorted()
}

val INTERNAL_TAGS = setOf("BLOCKING", "ACTIVE", "BLOCKED", "WAITING")

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
    )
