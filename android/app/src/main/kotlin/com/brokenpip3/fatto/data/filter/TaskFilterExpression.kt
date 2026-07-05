package com.brokenpip3.fatto.data.filter

sealed interface TaskFilterExpression

data object MatchAll : TaskFilterExpression

data class AndExpression(
    val children: List<TaskFilterExpression>,
) : TaskFilterExpression

data class OrExpression(
    val children: List<TaskFilterExpression>,
) : TaskFilterExpression

data class NotExpression(
    val child: TaskFilterExpression,
) : TaskFilterExpression

data class TagTerm(
    val name: String,
) : TaskFilterExpression

data class ProjectTerm(
    val name: String,
) : TaskFilterExpression

data class KeywordTerm(
    val value: String,
) : TaskFilterExpression

data class VirtualTagTerm(
    val name: String,
) : TaskFilterExpression

class TaskFilterParseException(message: String) : IllegalArgumentException(message)

object SupportedVirtualTags {
    val names =
        listOf(
            "PENDING",
            "COMPLETED",
            "DUE",
            "WAITING",
            "ACTIVE",
            "BLOCKING",
            "BLOCKED",
        )

    fun normalize(name: String): String? = names.firstOrNull { it.equals(name, ignoreCase = true) }
}

object TaskFilterExpressionFormatter {
    fun keyword(value: String): String = term(value, quoteLeadingSign = true)

    fun term(value: String): String = term(value, quoteLeadingSign = false)

    private fun term(
        value: String,
        quoteLeadingSign: Boolean,
    ): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return trimmed
        val needsQuote =
            trimmed.any { it.isWhitespace() || it == '(' || it == ')' || it == '"' } ||
                trimmed.equals("and", ignoreCase = true) ||
                trimmed.equals("or", ignoreCase = true) ||
                (quoteLeadingSign && (trimmed.startsWith("+") || trimmed.startsWith("-")))
        return if (needsQuote) {
            "\"${trimmed.replace("\"", "")}\""
        } else {
            trimmed
        }
    }
}
