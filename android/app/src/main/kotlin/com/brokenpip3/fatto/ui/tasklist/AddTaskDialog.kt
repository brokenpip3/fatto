package com.brokenpip3.fatto.ui.tasklist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.brokenpip3.fatto.data.TaskDateValue
import com.brokenpip3.fatto.data.model.RecurrenceRule
import com.brokenpip3.fatto.data.model.TaskEditResult
import com.brokenpip3.fatto.data.model.forField
import com.brokenpip3.fatto.data.model.withField
import com.brokenpip3.fatto.ui.common.ProjectPickerDialog
import com.brokenpip3.fatto.ui.common.TagPickerDialog
import com.brokenpip3.fatto.ui.theme.FattoFieldDefaults
import com.brokenpip3.fatto.ui.theme.FattoMetrics
import com.brokenpip3.fatto.ui.theme.FattoSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import uniffi.taskchampion_android.TaskAddProps
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    availableProjects: List<String>,
    availableTags: List<String>,
    initialProject: String? = null,
    initialTags: List<String> = emptyList(),
    initialDescription: String = "",
    onDismiss: () -> Unit,
    onConfirm: (TaskAddProps, Boolean, Set<String>) -> Unit,
    firstDayOfWeek: Int = Calendar.MONDAY,
    onCreate: (suspend (TaskAddProps, RecurrenceRule?, Boolean, Set<String>) -> TaskEditResult)? = null,
) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var saveError by rememberSaveable { mutableStateOf<String?>(null) }
    var rule by rememberSaveable(saver = TaskEditorSavedState.nullableRuleStateSaver) { mutableStateOf<RecurrenceRule?>(null) }
    var description by rememberSaveable(initialDescription) { mutableStateOf(initialDescription) }
    var project by rememberSaveable { mutableStateOf(initialProject ?: "") }
    var tags by rememberSaveable { mutableStateOf(initialTags) }
    var priority by rememberSaveable { mutableStateOf<String?>(null) }
    var openEditorAfterCreate by rememberSaveable { mutableStateOf(false) }
    var newTag by rememberSaveable { mutableStateOf("") }

    var waitDate by rememberSaveable { mutableStateOf<String?>(null) }
    var dueDate by rememberSaveable { mutableStateOf<String?>(null) }
    var scheduledDate by rememberSaveable { mutableStateOf<String?>(null) }

    var explicitDateClears by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var dateModes by rememberSaveable(saver = TaskEditorSavedState.modesStateSaver) {
        mutableStateOf(com.brokenpip3.fatto.data.model.calendarDateModes())
    }
    var activePicker by rememberSaveable { mutableStateOf<DatePickerType?>(null) }
    var showProjectPicker by rememberSaveable { mutableStateOf(false) }
    var showTagPicker by rememberSaveable { mutableStateOf(false) }

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
        if (!saving) {
            description = ""
            project = ""
            tags = emptyList()
            priority = null
            waitDate = null
            dueDate = null
            scheduledDate = null
            openEditorAfterCreate = false
            onDismiss()
        }
    }

    val addTag = {
        if (!saving && newTag.isNotBlank() && !tags.contains(newTag.trim())) {
            tags = tags + newTag.trim()
            newTag = ""
        }
    }

    AlertDialog(
        onDismissRequest = resetAndDismiss,
        modifier = Modifier.testTag("AddTaskDialog"),
        title = { Text("New Task", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Box(Modifier.fillMaxWidth()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(FattoSpacing.medium),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = FattoMetrics.formBodyMaxHeight)
                            .verticalScroll(rememberScrollState()),
                ) {
                    TextField(
                        enabled = !saving,
                        value = description,
                        onValueChange = { if (!saving) description = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth().testTag("DescriptionInput"),
                        colors = FattoFieldDefaults.filledColors(),
                    )

                    TextField(
                        enabled = !saving,
                        value = project,
                        onValueChange = { if (!saving) project = it },
                        label = { Text("Project") },
                        modifier = Modifier.fillMaxWidth().testTag("ProjectInput"),
                        colors = FattoFieldDefaults.filledColors(),
                        trailingIcon = {
                            IconButton(
                                onClick = { showProjectPicker = true },
                                enabled = !saving,
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
                                SuggestionChip(label = suggestion, onClick = { project = suggestion })
                            }
                        }
                    }

                    TaskDateAndPriorityControls(
                        enabled = !saving,
                        dateModes = dateModes,
                        priority = priority,
                        due = dueDate,
                        scheduled = scheduledDate,
                        wait = waitDate,
                        onPriorityChange = { priority = it },
                        onDateClick = { activePicker = it },
                        trailingControl = {
                            RecurrenceFields(rule, dueDate, { rule = it }, rule?.creationError(dueDate), enabled = !saving)
                        },
                    )
                    RecurrenceValidationMessage(rule, dueDate)
                    if (tags.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(tags) { tag ->
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

                    TextField(
                        enabled = !saving,
                        value = newTag,
                        onValueChange = { if (!saving) newTag = it },
                        label = { Text("Add Tag") },
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "TagInput" },
                        singleLine = true,
                        colors = FattoFieldDefaults.filledColors(),
                        trailingIcon = {
                            TaskTagActions(
                                enabled = !saving,
                                onAdd = addTag,
                                addContentDescription = "AddTagButton",
                                onSelect = { showTagPicker = true },
                            )
                        },
                    )

                    if (!saving && filteredTags.isNotEmpty()) {
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

                    if (saveError !=
                        null
                    ) {
                        Text(saveError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (saving) Text("Saving…", style = MaterialTheme.typography.bodyMedium)

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = FattoMetrics.minTouchTarget)
                                .testTag("OpenEditorAfterCreateSwitch")
                                .toggleable(
                                    value = openEditorAfterCreate,
                                    enabled = !saving,
                                    role = Role.Switch,
                                    onValueChange = { if (!saving) openEditorAfterCreate = it },
                                ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Open editor after creating", modifier = Modifier.weight(1f))
                        Switch(checked = openEditorAfterCreate, onCheckedChange = null)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && description.isNotBlank() && (rule == null || rule?.creationError(dueDate) == null),
                onClick = {
                    if (onCreate != null) {
                        saving = true
                        val props =
                            TaskAddProps(
                                description,
                                project.trim().takeIf {
                                    it.isNotEmpty()
                                },
                                tags, dueDate, waitDate, scheduledDate, null, priority, emptyList(),
                                dateModes = dateModes,
                            )
                        val capturedRule = rule
                        val capturedOpenEditor = openEditorAfterCreate
                        scope.launch {
                            try {
                                when (val result = onCreate(props, capturedRule, capturedOpenEditor, explicitDateClears)) {
                                    TaskEditResult.Success -> onDismiss()
                                    is TaskEditResult.Failure -> saveError = result.message
                                }
                            } catch (
                                e: CancellationException,
                            ) {
                                throw e
                            } catch (
                                e: Exception,
                            ) {
                                saveError = "Could not create task: ${e.message}"
                            } finally {
                                saving = false
                            }
                        }
                    } else if (description.isNotBlank()) {
                        onConfirm(
                            TaskAddProps(
                                description, project.trim().takeIf { it.isNotEmpty() }, tags, dueDate, waitDate, scheduledDate,
                                null, priority, emptyList(), dateModes,
                            ),
                            openEditorAfterCreate, explicitDateClears,
                        )
                    }
                },
                shape = MaterialTheme.shapes.medium,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = resetAndDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
    )

    if (showProjectPicker && !saving) {
        ProjectPickerDialog(
            projects = (availableProjects + project).toSet().toList(),
            selectedProject = project.ifBlank { null },
            onDismiss = { showProjectPicker = false },
            onConfirm = { selectedProject ->
                project = selectedProject
                showProjectPicker = false
            },
        )
    }

    if (showTagPicker && !saving) {
        TagPickerDialog(
            tags = (availableTags + tags).toSet().toList(),
            selectedTags = tags.toSet(),
            onDismiss = { showTagPicker = false },
            onConfirm = { selectedTags ->
                tags = selectedTags.toList()
                showTagPicker = false
            },
        )
    }

    activePicker?.takeUnless { saving }?.let { picker ->
        val current =
            when (picker) {
                DatePickerType.DUE -> dueDate
                DatePickerType.WAIT -> waitDate
                DatePickerType.SCHEDULED -> scheduledDate
            }
        val field = picker.name.lowercase()
        DateTimePickerDialog(
            current = TaskDateValue(current, dateModes.forField(field)),
            firstDayOfWeek = firstDayOfWeek,
            onConfirm = { value ->
                dateModes = dateModes.withField(field, value.mode)
                explicitDateClears = explicitDateClears - field
                when (picker) {
                    DatePickerType.DUE -> dueDate = value.timestamp
                    DatePickerType.WAIT -> waitDate = value.timestamp
                    DatePickerType.SCHEDULED -> scheduledDate = value.timestamp
                }
                activePicker = null
            },
            onClear = {
                dateModes = dateModes.withField(field, uniffi.taskchampion_android.DateDisplayMode.CALENDAR_DATE)
                explicitDateClears = explicitDateClears + field
                when (picker) {
                    DatePickerType.DUE -> dueDate = null
                    DatePickerType.WAIT -> waitDate = null
                    DatePickerType.SCHEDULED -> scheduledDate = null
                }
                activePicker = null
            },
            onDismiss = { activePicker = null },
        )
    }
}

@Composable
private fun RecurrenceValidationMessage(
    rule: RecurrenceRule?,
    due: String?,
) {
    val message = rule?.creationError(due) ?: return
    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}
