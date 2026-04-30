package com.brokenpip3.fatto.ui.tasklist

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    availableProjects: List<String>,
    availableTags: List<String>,
    initialProject: String? = null,
    initialTags: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (String, String?, List<String>, String?, String?, String?, String?, String?, List<String>) -> Unit,
    firstDayOfWeek: Int = Calendar.MONDAY,
) {
    var description by remember { mutableStateOf("") }
    var project by remember { mutableStateOf(initialProject ?: "") }
    var tags by remember { mutableStateOf(initialTags) }
    var priority by remember { mutableStateOf<String?>(null) }
    var newTag by remember { mutableStateOf("") }

    var waitDate by remember { mutableStateOf<String?>(null) }
    var dueDate by remember { mutableStateOf<String?>(null) }
    var scheduledDate by remember { mutableStateOf<String?>(null) }

    var activePicker by remember { mutableStateOf<DatePickerType?>(null) }
    val datePickerState = rememberDatePickerState()

    val filteredProjects =
        remember(project) {
            if (project.isBlank()) {
                emptyList()
            } else {
                availableProjects.filter { it.contains(project, ignoreCase = true) && it != project }
            }
        }

    val filteredTags =
        remember(newTag) {
            if (newTag.isBlank()) {
                emptyList()
            } else {
                availableTags.filter {
                    it.contains(newTag, ignoreCase = true) && it != newTag && !tags.contains(it)
                }
            }
        }

    val resetAndDismiss = {
        description = ""
        project = ""
        tags = emptyList()
        priority = null
        waitDate = null
        dueDate = null
        scheduledDate = null
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = resetAndDismiss,
        modifier = Modifier.testTag("AddTaskDialog"),
        title = { Text("New Task", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth().testTag("DescriptionInput"),
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
                    modifier = Modifier.fillMaxWidth().testTag("ProjectInput"),
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

                if (tags.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(tags) { tag ->
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
                                androidx.compose.ui.Modifier.semantics {
                                    contentDescription = "AddTagButton"
                                },
                        ) {
                            Text("Add", style = MaterialTheme.typography.labelLarge)
                        }
                    },
                )

                if (filteredTags.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
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
                        date = dueDate,
                        icon = Icons.Default.Event,
                        onClick = { activePicker = DatePickerType.DUE },
                    )
                    DatePickerIconButton(
                        label = "Sch",
                        date = scheduledDate,
                        icon = Icons.Default.Schedule,
                        onClick = { activePicker = DatePickerType.SCHEDULED },
                    )
                    DatePickerIconButton(
                        label = "Wait",
                        date = waitDate,
                        icon = Icons.Default.CalendarMonth,
                        onClick = { activePicker = DatePickerType.WAIT },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (description.isNotBlank()) {
                        val proj = if (project.isNotBlank()) project.trim() else null
                        onConfirm(description, proj, tags, waitDate, dueDate, scheduledDate, null, priority, emptyList())
                        description = ""
                        project = ""
                        tags = emptyList()
                        priority = null
                        waitDate = null
                        dueDate = null
                        scheduledDate = null
                    }
                },
                shape = RoundedCornerShape(8.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = androidx.compose.ui.graphics.Color.White,
                    ),
            ) {
                Text("Create", color = androidx.compose.ui.graphics.Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = resetAndDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
    )

    if (activePicker != null) {
        DatePickerDialog(
            onDismissRequest = { activePicker = null },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val instant = Instant.ofEpochMilli(millis)
                        val formatted = DateTimeFormatter.ISO_INSTANT.format(instant)
                        when (activePicker) {
                            DatePickerType.DUE -> dueDate = formatted
                            DatePickerType.WAIT -> waitDate = formatted
                            DatePickerType.SCHEDULED -> scheduledDate = formatted
                            null -> {}
                        }
                    }
                    activePicker = null
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    when (activePicker) {
                        DatePickerType.DUE -> dueDate = null
                        DatePickerType.WAIT -> waitDate = null
                        DatePickerType.SCHEDULED -> scheduledDate = null
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
