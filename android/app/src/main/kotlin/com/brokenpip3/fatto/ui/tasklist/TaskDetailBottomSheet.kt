package com.brokenpip3.fatto.ui.tasklist

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.brokenpip3.fatto.data.model.INTERNAL_TAGS
import com.brokenpip3.fatto.data.model.Task
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailBottomSheet(
    task: Task,
    onDismiss: () -> Unit,
    onSave: (Task) -> Unit,
    availableProjects: List<String>,
    showInternalTags: Boolean = true,
    firstDayOfWeek: Int = Calendar.MONDAY,
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
    var showAdvanced by remember(task) { mutableStateOf(false) }

    val filteredProjects =
        remember(project, availableProjects) {
            if (project.isBlank()) {
                emptyList()
            } else {
                availableProjects.filter { it.contains(project, ignoreCase = true) && it != project }
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
            ),
        )
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = saveAndDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)) },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .padding(bottom = 32.dp)
                    .testTag("TaskDetailBottomSheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (task.isBlocked || task.isBlocking) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (task.isBlocked) {
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
                    if (task.isBlocking) {
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

            Column(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = { showAdvanced = !showAdvanced },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Icon(
                        imageVector = if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                    )
                    Text(if (showAdvanced) "Hide Advanced Details" else "Show Advanced Details")
                }

                if (showAdvanced) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Urgency: ${"%.2f".format(task.urgency)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (task.dependencies.isNotEmpty()) {
                            Text(text = "Dependencies", style = MaterialTheme.typography.labelLarge)
                            for (dep in task.dependencies) {
                                Text(
                                    text = dep,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }
                        }

                        if (task.udas.isNotEmpty()) {
                            Text(text = "Extra Attributes (UDAs)", style = MaterialTheme.typography.labelLarge)
                            for ((key, value) in task.udas) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
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
        }
    }

    if (activePicker != null) {
        DatePickerDialog(
            onDismissRequest = { activePicker = null },
            confirmButton = {
                TextButton(onClick = {
                    val date =
                        datePickerState.selectedDateMillis?.let {
                            Instant.ofEpochMilli(it)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                                .atStartOfDay(ZoneId.systemDefault())
                                .toInstant()
                                .toString()
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
}
