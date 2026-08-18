package com.brokenpip3.fatto.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.brokenpip3.fatto.data.DateTimeUtils
import com.brokenpip3.fatto.data.model.Task
import uniffi.taskchampion_android.TaskStatus
import java.time.Instant

@Composable
fun TaskPickerDialog(
    title: String,
    tasks: List<Task>,
    onDismiss: () -> Unit,
    onConfirm: (List<Task>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }

    val filtered =
        remember(query, tasks) {
            val q = query.trim()
            if (q.isEmpty()) {
                tasks
            } else {
                tasks.filter { t ->
                    t.description.contains(q, ignoreCase = true) ||
                        t.project?.contains(q, ignoreCase = true) == true ||
                        t.userTags.any { it.contains(q, ignoreCase = true) }
                }
            }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.testTag("TaskPickerDialog")) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search tasks") },
                    singleLine = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "TaskPickerSearch" },
                    colors =
                        TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                )
                Spacer(Modifier.height(8.dp))
                if (filtered.isEmpty()) {
                    Text(
                        text = "No tasks found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp),
                    ) {
                        items(filtered, key = { it.uuid }) { t ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (t.uuid in selected) {
                                                selected.remove(t.uuid)
                                            } else {
                                                selected.add(t.uuid)
                                            }
                                        }
                                        .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = t.uuid in selected,
                                    onCheckedChange = null,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = t.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color =
                                            if (t.status == TaskStatus.COMPLETED) {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                    )
                                    val subtitle =
                                        listOfNotNull(
                                            t.project,
                                            t.userTags.joinToString(" ").takeIf { it.isNotEmpty() },
                                        ).joinToString(" · ")
                                    if (subtitle.isNotEmpty()) {
                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                when {
                                    t.status == TaskStatus.COMPLETED -> {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Completed",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    DateTimeUtils.parseToInstant(t.wait)
                                        ?.isAfter(Instant.now()) == true -> {
                                        Icon(
                                            Icons.Default.Schedule,
                                            contentDescription = "Waiting",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(filtered.filter { it.uuid in selected })
                },
                enabled = selected.isNotEmpty(),
            ) {
                Text("Add (${selected.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
