package com.brokenpip3.fatto.ui.tasklist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskFilterBuilderSheet(
    initialState: TaskFilterState,
    availableProjects: List<String>,
    availableTags: Set<String>,
    contextName: String,
    onDismiss: () -> Unit,
    onApply: (TaskFilterState) -> Unit,
    onSaveContext: (String, TaskFilterState) -> Unit,
) {
    var state by remember(initialState) { mutableStateOf(initialState) }
    var projectQuery by remember(initialState.project) { mutableStateOf(initialState.project ?: "") }
    var tagQuery by remember { mutableStateOf("") }
    var projectExpanded by remember { mutableStateOf(false) }
    var tagsExpanded by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val filteredProjects =
        remember(projectQuery, availableProjects) {
            if (projectQuery.isBlank()) {
                availableProjects.sorted().take(12)
            } else {
                availableProjects
                    .filter { it.contains(projectQuery, ignoreCase = true) && it != state.project }
                    .sorted()
                    .take(12)
            }
        }
    val filteredTags =
        remember(tagQuery, availableTags, state.tags) {
            if (tagQuery.isBlank()) {
                emptyList()
            } else {
                availableTags
                    .filter { it.contains(tagQuery, ignoreCase = true) && !state.tags.contains(it) }
                    .sorted()
                    .take(12)
            }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filters") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(scrollState),
            ) {
                if (state.rawExpressionText != null) {
                    OutlinedTextField(
                        value = state.rawExpressionText.orEmpty(),
                        onValueChange = { state = state.copy(rawExpressionText = it) },
                        label = { Text("Expression") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = state.descriptionQuery,
                        onValueChange = { state = state.copy(descriptionQuery = it) },
                        label = { Text("Search") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (state.rawExpressionText == null) {
                    AccordionSection(
                        title = state.project?.let { "Project: $it" } ?: "Project",
                        icon = Icons.Default.AccountTree,
                        count = if (state.project == null) null else 1,
                        expanded = projectExpanded,
                        onToggle = { projectExpanded = !projectExpanded },
                    ) {
                        OutlinedTextField(
                            value = projectQuery,
                            onValueChange = { projectQuery = it },
                            label = { Text("Find project") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            SuggestionChip(
                                label = "Any project",
                                onClick = {
                                    state = state.copy(project = null)
                                    projectQuery = ""
                                },
                            )
                            filteredProjects.forEach { project ->
                                SuggestionChip(
                                    label = project,
                                    onClick = {
                                        state = state.copy(project = project)
                                        projectQuery = project
                                    },
                                )
                            }
                        }
                    }

                    AccordionSection(
                        title = if (state.tags.isEmpty()) "Tags" else "Tags: ${state.tags.size}",
                        icon = Icons.Default.Tag,
                        count = state.tags.size.takeIf { it > 0 },
                        expanded = tagsExpanded,
                        onToggle = { tagsExpanded = !tagsExpanded },
                    ) {
                        if (state.tags.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = 8.dp),
                            ) {
                                state.tags.sorted().forEach { tag ->
                                    TagChip(tag = tag, onRemove = { state = state.copy(tags = state.tags - tag) })
                                }
                            }
                        }
                        OutlinedTextField(
                            value = tagQuery,
                            onValueChange = { tagQuery = it },
                            label = { Text("Add tag") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                TextButton(
                                    onClick = {
                                        val tag = tagQuery.trim()
                                        if (tag.isNotBlank()) {
                                            state = state.copy(tags = state.tags + tag)
                                            tagQuery = ""
                                        }
                                    },
                                ) {
                                    Text("Add")
                                }
                            },
                        )
                        if (filteredTags.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                filteredTags.forEach { tag ->
                                    SuggestionChip(
                                        label = tag,
                                        onClick = {
                                            state = state.copy(tags = state.tags + tag)
                                            tagQuery = ""
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 8.dp),
            ) {
                TextButton(
                    onClick = {
                        state = TaskFilterState()
                        projectQuery = ""
                        tagQuery = ""
                    },
                ) {
                    Text("Clear")
                }
                TextButton(onClick = { showNameDialog = true }) {
                    Text("Save")
                }
                Button(
                    onClick = { onApply(state) },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Apply")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )

    if (showNameDialog) {
        SaveContextDialog(
            initialName = contextName,
            filterState = state,
            onDismiss = { showNameDialog = false },
            onSave = { name ->
                onSaveContext(name, state)
                showNameDialog = false
            },
        )
    }
}

@Composable
private fun SaveContextDialog(
    initialName: String,
    filterState: TaskFilterState,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(initialName, filterState) { mutableStateOf(initialName.ifBlank { filterState.defaultContextName() }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save context") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Context name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim()) },
                enabled = name.isNotBlank(),
                modifier =
                    Modifier.semantics {
                        contentDescription = "Confirm save context"
                    },
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun TaskFilterState.defaultContextName(): String {
    return when {
        project != null -> "Project $project"
        tags.isNotEmpty() -> "Tags ${tags.sorted().joinToString(", ")}"
        descriptionQuery.isNotBlank() -> descriptionQuery.trim().take(32)
        else -> "Context"
    }
}
