package com.brokenpip3.fatto.ui.tasklist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.review.ReviewPolicy
import com.brokenpip3.fatto.ui.common.LinkedTaskText
import com.brokenpip3.fatto.ui.theme.FattoSpacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReviewTaskCard(task: Task) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(FattoSpacing.extraLarge), verticalArrangement = Arrangement.spacedBy(FattoSpacing.large)) {
            LinkedTaskText(task.description, style = MaterialTheme.typography.titleSmall)
            task.project?.let { project ->
                Row(horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small)) {
                    Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text("Project", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(project, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (task.userTags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small),
                    verticalArrangement = Arrangement.spacedBy(FattoSpacing.small),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Label, "Tags", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    task.userTags.forEach { tag ->
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                            Text(
                                tag, Modifier.padding(horizontal = FattoSpacing.small, vertical = FattoSpacing.xSmall),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
            HorizontalDivider()
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(FattoSpacing.large),
                verticalArrangement = Arrangement.spacedBy(FattoSpacing.small),
            ) {
                Text(
                    task.due?.let {
                        "Due ${readableDate(it)}"
                    } ?: "No due date",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val priority =
                    when (task.priority) {
                        "H" -> "High priority"
                        "M" -> "Medium priority"
                        "L" -> "Low priority"
                        null -> "No priority"
                        else -> "Priority ${task.priority}"
                    }
                Text(priority, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            task.wait?.let { ReviewMetadata("Waiting", "Until ${readableDate(it)}") }
            val reviewed = task.udas["reviewed"]?.takeIf { it.isNotEmpty() }
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.small) {
                Row(
                    Modifier.fillMaxWidth().padding(FattoSpacing.large),
                    horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Last reviewed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            reviewed?.let { raw -> ReviewPolicy.parseReviewed(raw).getOrNull()?.let { readableDate(it.toString()) } ?: raw }
                                ?: "Never reviewed",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            task.annotations.forEach { annotation ->
                HorizontalDivider()
                LinkedTaskText(annotation.description, style = MaterialTheme.typography.bodyMedium)
            }
            var expanded by androidx.compose.runtime.remember(task.uuid) { androidx.compose.runtime.mutableStateOf(false) }
            androidx.compose.material3.TextButton(onClick = {
                expanded = !expanded
            }) { Text(if (expanded) "Fewer details" else "More task details") }
            if (expanded) {
                if (task.wait == null) ReviewMetadata("Waiting", "Not waiting")
                ReviewMetadata("Scheduled", readableDate(task.scheduled))
                ReviewMetadata("Started", readableDate(task.start))
                ReviewMetadata("Dependencies", task.dependencies.size.toString())
                task.udas.filterKeys { it != "reviewed" }.forEach { (key, value) -> ReviewMetadata(key, value) }
                Text(task.uuid, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
