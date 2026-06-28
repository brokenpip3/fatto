package com.brokenpip3.fatto.data.model

import java.util.UUID

data class TaskContext(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val descriptionQuery: String = "",
    val project: String? = null,
    val tags: Set<String> = emptySet(),
) {
    fun summary(): String {
        val parts =
            buildList {
                if (descriptionQuery.isNotBlank()) add("Search: $descriptionQuery")
                if (!project.isNullOrBlank()) add("Project: $project")
                if (tags.isNotEmpty()) add("Tags: ${tags.sorted().joinToString(", ")}")
            }
        return if (parts.isEmpty()) "All tasks" else parts.joinToString(" · ")
    }
}
