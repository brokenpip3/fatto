package com.brokenpip3.fatto.ui.tasklist

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.brokenpip3.fatto.data.model.Annotation
import com.brokenpip3.fatto.data.model.INTERNAL_TAGS
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.ui.common.TaskPickerDialog
import kotlinx.coroutines.launch
import uniffi.taskchampion_android.TaskStatus
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
@Composable
fun TaskDetailBottomSheet(
    task: Task,
    onDismiss: () -> Unit,
    onSave: (Task) -> Unit,
    availableProjects: List<String>,
    availableTags: List<String>,
    showInternalTags: Boolean = true,
    firstDayOfWeek: Int = Calendar.MONDAY,
    onAddAnnotation: (suspend (String, String) -> Annotation)? = null,
    onRemoveAnnotation: ((String, String) -> Unit)? = null,
    allTasks: List<Task> = emptyList(),
    showCompleted: Boolean = true,
    onAddDependencies: suspend (String, List<String>) -> Unit = { _, _ -> },
    onRemoveDependency: suspend (String, String) -> Unit = { _, _ -> },
) {
    var description by remember(task) { mutableStateOf(task.description) }
    var project by remember(task) { mutableStateOf(task.project ?: "") }
    var tags by remember(task) { mutableStateOf(task.userTags) }
    var due by remember(task) { mutableStateOf(task.due) }
    var wait by remember(task) { mutableStateOf(task.wait) }
    var scheduled by remember(task) { mutableStateOf(task.scheduled) }
    var start by remember(task) { mutableStateOf(task.start) }
    var priority by remember(task) { mutableStateOf(task.priority) }
    var newTag by remember(task) { mutableStateOf("") }
    var annotations by remember(task) { mutableStateOf(task.annotations) }
    var dependencies by remember(task) { mutableStateOf(task.dependencies) }
    var newAnnotation by remember(task) { mutableStateOf("") }
    var showAnnotations by remember(task) { mutableStateOf(task.annotations.isNotEmpty()) }
    var showDependencies by remember(task) { mutableStateOf(task.dependencies.isNotEmpty()) }
    var showDetails by remember(task) { mutableStateOf(false) }
    var showBlocking by remember(task) { mutableStateOf(false) }
    var showBlockedByPicker by remember { mutableStateOf(false) }
    var showBlockingPicker by remember { mutableStateOf(false) }

    val blockingTasks =
        allTasks.filter {
            it.dependencies.contains(task.uuid) && it.status != TaskStatus.DELETED
        }
    // Live flags — the `task` param is a snapshot; recompute from local state + allTasks
    val isBlockedLive =
        dependencies.any { depUuid ->
            allTasks.find { it.uuid == depUuid }?.status == TaskStatus.PENDING
        }
    val isBlockingLive = blockingTasks.any { it.status == TaskStatus.PENDING }

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

    val saveAndDismiss = {
        onSave(
            task.copy(
                description = description,
                project = if (project.isNotBlank()) project.trim() else null,
                tags = tags,
                due = due,
                wait = wait,
                scheduled = scheduled,
                start = start,
                priority = priority,
                dependencies = dependencies,
            ),
        )
        onDismiss()
    }

    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = saveAndDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)) },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(16.dp)
                    .padding(bottom = 32.dp)
                    .testTag("TaskDetailBottomSheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (isBlockedLive || isBlockingLive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isBlockedLive) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                text = "Task is blocked",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
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
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = saveAndDismiss,
                        modifier = Modifier.semantics { contentDescription = "CloseButton" },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(text = "Edit Task", style = MaterialTheme.typography.titleMedium)
                }

                Button(
                    onClick = {
                        start = if (start == null) Instant.now().truncatedTo(ChronoUnit.SECONDS).toString() else null
                    },
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
                    Text(if (start == null) "Start" else "Stop")
                }
            }

            TextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "TaskDescriptionInput" },
                maxLines = 5,
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
            )

            TextField(
                value = project,
                onValueChange = { project = it },
                label = { Text("Project") },
                modifier = Modifier.fillMaxWidth(),
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
            )

            if (filteredProjects.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(filteredProjects) { suggestion ->
                        SuggestionChip(label = suggestion, onClick = { project = suggestion })
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                PriorityIconButton(
                    priority = priority,
                    onPriorityChange = { priority = it },
                )
                DatePickerIconButton(
                    label = "Due",
                    date = due,
                    icon = Icons.Default.Event,
                    onClick = { activePicker = DatePickerType.DUE },
                )
                DatePickerIconButton(
                    label = "Sch",
                    date = scheduled,
                    icon = Icons.Default.Schedule,
                    onClick = { activePicker = DatePickerType.SCHEDULED },
                )
                DatePickerIconButton(
                    label = "Wait",
                    date = wait,
                    icon = Icons.Default.CalendarMonth,
                    onClick = { activePicker = DatePickerType.WAIT },
                )
            }

            Text(text = "Tags", style = MaterialTheme.typography.labelLarge)

            val displayTags = if (showInternalTags) tags else tags.filter { !INTERNAL_TAGS.contains(it.uppercase()) }
            if (displayTags.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(displayTags) { tag ->
                        TagChip(tag = tag, onRemove = { tags = tags - tag })
                    }
                }
            }

            TextField(
                value = newTag,
                onValueChange = { newTag = it },
                label = { Text("Add Tag") },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "TagInput" },
                singleLine = true,
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                trailingIcon = {
                    TextButton(
                        onClick = {
                            if (newTag.isNotBlank() && !tags.contains(newTag.trim())) {
                                tags = tags + newTag.trim()
                                newTag = ""
                            }
                        },
                        modifier =
                            Modifier.semantics {
                                contentDescription = "AddTagButton"
                            },
                    ) {
                        Text("Add", style = MaterialTheme.typography.labelLarge)
                    }
                },
            )

            if (filteredTags.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filteredTags) { suggestion ->
                        SuggestionChip(
                            label = suggestion,
                            onClick = {
                                if (!tags.contains(suggestion)) {
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                        IconButton(onClick = {
                            annotations = annotations - annotation
                            onRemoveAnnotation?.invoke(task.uuid, annotation.entry)
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
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            ),
                    )
                    TextButton(
                        onClick = {
                            if (newAnnotation.isNotBlank() && onAddAnnotation != null) {
                                scope.launch {
                                    try {
                                        val ann = onAddAnnotation.invoke(task.uuid, newAnnotation.trim())
                                        annotations = annotations + ann
                                        newAnnotation = ""
                                    } catch (_: Exception) {
                                    }
                                }
                            }
                        },
                        enabled = newAnnotation.isNotBlank() && onAddAnnotation != null,
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
                                try {
                                    onRemoveDependency(task.uuid, depUuid)
                                    dependencies = dependencies - depUuid
                                } catch (_: Exception) {
                                }
                            }
                        },
                    )
                }
                AddDependencyRow(
                    label = "Add task",
                    contentDescription = "AddBlockedByButton",
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
                                try {
                                    onRemoveDependency(dependent.uuid, task.uuid)
                                } catch (_: Exception) {
                                }
                            }
                        },
                    )
                }
                AddDependencyRow(
                    label = "Add task",
                    contentDescription = "AddBlockingButton",
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("UUID", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = task.uuid,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                if (task.udas.isNotEmpty()) {
                    Text("Extra Attributes (UDAs)", style = MaterialTheme.typography.labelLarge)
                    for ((key, value) in task.udas) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }

    if (activePicker != null) {
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
                    it.status != TaskStatus.DELETED &&
                    (showCompleted || it.status != TaskStatus.COMPLETED) &&
                    it.uuid !in dependencies
            }
        TaskPickerDialog(
            title = "Add blocked by",
            tasks = pool,
            onDismiss = { showBlockedByPicker = false },
            onConfirm = { picked ->
                showBlockedByPicker = false
                scope.launch {
                    try {
                        onAddDependencies(task.uuid, picked.map { it.uuid })
                        dependencies = dependencies + picked.map { it.uuid }
                    } catch (_: Exception) {
                    }
                }
            },
        )
    }

    if (showBlockingPicker) {
        val pool =
            allTasks.filter {
                it.uuid != task.uuid &&
                    it.status != TaskStatus.DELETED &&
                    (showCompleted || it.status != TaskStatus.COMPLETED) &&
                    !it.dependencies.contains(task.uuid)
            }
        TaskPickerDialog(
            title = "Add blocking task",
            tasks = pool,
            onDismiss = { showBlockingPicker = false },
            onConfirm = { picked ->
                showBlockingPicker = false
                scope.launch {
                    try {
                        picked.forEach { onAddDependencies(it.uuid, listOf(task.uuid)) }
                    } catch (_: Exception) {
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
                    if (task?.status == TaskStatus.COMPLETED) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
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
        if (task?.status == TaskStatus.COMPLETED) {
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
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp)
                .semantics { this.contentDescription = contentDescription },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
