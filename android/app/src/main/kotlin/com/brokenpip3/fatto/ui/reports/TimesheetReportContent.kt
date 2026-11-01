package com.brokenpip3.fatto.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.brokenpip3.fatto.data.reports.TimesheetItem
import com.brokenpip3.fatto.data.reports.TimesheetReport
import com.brokenpip3.fatto.ui.theme.FattoMetrics
import com.brokenpip3.fatto.ui.theme.FattoSpacing
import com.brokenpip3.fatto.ui.theme.FattoStroke
import com.brokenpip3.fatto.ui.theme.effectiveFontScale
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun TimesheetReportContent(
    report: TimesheetReport,
    zone: ZoneId,
    expansionStates: SaveableStateHolder,
) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FattoSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(FattoSpacing.medium),
    ) {
        ActivityMetric(
            "Completed", report.weeks.sumOf { it.completed.size }, Icons.Default.CheckCircle,
            MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer,
            Modifier.weight(1f).widthIn(min = FattoMetrics.largeTouchTarget * 2 * effectiveFontScale().coerceAtLeast(1f)),
        )
        ActivityMetric(
            "Started", report.weeks.sumOf { it.started.size }, Icons.Default.PlayArrow,
            MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer,
            Modifier.weight(1f).widthIn(min = FattoMetrics.largeTouchTarget * 2 * effectiveFontScale().coerceAtLeast(1f)),
        )
    }
    report.weeks.asReversed().forEach { week ->
        expansionStates.SaveableStateProvider(week.bucket.startDate.toString()) {
            Text(bucketLabel(week.bucket), style = MaterialTheme.typography.titleMedium)
            if (week.completed.isEmpty() && week.started.isEmpty()) {
                Text("No activity", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            } else {
                TimesheetSection("Completed", week.completed, zone, true)
                TimesheetSection("Started", week.started, zone, false)
            }
        }
    }
    if (report.missingEndCount + report.missingStartCount > 0) {
        Text(
            "Missing dates: ${report.missingEndCount} end · ${report.missingStartCount} start",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActivityMetric(
    label: String,
    count: Int,
    icon: ImageVector,
    container: Color,
    content: Color,
    modifier: Modifier,
) {
    Surface(modifier, color = container, contentColor = content, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(FattoSpacing.large), verticalArrangement = Arrangement.spacedBy(FattoSpacing.medium)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small)) {
                Icon(icon, null, Modifier.size(FattoMetrics.smallIcon))
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
            Text("$count", style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun TimesheetSection(
    title: String,
    items: List<TimesheetItem>,
    zone: ZoneId,
    completed: Boolean,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(true) }
    val action = if (expanded) "Collapse" else "Expand"
    val accent = if (completed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    val container = if (completed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
    val onContainer = if (completed) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
    val icon = if (completed) Icons.Default.CheckCircle else Icons.Default.PlayArrow
    Row(
        Modifier.fillMaxWidth().heightIn(min = FattoMetrics.minTouchTarget)
            .clickable(role = Role.Button, onClickLabel = "$action $title") { expanded = !expanded }
            .semantics {
                contentDescription = "$action $title"
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small),
    ) {
        Icon(icon, null, Modifier.size(FattoMetrics.smallIcon), tint = accent)
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = accent)
        Surface(color = container, contentColor = onContainer, shape = MaterialTheme.shapes.small) {
            Text(
                "${items.size}", Modifier.padding(horizontal = FattoSpacing.small, vertical = FattoSpacing.xSmall),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = accent)
    }
    if (!expanded) return
    if (items.isEmpty()) {
        Text(
            "No ${title.lowercase()} tasks", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    items.forEach { task ->
        Card(
            Modifier.fillMaxWidth().testTag("TimesheetTask_${task.uuid}"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Row(
                Modifier.drawWithContent {
                    drawContent()
                    drawRect(accent, size = Size(FattoStroke.projectStripe.toPx(), size.height))
                }.padding(FattoSpacing.large),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(FattoSpacing.medium),
            ) {
                Box(Modifier.background(container, MaterialTheme.shapes.small).padding(FattoSpacing.xSmall)) {
                    Icon(icon, null, Modifier.size(FattoMetrics.smallIcon), tint = onContainer)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FattoSpacing.small)) {
                    Text(task.description, style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(FattoSpacing.small),
                    ) {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.small) {
                            Text(
                                task.project ?: "No project",
                                Modifier.padding(horizontal = FattoSpacing.small, vertical = FattoSpacing.xSmall),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            task.event.atZone(zone).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
