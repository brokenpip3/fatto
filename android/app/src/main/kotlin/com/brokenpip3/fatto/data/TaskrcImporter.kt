package com.brokenpip3.fatto.data

import com.brokenpip3.fatto.data.filter.TaskFilterExpressionParser
import com.brokenpip3.fatto.data.model.TaskContext
import java.util.Calendar
import java.util.Locale
import java.util.UUID

enum class TaskrcImportResultType {
    ADDED,
    UPDATED,
    UNCHANGED,
    ACTIVATED,
    SKIPPED,
    ERROR,
}

data class TaskrcImportAction(
    val type: TaskrcImportResultType,
    val lineNumber: Int,
    val key: String,
    val message: String,
)

data class TaskrcImportPreview(
    val actions: List<TaskrcImportAction>,
    val contextsAfter: List<TaskContext>,
    val activeContextIdAfter: String?,
    val firstDayOfWeekAfter: Int,
) {
    val hasErrors: Boolean = actions.any { it.type == TaskrcImportResultType.ERROR }
}

object TaskrcImporter {
    fun preview(
        text: String,
        existingContexts: List<TaskContext>,
        currentActiveContextId: String?,
        currentFirstDayOfWeek: Int,
    ): TaskrcImportPreview {
        val actions = mutableListOf<TaskrcImportAction>()
        val contextsByName = existingContexts.associateBy { it.name }.toMutableMap()
        var requestedActiveName: String? = null
        var activeContextId = currentActiveContextId
        var firstDayOfWeek = currentFirstDayOfWeek

        text.lines().forEachIndexed { index, rawLine ->
            val lineNumber = index + 1
            parseLine(rawLine)?.let { entry ->
                when {
                    entry.isInclude ->
                        actions += TaskrcImportAction(TaskrcImportResultType.SKIPPED, lineNumber, "include", "Includes are not imported")

                    entry.key == "context" ->
                        requestedActiveName = entry.value

                    entry.key == "weekstart" ->
                        actions += previewWeekstart(lineNumber, entry.value, firstDayOfWeek) { firstDayOfWeek = it }

                    entry.key.startsWith("context.") && entry.key.endsWith(".read") ->
                        actions += previewContextRead(lineNumber, entry.key, entry.value, contextsByName)

                    entry.key.startsWith("context.") && entry.key.contains(".write") ->
                        actions +=
                            TaskrcImportAction(
                                TaskrcImportResultType.SKIPPED,
                                lineNumber,
                                entry.key,
                                "Context write modifications are not supported",
                            )

                    entry.key.startsWith("context.") && entry.key.contains(".rc.") ->
                        actions +=
                            TaskrcImportAction(
                                TaskrcImportResultType.SKIPPED,
                                lineNumber,
                                entry.key,
                                "Context rc overrides are not supported",
                            )

                    else ->
                        actions += TaskrcImportAction(TaskrcImportResultType.SKIPPED, lineNumber, entry.key, "Unsupported taskrc key")
                }
            }
        }

        requestedActiveName?.let { name ->
            val activeContext = contextsByName[name]
            if (activeContext == null) {
                actions += TaskrcImportAction(TaskrcImportResultType.ERROR, 0, "context", "Active context '$name' was not imported")
            } else {
                activeContextId = activeContext.id
                actions += TaskrcImportAction(TaskrcImportResultType.ACTIVATED, 0, "context", "Active context set to '$name'")
            }
        }

        return TaskrcImportPreview(
            actions = actions,
            contextsAfter = contextsByName.values.sortedBy { it.name.lowercase(Locale.ROOT) },
            activeContextIdAfter = activeContextId,
            firstDayOfWeekAfter = firstDayOfWeek,
        )
    }

    private fun parseLine(rawLine: String): TaskrcLine? {
        val line = rawLine.substringBefore("#").trim()
        return when {
            line.isBlank() -> null
            line.startsWith("include ") ->
                TaskrcLine(key = "include", value = line.removePrefix("include").trim(), isInclude = true)
            !line.contains("=") ->
                TaskrcLine(key = line, value = "")
            else ->
                TaskrcLine(
                    key = line.substringBefore("=").trim(),
                    value = line.substringAfter("=").trim(),
                )
        }
    }

    private fun previewWeekstart(
        lineNumber: Int,
        value: String,
        currentValue: Int,
        update: (Int) -> Unit,
    ): TaskrcImportAction {
        val parsed =
            when (value.lowercase(Locale.ROOT)) {
                "sunday" -> Calendar.SUNDAY
                "monday" -> Calendar.MONDAY
                else -> null
            }
        return when {
            parsed == null ->
                TaskrcImportAction(TaskrcImportResultType.ERROR, lineNumber, "weekstart", "Unsupported weekstart '$value'")
            parsed == currentValue ->
                TaskrcImportAction(TaskrcImportResultType.UNCHANGED, lineNumber, "weekstart", "Week already starts on $value")
            else -> {
                update(parsed)
                TaskrcImportAction(TaskrcImportResultType.UPDATED, lineNumber, "weekstart", "Weekstart changed to $value")
            }
        }
    }

    private fun previewContextRead(
        lineNumber: Int,
        key: String,
        value: String,
        contextsByName: MutableMap<String, TaskContext>,
    ): TaskrcImportAction {
        val name = key.removePrefix("context.").removeSuffix(".read")
        val parseError = TaskFilterExpressionParser.parse(value).exceptionOrNull()
        return when {
            name.isBlank() ->
                TaskrcImportAction(TaskrcImportResultType.ERROR, lineNumber, key, "Empty context name")
            parseError != null ->
                TaskrcImportAction(TaskrcImportResultType.ERROR, lineNumber, key, parseError.message ?: "Invalid context expression")
            else -> {
                val existing = contextsByName[name]
                contextsByName[name] =
                    existing?.copy(expressionText = value)
                        ?: TaskContext(id = UUID.randomUUID().toString(), name = name, expressionText = value)

                val type =
                    when {
                        existing == null -> TaskrcImportResultType.ADDED
                        existing.expressionText == value -> TaskrcImportResultType.UNCHANGED
                        else -> TaskrcImportResultType.UPDATED
                    }
                TaskrcImportAction(type, lineNumber, key, "Context '$name'")
            }
        }
    }

    private data class TaskrcLine(
        val key: String,
        val value: String,
        val isInclude: Boolean = false,
    )
}
