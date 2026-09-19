package com.brokenpip3.fatto.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

private enum class SelectionMode {
    SINGLE,
    MULTIPLE,
}

@Composable
fun ProjectPickerDialog(
    projects: List<String>,
    selectedProject: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    SearchableStringSelectionDialog(
        title = "Select project",
        searchLabel = "Search projects",
        emptyMessage = "No projects found",
        options = projects,
        initialSelection = selectedProject?.let(::setOf).orEmpty(),
        mode = SelectionMode.SINGLE,
        rootTestTag = "ProjectPickerDialog",
        optionTestTagPrefix = "ProjectPickerOption-",
        confirmText = { "Select" },
        confirmTestTag = "ProjectPickerConfirmButton",
        onDismiss = onDismiss,
        onConfirm = { selection -> onConfirm(selection.single()) },
    )
}

@Composable
fun TagPickerDialog(
    tags: List<String>,
    selectedTags: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    SearchableStringSelectionDialog(
        title = "Select tags",
        searchLabel = "Search tags",
        emptyMessage = "No tags found",
        options = tags,
        initialSelection = selectedTags,
        mode = SelectionMode.MULTIPLE,
        rootTestTag = "TagPickerDialog",
        optionTestTagPrefix = "TagPickerOption-",
        confirmText = { count -> "Apply ($count)" },
        confirmTestTag = "TagPickerConfirmButton",
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

@Composable
@Suppress("LongParameterList")
private fun SearchableStringSelectionDialog(
    title: String,
    searchLabel: String,
    emptyMessage: String,
    options: List<String>,
    initialSelection: Set<String>,
    mode: SelectionMode,
    rootTestTag: String,
    optionTestTagPrefix: String,
    confirmText: (Int) -> String,
    confirmTestTag: String,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    val choices =
        remember(options, initialSelection) {
            (options + initialSelection)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
        }
    var query by remember { mutableStateOf("") }
    var temporarySelection by
        remember {
            mutableStateOf(
                initialSelection
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet(),
            )
        }
    val filteredChoices =
        remember(choices, query) {
            val trimmedQuery = query.trim()
            if (trimmedQuery.isEmpty()) {
                choices
            } else {
                choices.filter { it.contains(trimmedQuery, ignoreCase = true) }
            }
        }
    val confirmationEnabled = mode == SelectionMode.MULTIPLE || temporarySelection.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.testTag(rootTestTag)) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(searchLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("${rootTestTag.removeSuffix("Dialog")}Search"),
                    colors =
                        TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                )
                Spacer(Modifier.height(8.dp))
                if (filteredChoices.isEmpty()) {
                    Text(
                        text = emptyMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    ) {
                        items(filteredChoices, key = { it }) { option ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .testTag("$optionTestTagPrefix$option")
                                        .then(
                                            when (mode) {
                                                SelectionMode.SINGLE ->
                                                    Modifier.selectable(
                                                        selected = option in temporarySelection,
                                                        onClick = { temporarySelection = setOf(option) },
                                                        role = Role.RadioButton,
                                                    )
                                                SelectionMode.MULTIPLE ->
                                                    Modifier.toggleable(
                                                        value = option in temporarySelection,
                                                        onValueChange = { checked ->
                                                            temporarySelection =
                                                                if (checked) {
                                                                    temporarySelection + option
                                                                } else {
                                                                    temporarySelection - option
                                                                }
                                                        },
                                                        role = Role.Checkbox,
                                                    )
                                            },
                                        ).padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                when (mode) {
                                    SelectionMode.SINGLE ->
                                        RadioButton(
                                            selected = option in temporarySelection,
                                            onClick = null,
                                        )
                                    SelectionMode.MULTIPLE ->
                                        Checkbox(
                                            checked = option in temporarySelection,
                                            onCheckedChange = null,
                                        )
                                }
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(temporarySelection) },
                enabled = confirmationEnabled,
                modifier = Modifier.testTag(confirmTestTag),
            ) {
                Text(confirmText(temporarySelection.size))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("${rootTestTag.removeSuffix("Dialog")}CancelButton"),
            ) {
                Text("Cancel")
            }
        },
    )
}
