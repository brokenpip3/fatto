package com.brokenpip3.fatto.data

import com.brokenpip3.fatto.data.filter.AndExpression
import com.brokenpip3.fatto.data.filter.KeywordTerm
import com.brokenpip3.fatto.data.filter.MatchAll
import com.brokenpip3.fatto.data.filter.NotExpression
import com.brokenpip3.fatto.data.filter.OrExpression
import com.brokenpip3.fatto.data.filter.ProjectTerm
import com.brokenpip3.fatto.data.filter.SupportedVirtualTags
import com.brokenpip3.fatto.data.filter.TagTerm
import com.brokenpip3.fatto.data.filter.TaskFilterExpression
import com.brokenpip3.fatto.data.filter.TaskFilterExpressionParser
import com.brokenpip3.fatto.data.filter.VirtualTagTerm
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.TaskContext
import uniffi.taskchampion_android.TaskStatus
import java.time.Instant

object TaskContextMatcher {
    fun matches(
        task: Task,
        context: TaskContext?,
        now: Instant = Instant.now(),
    ): Boolean {
        val expression = parseExpression(context).getOrNull() ?: return false
        return matches(task, expression, now)
    }

    fun matches(
        task: Task,
        expression: TaskFilterExpression,
        now: Instant = Instant.now(),
    ): Boolean =
        when (expression) {
            MatchAll -> true
            is AndExpression -> expression.children.all { matches(task, it, now) }
            is OrExpression -> expression.children.any { matches(task, it, now) }
            is NotExpression -> !matches(task, expression.child, now)
            is TagTerm -> task.tags.contains(expression.name)
            is ProjectTerm ->
                task.project == expression.name ||
                    task.project?.startsWith("${expression.name}.") == true
            is KeywordTerm -> task.description.contains(expression.value, ignoreCase = true)
            is VirtualTagTerm -> matchesVirtualTag(task, expression.name, now)
        }

    fun parseError(context: TaskContext?): String? {
        if (context == null) return null
        return parseExpression(context).exceptionOrNull()?.message
    }

    fun parseExpression(context: TaskContext?): Result<TaskFilterExpression> =
        if (context == null) {
            Result.success(MatchAll)
        } else {
            TaskFilterExpressionParser.parse(context.expressionText)
        }

    private fun matchesVirtualTag(
        task: Task,
        tagName: String,
        now: Instant,
    ): Boolean =
        when (SupportedVirtualTags.normalize(tagName)) {
            "PENDING" -> task.status == TaskStatus.PENDING
            "COMPLETED" -> task.status == TaskStatus.COMPLETED
            "DUE" -> task.due != null
            "WAITING" ->
                task.status == TaskStatus.PENDING &&
                    task.wait
                        ?.let { runCatching { Instant.parse(it) }.getOrNull()?.isAfter(now) }
                        ?: false
            "ACTIVE" ->
                task.status == TaskStatus.PENDING &&
                    (task.start != null || task.tags.any { it.equals("ACTIVE", ignoreCase = true) })
            "BLOCKING" -> task.isBlocking
            "BLOCKED" -> task.isBlocked
            else -> false
        }
}
