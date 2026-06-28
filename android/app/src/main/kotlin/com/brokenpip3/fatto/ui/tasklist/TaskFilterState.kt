package com.brokenpip3.fatto.ui.tasklist

import com.brokenpip3.fatto.data.model.TaskContext
import java.util.UUID

data class TaskFilterState(
    val descriptionQuery: String = "",
    val project: String? = null,
    val tags: Set<String> = emptySet(),
) {
    fun toContext(
        name: String,
        id: String? = null,
    ): TaskContext =
        TaskContext(
            id = id ?: UUID.randomUUID().toString(),
            name = name.trim(),
            descriptionQuery = descriptionQuery.trim(),
            project = project?.trim()?.takeIf { it.isNotBlank() },
            tags = tags,
        )

    companion object {
        fun fromContext(context: TaskContext): TaskFilterState =
            TaskFilterState(
                descriptionQuery = context.descriptionQuery,
                project = context.project,
                tags = context.tags,
            )
    }
}
