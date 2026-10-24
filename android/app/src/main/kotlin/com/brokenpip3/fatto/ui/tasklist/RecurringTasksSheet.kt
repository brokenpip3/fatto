package com.brokenpip3.fatto.ui.tasklist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.brokenpip3.fatto.data.DateTimeUtils
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.dateValue
import com.brokenpip3.fatto.ui.common.readableTaskDate
import com.brokenpip3.fatto.ui.theme.FattoMetrics
import com.brokenpip3.fatto.ui.theme.FattoSpacing
import uniffi.taskchampion_android.RecurrenceDiagnostic
import uniffi.taskchampion_android.TaskStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringTasksSheet(
    templates: List<Task>,
    allTasks: List<Task>,
    diagnostics: List<RecurrenceDiagnostic>,
    onDismiss: () -> Unit,
    onOccurrenceClick: (Task) -> Unit,
    onEditSeries: ((Task) -> Unit)? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(FattoSpacing.large),
            verticalArrangement = Arrangement.spacedBy(FattoSpacing.medium),
        ) {
            Text("Recurring series", style = MaterialTheme.typography.headlineSmall)
            if (templates.isEmpty()) Text("No active recurring series", style = MaterialTheme.typography.bodyLarge)
            templates.sortedBy { it.description }.forEach { template ->
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
                    Column(
                        Modifier.fillMaxWidth().padding(FattoSpacing.medium),
                        verticalArrangement = Arrangement.spacedBy(FattoSpacing.small),
                    ) {
                        Text(template.description, style = MaterialTheme.typography.titleSmall)
                        Text(recurrenceLabel(template.recurrence?.recur), style = MaterialTheme.typography.bodyMedium)
                        val children =
                            allTasks.filter { it.recurrence?.parent.equals(template.uuid, ignoreCase = true) }
                                .sortedWith(compareBy<Task> { it.due ?: "9999" }.thenBy { it.uuid })
                        val next = children.firstOrNull { it.status == TaskStatus.Pending }
                        if (children.isEmpty()) {
                            Text("No occurrences synced yet", style = MaterialTheme.typography.bodyMedium)
                        } else if (next != null) {
                            Text(
                                "Next synced occurrence: ${readableTaskDate(next.dateValue("due"))}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (onEditSeries != null) TextButton(onClick = { onEditSeries(template) }) { Text("Edit series") }
                        var history by remember(template.uuid) { mutableStateOf(false) }
                        if (children.isNotEmpty()) {
                            AccordionSection("Occurrences", Icons.Default.Info, children.size, history, { history = !history }) {
                                children.forEach { child ->
                                    TextButton(onClick = { onOccurrenceClick(child) }) {
                                        Text(
                                            "${child.description} · ${readableTaskDate(child.dateValue("due"))} · " +
                                                statusLabel(child.status),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Include orphans even when no active template remains.
            if (diagnostics.isNotEmpty()) {
                var showDiagnostics by remember { mutableStateOf(false) }
                AccordionSection("Recurrence issues", Icons.Default.Info, diagnostics.size, showDiagnostics, {
                    showDiagnostics =
                        !showDiagnostics
                }) {
                    diagnostics.forEach { diagnostic ->
                        val name =
                            allTasks.find { it.uuid.equals(diagnostic.taskUuid, ignoreCase = true) }?.description ?: diagnostic.taskUuid
                        Text("$name: ${diagnostic.message}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private fun statusLabel(status: TaskStatus): String =
    when (status) {
        TaskStatus.Pending -> "Pending"
        TaskStatus.Completed -> "Completed"
        TaskStatus.Deleted -> "Deleted"
        TaskStatus.Recurring -> "Series"
        is TaskStatus.Unknown -> status.value
    }

internal fun readableDate(value: String?): String =
    when {
        value == null -> "No date"
        else -> DateTimeUtils.formatLocalDate(value) ?: "Imported date"
    }

@Composable
internal fun OccurrenceSeriesRow(
    task: Task,
    allTasks: List<Task>,
    diagnostics: List<RecurrenceDiagnostic>,
    enabled: Boolean,
    onEditSeries: ((Task) -> Unit)?,
) {
    val parent = allTasks.find { it.uuid.equals(task.recurrence?.parent, ignoreCase = true) }
    val interval = task.recurrence?.recur ?: parent?.recurrence?.recur
    Column(verticalArrangement = Arrangement.spacedBy(FattoSpacing.small)) {
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small),
        ) {
            Icon(
                Icons.Default.Repeat, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(FattoMetrics.smallIcon),
            )
            Text(
                interval?.let {
                    recurrenceLabel(it)
                } ?: "Unknown repeat rule",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
            )
            if (parent?.status == TaskStatus.Recurring && onEditSeries != null) {
                TextButton(onClick = { onEditSeries(parent) }, enabled = enabled) { Text("Edit series") }
            }
        }
        if (parent == null) {
            Text("Series unavailable. Sync to load its details.", style = MaterialTheme.typography.bodySmall)
        } else if (parent.status != TaskStatus.Recurring) {
            Text("Series stopped", style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()
        if (diagnostics.any {
                it.taskUuid.equals(task.uuid, ignoreCase = true) ||
                    it.taskUuid.equals(task.recurrence?.parent, ignoreCase = true)
            }
        ) {
            RecurrenceDetails(task, allTasks, diagnostics)
        }
    }
}

@Composable
internal fun RecurrenceDetails(
    task: Task,
    allTasks: List<Task>,
    diagnostics: List<RecurrenceDiagnostic>,
) {
    val metadata = task.recurrence ?: return
    val parent = allTasks.find { it.uuid.equals(metadata.parent, ignoreCase = true) }
    var technicalDetails by remember(task.uuid) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(FattoSpacing.small)) {
        Text(
            (metadata.recur ?: parent?.recurrence?.recur)?.let {
                recurrenceLabel(it)
            } ?: "Unknown repeat rule",
            style = MaterialTheme.typography.bodyLarge,
        )
        if (metadata.parent != null) {
            if (parent == null) {
                Text("Series unavailable. Sync to load its details.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(parent.description, style = MaterialTheme.typography.titleSmall)
                if (parent.status != TaskStatus.Recurring) Text("Series stopped", style = MaterialTheme.typography.bodyMedium)
            }
        }
        val until = metadata.until ?: parent?.recurrence?.until
        val untilRaw = metadata.untilRaw ?: parent?.recurrence?.untilRaw
        if (until != null || untilRaw != null) {
            Text("Until: ${readableDate(until ?: untilRaw)}", style = MaterialTheme.typography.bodyMedium)
            Text("After this date, Taskwarrior deletes unfinished occurrences.", style = MaterialTheme.typography.bodySmall)
        }
        val issues =
            diagnostics.filter {
                it.taskUuid.equals(task.uuid, ignoreCase = true) || it.taskUuid.equals(metadata.parent, ignoreCase = true)
            }
        if (issues.isNotEmpty()) {
            AccordionSection("Recurrence issues", Icons.Default.Info, null, technicalDetails, {
                technicalDetails = !technicalDetails
            }) {
                metadata.parent?.let { Text("Series ID: $it", style = MaterialTheme.typography.bodySmall) }
                metadata.imask?.let { Text("Occurrence index: $it", style = MaterialTheme.typography.bodySmall) }
                untilRaw?.let { Text("Imported Until: $it", style = MaterialTheme.typography.bodySmall) }
                issues.forEach { Text(it.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
