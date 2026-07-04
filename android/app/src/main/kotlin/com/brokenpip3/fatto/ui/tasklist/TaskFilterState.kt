package com.brokenpip3.fatto.ui.tasklist

import com.brokenpip3.fatto.data.filter.AndExpression
import com.brokenpip3.fatto.data.filter.KeywordTerm
import com.brokenpip3.fatto.data.filter.MatchAll
import com.brokenpip3.fatto.data.filter.NotExpression
import com.brokenpip3.fatto.data.filter.OrExpression
import com.brokenpip3.fatto.data.filter.ProjectTerm
import com.brokenpip3.fatto.data.filter.TagTerm
import com.brokenpip3.fatto.data.filter.TaskFilterExpression
import com.brokenpip3.fatto.data.filter.TaskFilterExpressionFormatter
import com.brokenpip3.fatto.data.filter.TaskFilterExpressionParser
import com.brokenpip3.fatto.data.filter.VirtualTagTerm
import com.brokenpip3.fatto.data.model.TaskContext
import java.util.UUID

data class TaskFilterState(
    val descriptionQuery: String = "",
    val project: String? = null,
    val tags: Set<String> = emptySet(),
    val rawExpressionText: String? = null,
) {
    fun toContext(
        name: String,
        id: String? = null,
    ): TaskContext =
        TaskContext(
            id = id ?: UUID.randomUUID().toString(),
            name = name.trim(),
            expressionText =
                rawExpressionText?.trim()
                    ?: buildList {
                        if (descriptionQuery.isNotBlank()) add(TaskFilterExpressionFormatter.keyword(descriptionQuery))
                        project?.trim()?.takeIf { it.isNotBlank() }?.let {
                            add(TaskFilterExpressionFormatter.term("project:$it"))
                        }
                        tags.sorted().forEach { add(TaskFilterExpressionFormatter.term("+$it")) }
                    }.joinToString(" "),
        )

    companion object {
        fun fromContext(context: TaskContext): TaskFilterState = TaskFilterState().fromExpression(context.expressionText)

        private fun TaskFilterState.fromExpression(expressionText: String): TaskFilterState {
            val parsed =
                TaskFilterExpressionParser.parse(expressionText).getOrNull()
                    ?: return fallback(expressionText)
            return extractFields(parsed) ?: fallback(expressionText)
        }

        private fun fallback(expressionText: String): TaskFilterState = TaskFilterState(rawExpressionText = expressionText.trim())

        private fun extractFields(expression: TaskFilterExpression): TaskFilterState? {
            val keywords = mutableListOf<String>()
            var project: String? = null
            val tags = linkedSetOf<String>()

            fun collect(node: TaskFilterExpression): Boolean =
                when (node) {
                    MatchAll -> true
                    is AndExpression -> node.children.all { collect(it) }
                    is KeywordTerm -> {
                        if (node.value.isBlank()) return false
                        keywords += node.value
                        true
                    }
                    is ProjectTerm -> {
                        if (node.name.isBlank() || project != null) return false
                        project = node.name
                        true
                    }
                    is TagTerm -> {
                        if (node.name.isBlank() || !tags.add(node.name)) return false
                        true
                    }
                    is NotExpression,
                    is OrExpression,
                    is VirtualTagTerm,
                    -> false
                }

            if (!collect(expression)) return null
            return TaskFilterState(
                descriptionQuery = keywords.joinToString(" "),
                project = project,
                tags = tags,
            )
        }
    }
}
