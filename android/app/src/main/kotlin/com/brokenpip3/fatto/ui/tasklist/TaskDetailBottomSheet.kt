package com.brokenpip3.fatto.ui.tasklist

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.brokenpip3.fatto.data.model.Annotation
import com.brokenpip3.fatto.data.model.INTERNAL_TAGS
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.TaskEditResult
import com.brokenpip3.fatto.data.model.toEditPatch
import com.brokenpip3.fatto.ui.common.ProjectPickerDialog
import com.brokenpip3.fatto.ui.common.TagPickerDialog
import com.brokenpip3.fatto.ui.common.TaskPickerDialog
import com.brokenpip3.fatto.ui.theme.FattoFieldDefaults
import com.brokenpip3.fatto.ui.theme.FattoMetrics
import com.brokenpip3.fatto.ui.theme.FattoSpacing
import com.brokenpip3.fatto.ui.theme.effectiveFontScale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import uniffi.taskchampion_android.RecurrenceDiagnostic
import uniffi.taskchampion_android.TaskEditPatch
import uniffi.taskchampion_android.TaskStatus
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
@Composable
fun TaskDetailBottomSheet(
    task: Task,
    onDismiss: () -> Unit,
    onSave: suspend (TaskEditPatch) -> TaskEditResult,
    recurrenceDiagnostics: List<RecurrenceDiagnostic> = emptyList(),
    seriesActions: SeriesEditorActions? = null,
    availableProjects: List<String>,
    availableTags: List<String>,
    showInternalTags: Boolean = true,
    autoWaiting: Boolean = false,
    firstDayOfWeek: Int = Calendar.MONDAY,
    onAddAnnotation: (suspend (String, String) -> Annotation)? = null,
    onRemoveAnnotation: (suspend (String, String) -> Unit)? = null,
    allTasks: List<Task> = emptyList(),
    showCompleted: Boolean = true,
    onAddDependencies: suspend (String, List<String>) -> Unit = { _, _ -> },
    onRemoveDependency: suspend (String, String) -> Unit = { _, _ -> },
) {
    var baseline by remember(task.uuid) { mutableStateOf(task) }
    var saveError by remember(task.uuid) { mutableStateOf<String?>(null) }
    var saving by remember(task.uuid) { mutableStateOf(false) }
    var showSeries by remember(task.uuid) { mutableStateOf(false) }
    var seriesSaving by remember(task.uuid) { mutableStateOf(false) }
    var seriesDirty by remember(task.uuid) { mutableStateOf(false) }
    var confirmDiscard by remember(task.uuid) { mutableStateOf(false) }
    var refreshAfterSave by remember(task.uuid) { mutableStateOf(false) }
    val parentSeries = allTasks.find { it.uuid.equals(task.recurrence?.parent, ignoreCase = true) }
    val largeFontScale = effectiveFontScale() >= 1.5f
    var description by remember(baseline) { mutableStateOf(baseline.description) }
    var project by remember(baseline) { mutableStateOf(baseline.project ?: "") }
    var tags by remember(baseline) { mutableStateOf(baseline.userTags) }
    var due by remember(baseline) { mutableStateOf(baseline.due) }
    var wait by remember(baseline) { mutableStateOf(baseline.wait) }
    var scheduled by remember(baseline) { mutableStateOf(baseline.scheduled) }
    var start by remember(baseline) { mutableStateOf(baseline.start) }
    var priority by remember(baseline) { mutableStateOf(baseline.priority) }
    var newTag by remember(baseline) { mutableStateOf("") }
    var annotations by remember(baseline) { mutableStateOf(baseline.annotations) }
    var dependencies by remember(baseline) { mutableStateOf(baseline.dependencies) }
    var newAnnotation by remember(baseline) { mutableStateOf("") }
    var showAnnotations by remember(baseline) { mutableStateOf(baseline.annotations.isNotEmpty()) }
    var showDependencies by remember(baseline) { mutableStateOf(baseline.dependencies.isNotEmpty()) }
    var showDetails by remember(baseline) { mutableStateOf(false) }
    var showBlocking by remember(baseline) { mutableStateOf(false) }
    var showBlockedByPicker by remember { mutableStateOf(false) }
    var showBlockingPicker by remember { mutableStateOf(false) }
    var showProjectPicker by remember { mutableStateOf(false) }
    var showTagPicker by remember { mutableStateOf(false) }

    val blockingTasks =
        allTasks.filter {
            it.dependencies.contains(task.uuid) && it.status != TaskStatus.Deleted
        }
    // Live flags — the `task` param is a snapshot; recompute from local state + allTasks
    val isBlockedLive =
        dependencies.any { depUuid ->
            allTasks.find { it.uuid == depUuid }?.status == TaskStatus.Pending
        }
    val isBlockingLive = blockingTasks.any { it.status == TaskStatus.Pending }

    val scope = rememberCoroutineScope()

    val filteredProjects =
        remember(project, availableProjects) {
            if (project.isBlank()) {
                emptyList()
            } else {
                availableProjects.filter { it.contains(project, ignoreCase = true) && it != project }
            }
        }
    val filteredTags =
        remember(newTag, availableTags, tags) {
            if (newTag.isBlank()) {
                emptyList()
            } else {
                availableTags.filter {
                    it.contains(newTag, ignoreCase = true) && !tags.contains(it)
                }
            }
        }

    var activePicker by remember { mutableStateOf<DatePickerType?>(null) }
    val datePickerState = rememberDatePickerState()
    val visibleTags =
        if (showInternalTags) tags else tags.filterNot { INTERNAL_TAGS.contains(it.uppercase()) }
    val hiddenInternalTags =
        if (showInternalTags) emptyList() else tags.filter { INTERNAL_TAGS.contains(it.uppercase()) }

    val saveThen: (() -> Unit) -> Unit = { afterSave ->
        if (!saving) {
            saving = true
            scope.launch {
                try {
                    val draft =
                        baseline.copy(
                            description = description,
                            project =
                                if (project ==
                                    baseline.project.orEmpty()
                                ) {
                                    baseline.project
                                } else {
                                    project.trim().takeIf { it.isNotBlank() }
                                },
                            tags = tags,
                            due = due, wait = wait, scheduled = scheduled, start = start,
                            priority = priority,
                        )
                    // Dependency/annotation controls save independently; do not send
                    // their possibly old local snapshot with an unrelated content edit.
                    when (val result = onSave(draft.toEditPatch(baseline, autoWaiting))) {
                        TaskEditResult.Success -> {
                            refreshAfterSave = true
                            afterSave()
                        }
                        is TaskEditResult.Failure -> saveError = result.message
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    saveError = "Could not save task: ${e.message}"
                } finally {
                    saving = false
                }
            }
        }
    }

    val saveAndDismiss: () -> Unit = { saveThen(onDismiss) }

    val latestTask = allTasks.find { it.uuid == task.uuid } ?: task
    val scrollState = rememberScrollState()
    LaunchedEffect(refreshAfterSave, latestTask) {
        if (refreshAfterSave) {
            baseline = latestTask
            refreshAfterSave = false
        }
    }
    val taskDirty =
        description != baseline.description ||
            project != baseline.project.orEmpty() ||
            tags != baseline.userTags ||
            due != baseline.due ||
            wait != baseline.wait ||
            scheduled != baseline.scheduled ||
            start != baseline.start ||
            priority != baseline.priority
    val requestClose: () -> Unit = {
        if (!saving && !seriesSaving) {
            if (task.recurrence != null && (taskDirty || seriesDirty)) confirmDiscard = true else saveAndDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = requestClose,
        sheetState =
            androidx.compose.material3.rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
                confirmValueChange = {
                    if (it == androidx.compose.material3.SheetValue.Hidden) {
                        requestClose()
                        false
                    } else {
                        true
                    }
                },
            ),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)) },
    ) {
        if (parentSeries != null && seriesActions != null) {
            RecurrenceTemplateSheet(
                parentSeries, allTasks, onDismiss = { showSeries = false },
                onSave = seriesActions.save, onStop = { seriesActions.stop(parentSeries.uuid) },
                showInternalTags = showInternalTags, embedded = true, visible = showSeries,
                onStateChange = { busy, dirty ->
                    seriesSaving = busy
                    seriesDirty = dirty
                },
            )
        }
        if (!showSeries || parentSeries == null) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(FattoSpacing.large)
                        .padding(bottom = FattoSpacing.large)
                        .testTag("TaskDetailBottomSheet"),
                verticalArrangement = Arrangement.spacedBy(FattoSpacing.large),
            ) {
                if (saveError != null) {
                    Text(saveError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = {
                        baseline = latestTask
                        saveError = null
                    }, enabled = !saving) { Text("Reload latest task") }
                }
                if (saving) Text("Saving…", style = MaterialTheme.typography.bodyMedium)
                if (isBlockedLive || isBlockingLive) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small),
                        verticalArrangement = Arrangement.spacedBy(FattoSpacing.small),
                    ) {
                        if (isBlockedLive) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text(
                                    text = "Task is blocked",
                                    modifier = Modifier.padding(horizontal = FattoSpacing.small, vertical = FattoSpacing.xSmall),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                        if (isBlockingLive) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text(
                                    text = "Blocking other tasks",
                                    modifier = Modifier.padding(horizontal = FattoSpacing.small, vertical = FattoSpacing.xSmall),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }
                    }
                }

                @Composable
                fun HeaderTitle(modifier: Modifier = Modifier) {
                    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = requestClose,
                            modifier = Modifier.testTag("CloseButton").semantics { contentDescription = "Close task editor" },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(text = "Edit Task", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    }
                }

                @Composable
                fun StartButton() {
                    Button(
                        onClick = {
                            start = if (start == null) Instant.now().truncatedTo(ChronoUnit.SECONDS).toString() else null
                        },
                        enabled = !saving,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor =
                                    if (start == null) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                contentColor =
                                    if (start == null) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onError
                                    },
                            ),
                        modifier =
                            Modifier.semantics {
                                contentDescription = if (start == null) "Start" else "Stop"
                            },
                    ) {
                        Icon(
                            imageVector = if (start == null) Icons.Default.PlayArrow else Icons.Default.Stop,
                            contentDescription = null,
                        )
                        if (!largeFontScale) {
                            Text(if (start == null) "Start" else "Stop")
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeaderTitle(Modifier.weight(1f))
                    if (task.status == TaskStatus.Pending && task.isActionableStatus) StartButton()
                }

                TextField(
                    value = description,
                    onValueChange = { if (!saving) description = it },
                    enabled = !saving,
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth().testTag("TaskDescriptionInput"),
                    maxLines = 5,
                    colors = FattoFieldDefaults.filledColors(),
                )

                TextField(
                    value = project,
                    onValueChange = { if (!saving) project = it },
                    enabled = !saving,
                    label = { Text("Project") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = FattoFieldDefaults.filledColors(),
                    trailingIcon = {
                        IconButton(
                            onClick = { showProjectPicker = true },
                            modifier = Modifier.testTag("SelectProjectButton"),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Select project")
                        }
                    },
                )

                if (!saving && filteredProjects.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(filteredProjects) { suggestion ->
                            SuggestionChip(label = suggestion, onClick = { if (!saving) project = suggestion })
                        }
                    }
                }

                if (task.isRecurrenceTemplate) {
                    PriorityIconButton(priority = priority, onPriorityChange = { if (!saving) priority = it }, enabled = !saving)
                    Text(
                        "Due: ${due ?: "None"} · Scheduled: ${scheduled ?: "None"}" +
                            " · Wait: ${wait ?: "None"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    TaskDateAndPriorityControls(
                        enabled = !saving,
                        priority = priority,
                        due = due,
                        scheduled = scheduled,
                        wait = wait,
                        onPriorityChange = { if (!saving) priority = it },
                        onDateClick = { if (!saving && !task.isRecurrenceTemplate) activePicker = it },
                    )
                }
                if (task.recurrence != null) {
                    OccurrenceSeriesRow(
                        latestTask, allTasks, recurrenceDiagnostics, enabled = !saving,
                        onEditSeries =
                            if (seriesActions != null) {
                                { showSeries = true }
                            } else {
                                null
                            },
                    )
                }

                Text(text = "Tags", style = MaterialTheme.typography.labelLarge)

                if (visibleTags.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(visibleTags) { tag ->
                            TagChip(
                                tag = tag,
                                onRemove =
                                    if (saving) {
                                        null
                                    } else {
                                        { tags = tags - tag }
                                    },
                            )
                        }
                    }
                }

                val addTag = {
                    if (!saving && newTag.isNotBlank() && !tags.contains(newTag.trim())) {
                        tags = tags + newTag.trim()
                        newTag = ""
                    }
                }

                TextField(
                    value = newTag,
                    onValueChange = { if (!saving) newTag = it },
                    enabled = !saving,
                    label = { Text("Add Tag") },
                    modifier = Modifier.fillMaxWidth().testTag("TagInput"),
                    singleLine = true,
                    colors = FattoFieldDefaults.filledColors(),
                    trailingIcon = {
                        TaskTagActions(
                            enabled = !saving,
                            addActionModifier = Modifier.testTag("AddTagButton"),
                            onAdd = addTag,
                            onSelect = { if (!saving) showTagPicker = true },
                        )
                    },
                )

                if (!saving && filteredTags.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filteredTags) { suggestion ->
                            SuggestionChip(
                                label = suggestion,
                                onClick = {
                                    if (!saving && !tags.contains(suggestion)) {
                                        tags = tags + suggestion
                                    }
                                    newTag = ""
                                },
                            )
                        }
                    }
                }

                AccordionSection(
                    title = "Annotations",
                    icon = Icons.Default.Add,
                    count = annotations.size,
                    expanded = showAnnotations,
                    onToggle = { showAnnotations = !showAnnotations },
                ) {
                    annotations.forEach { annotation ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = annotation.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = annotation.entry,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    runDetailAction(onError = { saveError = "Could not remove annotation: ${it.message}" }) {
                                        onRemoveAnnotation?.invoke(task.uuid, annotation.entry)
                                        annotations = annotations - annotation
                                    }
                                }
                            }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove annotation",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextField(
                            value = newAnnotation,
                            onValueChange = { newAnnotation = it },
                            placeholder = { Text("Add a note...") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = FattoFieldDefaults.filledColors(),
                        )
                        TextButton(
                            onClick = {
                                if (newAnnotation.isNotBlank() && onAddAnnotation != null) {
                                    scope.launch {
                                        runDetailAction(onError = { saveError = "Could not save change: ${it.message}" }) {
                                            val ann = onAddAnnotation.invoke(task.uuid, newAnnotation.trim())
                                            annotations = annotations + ann
                                            newAnnotation = ""
                                        }
                                    }
                                }
                            },
                            enabled = !saving && newAnnotation.isNotBlank() && onAddAnnotation != null,
                        ) {
                            Text("Add", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                AccordionSection(
                    title = "Blocked by",
                    icon = Icons.Default.Link,
                    count = dependencies.size,
                    expanded = showDependencies,
                    onToggle = { showDependencies = !showDependencies },
                ) {
                    dependencies.forEach { depUuid ->
                        val depTask = allTasks.find { it.uuid == depUuid }
                        DependencyRow(
                            task = depTask,
                            fallbackUuid = depUuid,
                            onRemove = {
                                scope.launch {
                                    runDetailAction(onError = { saveError = "Could not save change: ${it.message}" }) {
                                        onRemoveDependency(task.uuid, depUuid)
                                        dependencies = dependencies - depUuid
                                    }
                                }
                            },
                        )
                    }
                    AddDependencyRow(
                        label = "Add task",
                        contentDescription = "Add blocking dependency",
                        testTag = "AddBlockedByButton",
                        onClick = { showBlockedByPicker = true },
                    )
                }

                AccordionSection(
                    title = "Blocking",
                    icon = Icons.Default.Link,
                    count = blockingTasks.size,
                    expanded = showBlocking,
                    onToggle = { showBlocking = !showBlocking },
                ) {
                    blockingTasks.forEach { dependent ->
                        DependencyRow(
                            task = dependent,
                            fallbackUuid = dependent.uuid,
                            onRemove = {
                                scope.launch {
                                    runDetailAction(onError = { saveError = "Could not save change: ${it.message}" }) {
                                        onRemoveDependency(dependent.uuid, task.uuid)
                                    }
                                }
                            },
                        )
                    }
                    AddDependencyRow(
                        label = "Add task",
                        contentDescription = "Add dependent task",
                        testTag = "AddBlockingButton",
                        onClick = { showBlockingPicker = true },
                    )
                }

                AccordionSection(
                    title = "Details",
                    icon = Icons.Default.Info,
                    count = null,
                    expanded = showDetails,
                    onToggle = { showDetails = !showDetails },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Urgency", style = MaterialTheme.typography.bodyMedium)
                        Text("%.2f".format(task.urgency), style = MaterialTheme.typography.bodyMedium)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(FattoSpacing.xSmall)) {
                        Text("UUID", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = task.uuid,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (task.udas.isNotEmpty()) {
                        Text("Extra Attributes (UDAs)", style = MaterialTheme.typography.labelLarge)
                        for ((key, value) in task.udas) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(FattoSpacing.xSmall),
                            ) {
                                Text(
                                    text = key,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = value,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                if (task.recurrence != null && taskDirty) {
                    Button(onClick = { saveThen {} }, enabled = !saving && description.isNotBlank()) { Text("Save task") }
                }
            }
        }
    }

    if (confirmDiscard && !saving && !seriesSaving) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false }, title = { Text("Discard unsaved changes?") },
            text = { Text("Your task or series changes have not been saved.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onDismiss()
                }) { Text("Discard changes") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }

    if (showProjectPicker && !saving) {
        ProjectPickerDialog(
            projects = (availableProjects + project).toSet().toList(),
            selectedProject = project.ifBlank { null },
            onDismiss = { showProjectPicker = false },
            onConfirm = { selectedProject ->
                if (!saving) project = selectedProject
                showProjectPicker = false
            },
        )
    }

    if (showTagPicker && !saving) {
        TagPickerDialog(
            tags = (availableTags + visibleTags).toSet().toList(),
            selectedTags = visibleTags.toSet(),
            onDismiss = { showTagPicker = false },
            onConfirm = { selectedVisibleTags ->
                if (!saving) tags = (hiddenInternalTags + selectedVisibleTags).distinct()
                showTagPicker = false
            },
        )
    }

    if (activePicker != null && !saving) {
        DatePickerDialog(
            onDismissRequest = { activePicker = null },
            confirmButton = {
                TextButton(onClick = {
                    val date =
                        datePickerState.selectedDateMillis?.let { millis ->
                            Instant.ofEpochMilli(millis).toString()
                        }
                    when (activePicker) {
                        DatePickerType.DUE -> due = date
                        DatePickerType.SCHEDULED -> scheduled = date
                        DatePickerType.WAIT -> wait = date
                        null -> {}
                    }
                    activePicker = null
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    when (activePicker) {
                        DatePickerType.DUE -> due = null
                        DatePickerType.SCHEDULED -> scheduled = null
                        DatePickerType.WAIT -> wait = null
                        null -> {}
                    }
                    activePicker = null
                }) {
                    Text("Clear")
                }
            },
        ) {
            val currentConfig = LocalConfiguration.current
            val config = Configuration(currentConfig)
            val targetLocale = if (firstDayOfWeek == Calendar.SUNDAY) Locale.US else Locale.UK
            config.setLocale(targetLocale)

            CompositionLocalProvider(LocalConfiguration provides config) {
                DatePicker(state = datePickerState)
            }
        }
    }

    if (showBlockedByPicker) {
        val pool =
            allTasks.filter {
                it.uuid != task.uuid &&
                    it.status != TaskStatus.Deleted &&
                    (showCompleted || it.status != TaskStatus.Completed) &&
                    it.uuid !in dependencies
            }
        TaskPickerDialog(
            title = "Add blocked by",
            tasks = pool,
            onDismiss = { showBlockedByPicker = false },
            onConfirm = { picked ->
                showBlockedByPicker = false
                scope.launch {
                    runDetailAction(onError = { saveError = "Could not save change: ${it.message}" }) {
                        onAddDependencies(task.uuid, picked.map { it.uuid })
                        dependencies = dependencies + picked.map { it.uuid }
                    }
                }
            },
        )
    }

    if (showBlockingPicker) {
        val pool =
            allTasks.filter {
                it.uuid != task.uuid &&
                    it.status != TaskStatus.Deleted &&
                    (showCompleted || it.status != TaskStatus.Completed) &&
                    !it.dependencies.contains(task.uuid)
            }
        TaskPickerDialog(
            title = "Add blocking task",
            tasks = pool,
            onDismiss = { showBlockingPicker = false },
            onConfirm = { picked ->
                showBlockingPicker = false
                scope.launch {
                    runDetailAction(onError = { saveError = "Could not save change: ${it.message}" }) {
                        picked.forEach { onAddDependencies(it.uuid, listOf(task.uuid)) }
                    }
                }
            },
        )
    }
}

@Composable
private fun DependencyRow(
    task: Task?,
    fallbackUuid: String,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task?.description ?: fallbackUuid,
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (task?.status == TaskStatus.Completed) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
            if (task != null) {
                val subtitle =
                    listOfNotNull(
                        task.project,
                        task.userTags.joinToString(" ").takeIf { it.isNotEmpty() },
                    ).joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (task?.status == TaskStatus.Completed) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Completed",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove dependency",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AddDependencyRow(
    label: String,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = FattoMetrics.minTouchTarget)
                .clickable(onClick = onClick)
                .semantics {
                    this.contentDescription = contentDescription
                    this.testTag = testTag
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(FattoMetrics.smallIcon),
        )
        Spacer(Modifier.width(FattoSpacing.small))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private suspend fun runDetailAction(
    onError: (Exception) -> Unit,
    action: suspend () -> Unit,
) {
    try {
        action()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        onError(e)
    }
}
