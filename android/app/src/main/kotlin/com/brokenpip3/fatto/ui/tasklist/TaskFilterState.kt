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

enum class ContextBuilderTermKind {
    KEYWORD,
    PROJECT,
    TAG,
    VIRTUAL_TAG,
}

enum class ContextBuilderPolarity {
    INCLUDE,
    EXCLUDE,
}

data class ContextBuilderTerm(
    val kind: ContextBuilderTermKind,
    val value: String,
    val polarity: ContextBuilderPolarity = ContextBuilderPolarity.INCLUDE,
)

data class TaskFilterState(
    val descriptionQuery: String = "",
    val project: String? = null,
    val tags: Set<String> = emptySet(),
    val rawExpressionText: String? = null,
    val builderTerms: List<ContextBuilderTerm> = emptyList(),
) {
    fun expressionText(): String =
        rawExpressionText?.trim()?.takeIf { it.isNotBlank() }
            ?: builderTerms
                .toExpressionText()
                .takeIf { it.isNotBlank() }
            ?: buildList {
                if (descriptionQuery.isNotBlank()) add(TaskFilterExpressionFormatter.keyword(descriptionQuery))
                project?.trim()?.takeIf { it.isNotBlank() }?.let {
                    add(TaskFilterExpressionFormatter.term("project:$it"))
                }
                tags.sorted().forEach { add(TaskFilterExpressionFormatter.term("+$it")) }
            }.joinToString(" ")

    fun toContext(
        name: String,
        id: String? = null,
    ): TaskContext =
        TaskContext(
            id = id ?: UUID.randomUUID().toString(),
            name = name.trim(),
            expressionText = expressionText(),
        )

    companion object {
        fun fromContext(context: TaskContext): TaskFilterState = TaskFilterState().fromExpression(context.expressionText)

        private fun TaskFilterState.fromExpression(expressionText: String): TaskFilterState {
            val parsed =
                TaskFilterExpressionParser.parse(expressionText).getOrNull()
                    ?: return fallback(expressionText)
            return flattenBuildableTerms(parsed)?.let(::stateFromBuilderTerms) ?: fallback(expressionText)
        }

        private fun fallback(expressionText: String): TaskFilterState = TaskFilterState(rawExpressionText = expressionText.trim())

        private fun flattenBuildableTerms(expression: TaskFilterExpression): List<ContextBuilderTerm>? =
            when (expression) {
                MatchAll -> emptyList()
                is AndExpression ->
                    expression.children.flatMap { child ->
                        flattenBuildableTerms(child) ?: return null
                    }
                is OrExpression ->
                    expression.children.map { child ->
                        val project = child as? ProjectTerm ?: return null
                        ContextBuilderTerm(ContextBuilderTermKind.PROJECT, project.name)
                    }
                else -> expression.toBuilderTerm()?.let(::listOf)
            }?.takeIf { terms -> terms.all { it.value.isNotBlank() } }

        private fun stateFromBuilderTerms(terms: List<ContextBuilderTerm>): TaskFilterState {
            val simple = terms.all { it.polarity == ContextBuilderPolarity.INCLUDE && it.kind != ContextBuilderTermKind.VIRTUAL_TAG }
            val projectTerms = terms.filter { it.kind == ContextBuilderTermKind.PROJECT }
            if (!simple || projectTerms.size > 1) {
                return TaskFilterState(
                    descriptionQuery = terms.filter { it.kind == ContextBuilderTermKind.KEYWORD }.joinToString(" ") { it.value },
                    tags = terms.filter { it.kind == ContextBuilderTermKind.TAG }.map { it.value }.toSet(),
                    builderTerms = terms,
                )
            }
            val keywords = terms.filter { it.kind == ContextBuilderTermKind.KEYWORD }
            val projectTerm = projectTerms.firstOrNull()
            val tagTerms = terms.filter { it.kind == ContextBuilderTermKind.TAG }
            val canonicalTerms =
                buildList {
                    addAll(keywords)
                    projectTerm?.let { add(it) }
                    addAll(tagTerms)
                }
            return TaskFilterState(
                descriptionQuery = keywords.joinToString(" ") { it.value },
                project = projectTerm?.value,
                tags = tagTerms.map { it.value }.toSet(),
                builderTerms = canonicalTerms,
            )
        }
    }
}

private fun List<ContextBuilderTerm>.toExpressionText(): String {
    val includedProjects =
        filter {
            it.kind == ContextBuilderTermKind.PROJECT &&
                it.polarity == ContextBuilderPolarity.INCLUDE &&
                it.value.isNotBlank()
        }
    if (includedProjects.size <= 1) {
        return mapNotNull { it.toExpressionToken() }.joinToString(" ")
    }
    val nonIncludedProjects =
        filterNot {
            it.kind == ContextBuilderTermKind.PROJECT &&
                it.polarity == ContextBuilderPolarity.INCLUDE
        }
    val projectExpression =
        when (includedProjects.size) {
            0 -> null
            1 -> includedProjects.first().toExpressionToken()
            else ->
                includedProjects
                    .mapNotNull { it.toExpressionToken() }
                    .joinToString(" or ", prefix = "(", postfix = ")")
        }
    return buildList {
        projectExpression?.let { add(it) }
        addAll(nonIncludedProjects.mapNotNull { it.toExpressionToken() })
    }.joinToString(" ")
}

private fun TaskFilterExpression.toBuilderTerm(): ContextBuilderTerm? {
    val polarity =
        if (this is NotExpression) {
            ContextBuilderPolarity.EXCLUDE
        } else {
            ContextBuilderPolarity.INCLUDE
        }
    return when (val term = if (this is NotExpression) child else this) {
        is KeywordTerm -> ContextBuilderTerm(ContextBuilderTermKind.KEYWORD, term.value, polarity)
        is ProjectTerm -> ContextBuilderTerm(ContextBuilderTermKind.PROJECT, term.name, polarity)
        is TagTerm -> ContextBuilderTerm(ContextBuilderTermKind.TAG, term.name, polarity)
        is VirtualTagTerm -> ContextBuilderTerm(ContextBuilderTermKind.VIRTUAL_TAG, term.name, polarity)
        else -> null
    }
}

private fun ContextBuilderTerm.toExpressionToken(): String? {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return null
    val token =
        when (kind) {
            ContextBuilderTermKind.KEYWORD -> TaskFilterExpressionFormatter.keyword(trimmed)
            ContextBuilderTermKind.PROJECT -> TaskFilterExpressionFormatter.term("project:$trimmed")
            ContextBuilderTermKind.TAG -> TaskFilterExpressionFormatter.term("+$trimmed")
            ContextBuilderTermKind.VIRTUAL_TAG -> TaskFilterExpressionFormatter.term("+${trimmed.uppercase()}")
        }
    return if (polarity == ContextBuilderPolarity.EXCLUDE) {
        when (kind) {
            ContextBuilderTermKind.KEYWORD -> "-$token"
            ContextBuilderTermKind.PROJECT -> "-$token"
            ContextBuilderTermKind.TAG,
            ContextBuilderTermKind.VIRTUAL_TAG,
            -> "-$token"
        }
    } else {
        token
    }
}
