package com.brokenpip3.fatto.ui.tasklist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.dateValue
import com.brokenpip3.fatto.data.model.visibleUdas
import com.brokenpip3.fatto.data.review.ReviewPolicy
import com.brokenpip3.fatto.ui.common.LinkedTaskText
import com.brokenpip3.fatto.ui.common.readableTaskDate
import com.brokenpip3.fatto.ui.theme.FattoMetrics
import com.brokenpip3.fatto.ui.theme.FattoSpacing
import com.brokenpip3.fatto.ui.theme.FattoStroke

@Composable
internal fun ReviewTaskCard(task: Task) {
    var expanded by remember(task.uuid) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FattoSpacing.medium)) {
        Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
            Column(
                Modifier.fillMaxWidth().padding(FattoSpacing.extraLarge),
                verticalArrangement = Arrangement.spacedBy(FattoSpacing.medium),
            ) {
                Text("Task to review", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinkedTaskText(task.description, style = MaterialTheme.typography.titleLarge)
                task.project?.let { project ->
                    Row(horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small)) {
                        Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            project, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        ReviewTaskContext(task)
        if (task.annotations.isNotEmpty()) ReviewAnnotations(task)
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.heightIn(min = FattoMetrics.minTouchTarget),
        ) { Text(if (expanded) "Fewer details" else "More task details") }
        if (expanded) ReviewExpandedMetadata(task)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewTaskContext(task: Task) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Column(
            Modifier.fillMaxWidth().padding(FattoSpacing.large),
            verticalArrangement = Arrangement.spacedBy(FattoSpacing.large),
        ) {
            if (task.userTags.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(FattoSpacing.small)) {
                    Text("Tags", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small),
                        verticalArrangement = Arrangement.spacedBy(FattoSpacing.small),
                    ) {
                        task.userTags.forEach { tag ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.small,
                                border = BorderStroke(FattoStroke.subtle, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Text(
                                    tag, Modifier.padding(horizontal = FattoSpacing.small, vertical = FattoSpacing.xSmall),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                HorizontalDivider()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Event, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    task.due?.let {
                        "Due ${readableTaskDate(task.dateValue("due"))}"
                    } ?: "No due date",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            val priority =
                when (task.priority) {
                    "H" -> "High priority"
                    "M" -> "Medium priority"
                    "L" -> "Low priority"
                    null -> "No priority"
                    else -> "Priority ${task.priority}"
                }
            Row(horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Flag, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(priority, style = MaterialTheme.typography.bodyMedium)
            }
            task.wait?.let { ReviewMetadata("Waiting", "Until ${readableTaskDate(task.dateValue("wait"))}") }
            val reviewed = task.udas["reviewed"]?.takeIf { it.isNotEmpty() }
            Row(horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f)) {
                    Text("Last reviewed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        reviewed?.let { raw -> ReviewPolicy.parseReviewed(raw).getOrNull()?.let { readableDate(it.toString()) } ?: raw }
                            ?: "Never reviewed",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewAnnotations(task: Task) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
        Column(
            Modifier.fillMaxWidth().padding(FattoSpacing.large),
            verticalArrangement = Arrangement.spacedBy(FattoSpacing.medium),
        ) {
            Text(
                if (task.annotations.size == 1) "Annotation" else "Annotations",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            task.annotations.forEachIndexed { index, annotation ->
                if (index > 0) HorizontalDivider()
                LinkedTaskText(annotation.description, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ReviewExpandedMetadata(task: Task) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Column(
            Modifier.fillMaxWidth().padding(FattoSpacing.large),
            verticalArrangement = Arrangement.spacedBy(FattoSpacing.medium),
        ) {
            if (task.wait == null) ReviewMetadata("Waiting", "Not waiting")
            ReviewMetadata("Scheduled", readableTaskDate(task.dateValue("scheduled")))
            ReviewMetadata("Started", readableDate(task.start))
            ReviewMetadata("Dependencies", task.dependencies.size.toString())
            task.visibleUdas.filterKeys { it != "reviewed" }.forEach { (key, value) -> ReviewMetadata(key, value) }
            Text(task.uuid, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReviewMetadata(
    label: String,
    value: String,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FattoSpacing.large)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium)
    }
}
