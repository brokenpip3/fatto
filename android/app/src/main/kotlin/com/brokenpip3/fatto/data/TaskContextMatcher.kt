package com.brokenpip3.fatto.data

import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.TaskContext

object TaskContextMatcher {
    fun matches(
        task: Task,
        context: TaskContext?,
    ): Boolean {
        if (context == null) return true

        val matchesDescription =
            context.descriptionQuery.isBlank() ||
                task.description.contains(context.descriptionQuery, ignoreCase = true)
        val matchesProject =
            context.project.isNullOrBlank() ||
                task.project == context.project ||
                task.project?.startsWith("${context.project}.") == true
        val matchesTags = context.tags.isEmpty() || task.tags.containsAll(context.tags)

        return matchesDescription && matchesProject && matchesTags
    }
}
