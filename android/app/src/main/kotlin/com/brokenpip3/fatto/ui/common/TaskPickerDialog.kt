package com.brokenpip3.fatto.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.brokenpip3.fatto.data.DateTimeUtils
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.ui.theme.FattoFieldDefaults
import com.brokenpip3.fatto.ui.theme.FattoMetrics
import com.brokenpip3.fatto.ui.theme.FattoSpacing
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
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = FattoSpacing.medium),
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(modifier = Modifier.testTag("TaskPickerDialog")) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search tasks") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("TaskPickerSearch"),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(FattoMetrics.icon),
                        )
                    },
                    colors = FattoFieldDefaults.filledColors(),
                )
                Spacer(Modifier.height(FattoSpacing.small))
                if (filtered.isEmpty()) {
                    Text(
                        text = "No tasks found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = FattoSpacing.large),
                    )
                } else {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = FattoMetrics.pickerListMaxHeight),
                        verticalArrangement = Arrangement.spacedBy(FattoSpacing.small),
                    ) {
                        items(filtered, key = { it.uuid }) { t ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .toggleable(
                                            value = t.uuid in selected,
                                            onValueChange = { checked ->
                                                if (checked) selected.add(t.uuid) else selected.remove(t.uuid)
                                            },
                                            role = Role.Checkbox,
                                        )
                                        .heightIn(min = FattoMetrics.minTouchTarget)
                                        .padding(vertical = FattoSpacing.small),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(FattoSpacing.medium),
                            ) {
                                Checkbox(
                                    checked = t.uuid in selected,
                                    onCheckedChange = null,
                                    modifier = Modifier.size(FattoMetrics.icon),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = t.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color =
                                            if (t.status == TaskStatus.COMPLETED) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
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
                                            style = MaterialTheme.typography.bodySmall,
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
                                            modifier = Modifier.size(FattoMetrics.smallIcon),
                                        )
                                    }
                                    DateTimeUtils.parseToInstant(t.wait)
                                        ?.isAfter(Instant.now()) == true -> {
                                        Icon(
                                            Icons.Default.Schedule,
                                            contentDescription = "Waiting",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(FattoMetrics.smallIcon),
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
