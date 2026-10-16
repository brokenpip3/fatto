package com.brokenpip3.fatto.ui.tasklist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.brokenpip3.fatto.data.DateTimeUtils
import com.brokenpip3.fatto.data.model.INTERNAL_TAGS
import com.brokenpip3.fatto.data.model.RecurrenceRule
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.TaskEditResult
import com.brokenpip3.fatto.data.model.isSynthetic
import com.brokenpip3.fatto.data.model.toEditPatch
import com.brokenpip3.fatto.ui.common.ProjectPickerDialog
import com.brokenpip3.fatto.ui.common.TagPickerDialog
import com.brokenpip3.fatto.ui.theme.FattoFieldDefaults
import com.brokenpip3.fatto.ui.theme.FattoMetrics
import com.brokenpip3.fatto.ui.theme.FattoSpacing
import com.brokenpip3.fatto.ui.theme.effectiveFontScale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import uniffi.taskchampion_android.RecurrenceRulePatch
import uniffi.taskchampion_android.StringEdit
import uniffi.taskchampion_android.TaskEditPatch
import uniffi.taskchampion_android.TaskStatus
import java.time.Instant

class SeriesEditorActions(
    val save: suspend (TaskEditPatch, RecurrenceRulePatch, Boolean) -> TaskEditResult,
    val stop: suspend (String) -> TaskEditResult,
)

private enum class SeriesDate(val label: String) {
    Due("First due date"),
    Scheduled("Scheduled"),
    Wait("Wait"),
    Until("Until"),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
@Composable
fun RecurrenceTemplateSheet(
    task: Task,
    allTasks: List<Task>,
    onDismiss: () -> Unit,
    onSave: suspend (TaskEditPatch, RecurrenceRulePatch, Boolean) -> TaskEditResult,
    onStop: suspend () -> TaskEditResult,
    showInternalTags: Boolean = false,
    embedded: Boolean = false,
    visible: Boolean = true,
    onStateChange: (Boolean, Boolean) -> Unit = { _, _ -> },
) {
    var baseline by remember(task.uuid) { mutableStateOf(task) }
    var description by remember(baseline) { mutableStateOf(baseline.description) }
    var project by remember(baseline) { mutableStateOf(baseline.project.orEmpty()) }
    var tags by remember(baseline) { mutableStateOf(baseline.userTags) }
    var newTag by remember(baseline) { mutableStateOf("") }
    var showTagPicker by remember { mutableStateOf(false) }
    var priority by remember(baseline) { mutableStateOf(baseline.priority) }
    var due by remember(baseline) { mutableStateOf(baseline.due) }
    var wait by remember(baseline) { mutableStateOf(baseline.wait) }
    var scheduled by remember(baseline) { mutableStateOf(baseline.scheduled) }
    var dirtyDates by remember(baseline) { mutableStateOf(emptySet<String>()) }
    val originalRule = RecurrenceRule(baseline.recurrence?.recur.orEmpty(), baseline.recurrence?.until ?: baseline.recurrence?.untilRaw)
    var rule by remember(baseline) { mutableStateOf(originalRule) }
    var changingSchedule by remember(baseline) { mutableStateOf(false) }
    var saving by remember(task.uuid) { mutableStateOf(false) }
    var error by remember(task.uuid) { mutableStateOf<String?>(null) }
    var confirmStop by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var showProjectPicker by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<SeriesDate?>(null) }
    var refreshAfterSave by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val latest = allTasks.find { it.uuid.equals(task.uuid, ignoreCase = true) } ?: task
    val generated =
        baseline.recurrence?.mask != null ||
            latest.recurrence?.mask != null ||
            allTasks.any { it.recurrence?.parent.equals(baseline.uuid, ignoreCase = true) }
    val replacement = generated && changingSchedule
    val scheduleChanged = dirtyDates.isNotEmpty() || rule != originalRule
    val contentChanged =
        description != baseline.description ||
            project != baseline.project.orEmpty() ||
            priority != baseline.priority ||
            tags.toSet() != baseline.userTags.toSet()
    val dirty = contentChanged || scheduleChanged
    val active = latest.status == TaskStatus.Recurring
    val validation = if (changingSchedule && (scheduleChanged || replacement)) rule.creationError(due) else null
    SideEffect { onStateChange(saving, dirty) }

    fun resetDraft(snapshot: Task) {
        baseline = snapshot
        description = snapshot.description
        project = snapshot.project.orEmpty()
        tags = snapshot.userTags
        newTag = ""
        priority = snapshot.priority
        due = snapshot.due
        wait = snapshot.wait
        scheduled = snapshot.scheduled
        dirtyDates = emptySet()
        rule = RecurrenceRule(snapshot.recurrence?.recur.orEmpty(), snapshot.recurrence?.until ?: snapshot.recurrence?.untilRaw)
        changingSchedule = false
        picker = null
        showTagPicker = false
        showProjectPicker = false
        error = null
    }
    LaunchedEffect(refreshAfterSave, latest) {
        if (refreshAfterSave) {
            resetDraft(latest)
            refreshAfterSave = false
        }
    }

    fun resetSchedule() {
        due = baseline.due
        wait = baseline.wait
        scheduled = baseline.scheduled
        dirtyDates = emptySet()
        rule = originalRule
        changingSchedule = false
        picker = null
    }

    fun discardSeries() {
        resetDraft(baseline)
        onDismiss()
    }

    fun cancel() {
        if (changingSchedule) resetSchedule() else discardSeries()
    }

    fun back() {
        if (!saving) {
            if (changingSchedule) {
                resetSchedule()
            } else if (embedded || !dirty) {
                onDismiss()
            } else {
                confirmClose = true
            }
        }
    }

    fun accept(result: TaskEditResult) {
        when (result) {
            TaskEditResult.Success -> {
                refreshAfterSave = true
                dirtyDates = emptySet()
                error = null
                changingSchedule = false
                onDismiss()
            }
            is TaskEditResult.Failure -> error = result.message
        }
    }
    val saveSeries: () -> Unit = {
        val metadata = baseline.recurrence
        if (metadata == null) {
            error = "Series metadata is unavailable"
        } else if (validation != null) {
            error = validation
        } else {
            var patch =
                baseline.copy(
                    description = description,
                    project = if (project == baseline.project.orEmpty()) baseline.project else project.takeIf { it.isNotBlank() },
                    priority = priority,
                    tags = tags,
                ).toEditPatch(baseline)

            fun edit(
                key: String,
                value: String?,
                original: String?,
            ) = if (key in dirtyDates) {
                StringEdit.Replace(baseline.nativeDates[key] ?: original, value)
            } else {
                StringEdit.Unchanged
            }
            patch =
                patch.copy(
                    due = edit("due", due, baseline.due),
                    wait = edit("wait", wait, baseline.wait),
                    scheduled = edit("scheduled", scheduled, baseline.scheduled),
                )
            val rulePatch = rule.toPatch(metadata)

            saving = true
            scope.launch {
                try {
                    accept(onSave(patch, rulePatch, replacement))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = "Could not save series: ${e.message}"
                } finally {
                    saving = false
                }
            }
        }
    }
    if (visible) {
        SeriesEditorSurface(embedded, {
            if (!saving) {
                if (dirty) confirmClose = true else onDismiss()
            }
        }) {
            BackHandler(enabled = embedded) { back() }
            Column(
                Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(FattoSpacing.large),
                verticalArrangement = Arrangement.spacedBy(FattoSpacing.medium),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { back() }, enabled = !saving) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription =
                                if (changingSchedule) {
                                    "Back to series"
                                } else if (embedded) {
                                    "Back to task"
                                } else {
                                    "Close series editor"
                                },
                        )
                    }
                    Text(if (changingSchedule) "Change schedule" else "Edit series", style = MaterialTheme.typography.headlineSmall)
                }
                if (changingSchedule) {
                    if (replacement) {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
                            Text(
                                "This starts a replacement series. Tasks already generated keep their current dates.",
                                Modifier.fillMaxWidth().padding(FattoSpacing.medium), style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    SeriesRuleFields(rule, { rule = it }, enabled = !saving && active)
                    SeriesDateField(SeriesDate.Due.label, due, !saving && active) { picker = SeriesDate.Due }
                    SeriesDateField(SeriesDate.Scheduled.label, scheduled, !saving && active) { picker = SeriesDate.Scheduled }
                    SeriesDateField(SeriesDate.Wait.label, wait, !saving && active) { picker = SeriesDate.Wait }
                    SeriesDateField(SeriesDate.Until.label, rule.until, !saving && active) { picker = SeriesDate.Until }
                    Text(
                        "If you set Until, Taskwarrior deletes unfinished occurrences after that date.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    TextField(
                        description, { description = it }, enabled = !saving && active, label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth().testTag("SeriesDescriptionInput"), colors = FattoFieldDefaults.filledColors(),
                    )
                    TextField(
                        project, { project = it }, enabled = !saving && active, label = { Text("Project") },
                        modifier = Modifier.fillMaxWidth(), colors = FattoFieldDefaults.filledColors(),
                        trailingIcon = {
                            IconButton(onClick = { showProjectPicker = true }, enabled = !saving && active) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Select project")
                            }
                        },
                    )
                    SeriesChoiceField(
                        "Priority", priority ?: "None", linkedMapOf("None" to "None", "H" to "High", "M" to "Medium", "L" to "Low"),
                        !saving && active, { priority = it.takeUnless { value -> value == "None" } },
                    )
                    SeriesTagFields(
                        tags, newTag, showInternalTags, enabled = !saving && active,
                        onTagsChange = { tags = it }, onNewTagChange = { newTag = it }, onPick = { showTagPicker = true },
                    )
                    Text(
                        "Content changes apply to the series. Tasks already generated keep their existing content.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("Schedule", style = MaterialTheme.typography.titleMedium)
                    SeriesScheduleSummary(rule, due, scheduled, wait)
                    if (active) {
                        TextButton(onClick = { changingSchedule = true }, enabled = !saving) { Text("Change schedule") }
                    }
                }
                if (!active) Text("This series has stopped. Existing tasks remain available.", style = MaterialTheme.typography.bodyMedium)
                ImportedSeriesDates(baseline.nativeDates + listOfNotNull(baseline.recurrence?.untilRaw?.let { "until" to it }).toMap())
                val message = error ?: validation
                if (message != null) {
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    if (error !=
                        null
                    ) {
                        TextButton(onClick = {
                            resetDraft(latest)
                        }, enabled = !saving) { Text("Reload latest series") }
                    }
                }
                if (saving) Text("Saving…", style = MaterialTheme.typography.bodyMedium)
                Column(Modifier.fillMaxWidth()) {
                    @Composable
                    fun SaveButton() {
                        Button(onClick = saveSeries, enabled = !saving && active && description.isNotBlank() && validation == null) {
                            Text(
                                if (replacement) {
                                    "Replace series"
                                } else if (changingSchedule) {
                                    "Save schedule"
                                } else {
                                    "Save series"
                                },
                            )
                        }
                    }
                    if (effectiveFontScale() >= FattoMetrics.largeFontScale) {
                        Column(verticalArrangement = Arrangement.spacedBy(FattoSpacing.small)) {
                            SaveButton()
                            TextButton(onClick = { cancel() }, enabled = !saving) { Text("Cancel") }
                        }
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small)) {
                            SaveButton()
                            TextButton(onClick = { cancel() }, enabled = !saving) { Text("Cancel") }
                        }
                    }
                }
                if (!changingSchedule && active) {
                    HorizontalDivider()
                    TextButton(
                        onClick = { confirmStop = true }, enabled = !saving,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Stop series") }
                }
            }
        }
    }
    if (visible && showProjectPicker && !saving) {
        ProjectPickerDialog(
            projects = (allTasks.mapNotNull { it.project } + project).distinct().sorted(), selectedProject = project.ifBlank { null },
            onConfirm = {
                project = it
                showProjectPicker = false
            }, onDismiss = { showProjectPicker = false },
        )
    }
    if (visible && showTagPicker && !saving && active) {
        val managedTags = tags.filter(::isManagedSeriesTag)
        TagPickerDialog(
            tags =
                (allTasks.flatMap { it.userTags } + tags).distinct().sorted().filter {
                    !isManagedSeriesTag(it)
                },
            selectedTags = tags.filterNot(::isManagedSeriesTag).toSet(),
            onDismiss = { showTagPicker = false },
            onConfirm = {
                tags = (managedTags + it.filterNot(::isManagedSeriesTag)).distinct()
                showTagPicker = false
            },
        )
    }
    if (visible && confirmClose && !saving) {
        AlertDialog(
            onDismissRequest = { confirmClose = false }, title = { Text("Discard series changes?") },
            text = { Text("Your changes to this series have not been saved.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClose = false
                    discardSeries()
                }) { Text("Discard changes") }
            },
            dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Keep editing") } },
        )
    }
    if (visible && confirmStop && !saving) {
        AlertDialog(
            onDismissRequest = { confirmStop = false }, title = { Text("Stop this series?") },
            text = { Text("No new occurrences will be generated. Existing occurrences remain available.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmStop = false
                    saving = true
                    scope.launch {
                        try {
                            accept(onStop())
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = "Could not stop series: ${e.message}"
                        } finally {
                            saving = false
                        }
                    }
                }, modifier = Modifier.testTag("ConfirmStopSeries")) { Text("Stop series") }
            }, dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Cancel") } },
        )
    }
    if (visible && picker != null && !saving && active) {
        val value =
            when (picker) {
                SeriesDate.Due -> due
                SeriesDate.Wait -> wait
                SeriesDate.Scheduled -> scheduled
                else -> rule.until
            }
        val state = rememberDatePickerState(initialSelectedDateMillis = DateTimeUtils.parseToInstant(value)?.toEpochMilli())

        fun select(value: String?) {
            when (picker) {
                SeriesDate.Due -> {
                    due = value
                    dirtyDates = dirtyDates + "due"
                }
                SeriesDate.Wait -> {
                    wait = value
                    dirtyDates = dirtyDates + "wait"
                }
                SeriesDate.Scheduled -> {
                    scheduled = value
                    dirtyDates = dirtyDates + "scheduled"
                }
                else -> rule = rule.copy(until = value)
            }
            picker = null
        }
        DatePickerDialog(
            onDismissRequest = { picker = null },
            confirmButton = {
                TextButton(onClick = { select(state.selectedDateMillis?.let { Instant.ofEpochMilli(it).toString() }) }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { select(null) }) { Text("Clear") } },
        ) { DatePicker(state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeriesEditorSurface(
    embedded: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (embedded) {
        content()
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState =
                rememberModalBottomSheetState(
                    skipPartiallyExpanded = true,
                    confirmValueChange = {
                        if (it == androidx.compose.material3.SheetValue.Hidden) {
                            onDismiss()
                            false
                        } else {
                            true
                        }
                    },
                ),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.extraLarge,
        ) { content() }
    }
}

@Composable
private fun SeriesScheduleSummary(
    rule: RecurrenceRule,
    due: String?,
    scheduled: String?,
    wait: String?,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(FattoSpacing.medium), verticalArrangement = Arrangement.spacedBy(FattoSpacing.small)) {
            SeriesSummaryRow("Repeat", recurrenceLabel(rule.interval))
            SeriesSummaryRow("First due date", readableDate(due))
            scheduled?.let { SeriesSummaryRow("Scheduled", readableDate(it)) }
            wait?.let { SeriesSummaryRow("Wait", readableDate(it)) }
            SeriesSummaryRow("Until", rule.until?.let { readableDate(it) } ?: "None")
        }
    }
}

@Composable
private fun SeriesSummaryRow(
    label: String,
    value: String,
) {
    if (effectiveFontScale() >= FattoMetrics.largeFontScale) {
        Column(verticalArrangement = Arrangement.spacedBy(FattoSpacing.xSmall)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FattoSpacing.medium)) {
            Text(
                label, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
            )
            Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun SeriesDateField(
    label: String,
    value: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.medium) {
        TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FattoSpacing.xSmall)) {
                Text(
                    label, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    value?.let {
                        readableDate(it)
                    } ?: "None",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Icon(Icons.Default.CalendarMonth, contentDescription = null)
        }
    }
}

@Composable
private fun SeriesChoiceField(
    label: String,
    value: String,
    options: Map<String, String>,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.medium) {
            TextButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FattoSpacing.xSmall)) {
                    Text(
                        label, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        options[value] ?: value, style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth(),
                    )
                }
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { (key, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    onChange(key)
                    expanded = false
                })
            }
        }
    }
}

@Composable
private fun SeriesRuleFields(
    rule: RecurrenceRule,
    onChange: (RecurrenceRule) -> Unit,
    enabled: Boolean,
) {
    val options =
        linkedMapOf(
            "daily" to "Daily", "weekdays" to "Weekdays", "weekly" to "Weekly", "monthly" to "Monthly",
            "yearly" to "Yearly", "custom" to "Custom",
        )
    var custom by remember(rule.interval) { mutableStateOf(rule.interval !in options) }
    SeriesChoiceField("Repeat", if (custom) "custom" else rule.interval, options, enabled) {
        custom = it == "custom"
        onChange(rule.copy(interval = if (custom) "" else it))
    }
    if (custom) {
        val match = Regex("([0-9]+)(days|weeks|months|years)").matchEntire(rule.interval)
        val number = match?.groupValues?.get(1).orEmpty()
        val unit = match?.groupValues?.get(2) ?: "days"
        if (match == null && rule.interval !in options && rule.interval.isNotBlank()) {
            Text("Imported repeat rule: ${rule.interval}", style = MaterialTheme.typography.bodySmall)
        }
        TextField(
            number, { onChange(rule.copy(interval = it + unit)) }, enabled = enabled, label = { Text("Repeat every") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("RecurrenceIntervalInput"), colors = FattoFieldDefaults.filledColors(),
        )
        SeriesChoiceField(
            "Unit", unit, linkedMapOf("days" to "Days", "weeks" to "Weeks", "months" to "Months", "years" to "Years"),
            enabled, { onChange(rule.copy(interval = number + it)) },
        )
    }
}

@Composable
private fun ImportedSeriesDates(dates: Map<String, String>) {
    var expanded by remember { mutableStateOf(false) }
    val imported =
        dates.filter { (key, value) ->
            key in listOf("due", "wait", "scheduled", "until") &&
                DateTimeUtils.parseToInstant(value) == null &&
                value.toLongOrNull() == null
        }
    if (imported.isEmpty()) return
    TextButton(onClick = { expanded = !expanded }) { Text("Imported date details") }
    if (expanded) {
        imported.forEach { (key, value) -> Text("$key: $value", style = MaterialTheme.typography.bodySmall) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeriesTagFields(
    tags: List<String>,
    newTag: String,
    showInternalTags: Boolean,
    enabled: Boolean,
    onTagsChange: (List<String>) -> Unit,
    onNewTagChange: (String) -> Unit,
    onPick: () -> Unit,
) {
    Text("Tags", style = MaterialTheme.typography.labelLarge)
    val visibleTags = tags.filter { showInternalTags || !isManagedSeriesTag(it) }
    if (visibleTags.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small),
            verticalArrangement = Arrangement.spacedBy(FattoSpacing.small),
        ) {
            visibleTags.forEach { tag ->
                TagChip(
                    tag,
                    onRemove =
                        if (enabled && !isManagedSeriesTag(tag)) {
                            { onTagsChange(tags - tag) }
                        } else {
                            null
                        },
                )
            }
        }
    }
    TextField(
        newTag, onNewTagChange, enabled = enabled, label = { Text("Add Tag") }, singleLine = true,
        isError = isManagedSeriesTag(newTag.trim()),
        supportingText = {
            if (isManagedSeriesTag(newTag.trim())) {
                Text("This tag is managed by Taskwarrior.", style = MaterialTheme.typography.bodySmall)
            }
        },
        modifier = Modifier.fillMaxWidth().testTag("SeriesTagInput"), colors = FattoFieldDefaults.filledColors(),
        trailingIcon = {
            TaskTagActions(
                enabled = enabled,
                onAdd = {
                    val value = newTag.trim()
                    if (value.isNotBlank() && !isManagedSeriesTag(value)) {
                        if (value !in tags) onTagsChange(tags + value)
                        onNewTagChange("")
                    }
                },
                onSelect = onPick,
            )
        },
    )
}

private fun isManagedSeriesTag(tag: String): Boolean = tag in INTERNAL_TAGS || isSynthetic(tag)
