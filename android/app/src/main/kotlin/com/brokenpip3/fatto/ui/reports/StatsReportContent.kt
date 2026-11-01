package com.brokenpip3.fatto.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.brokenpip3.fatto.data.reports.StatsReport
import com.brokenpip3.fatto.ui.theme.FattoMetrics
import com.brokenpip3.fatto.ui.theme.FattoSpacing
import com.brokenpip3.fatto.ui.theme.effectiveFontScale
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun StatsReportContent(
    report: StatsReport,
    zone: ZoneId,
) {
    val format =
        NumberFormat.getNumberInstance().apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }
    val dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    StatsStatusPanel(report)
    Text("Collection", style = MaterialTheme.typography.titleMedium)
    FlowRow(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FattoSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(FattoSpacing.medium), maxItemsInEachRow = 3,
    ) {
        CollectionTile(
            "Projects", "${report.projects}", Icons.Default.AccountTree,
            Modifier.weight(1f).widthIn(min = FattoMetrics.largeTouchTarget * effectiveFontScale().coerceAtLeast(1f)),
        )
        CollectionTile(
            "Tags", "${report.uniqueTags}", Icons.Default.Tag,
            Modifier.weight(1f).widthIn(min = FattoMetrics.largeTouchTarget * effectiveFontScale().coerceAtLeast(1f)),
        )
        CollectionTile(
            "Annotations", "${report.annotations}", Icons.AutoMirrored.Filled.Notes,
            Modifier.weight(1f).widthIn(min = FattoMetrics.largeTouchTarget * effectiveFontScale().coerceAtLeast(1f)),
        )
    }
    CoverageRow("With tags", report.taggedTasks, report.taggedPercent, Icons.Default.Sell, format)
    CoverageRow("With projects", report.projectTasks, report.projectPercent, Icons.Default.AccountTree, format)
    Text("Details", style = MaterialTheme.typography.titleMedium)
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(FattoSpacing.large), verticalArrangement = Arrangement.spacedBy(FattoSpacing.medium)) {
            StatsDetail("Oldest task", report.oldestEntry?.atZone(zone)?.format(dates) ?: "—")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            StatsDetail("Newest task", report.newestEntry?.atZone(zone)?.format(dates) ?: "—")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            StatsDetail("Avg. description", "${format.format(report.averageDescriptionCodePoints)} characters")
        }
    }
}

@Composable
private fun StatsStatusPanel(report: StatsReport) {
    Surface(
        Modifier.fillMaxWidth().testTag("StatsStatusPanel"), color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(FattoSpacing.large), verticalArrangement = Arrangement.spacedBy(FattoSpacing.large)) {
            Text("Total tasks", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${report.total}", style = MaterialTheme.typography.displaySmall)
            if (report.total > 0) {
                Row(Modifier.fillMaxWidth().height(FattoSpacing.small)) {
                    listOf(
                        report.pending to MaterialTheme.colorScheme.primary, report.completed to MaterialTheme.colorScheme.secondary,
                        report.deleted to MaterialTheme.colorScheme.error,
                    ).forEach { (count, color) ->
                        if (count > 0) Surface(Modifier.weight(count.toFloat()).fillMaxHeight(), color = color) {}
                    }
                }
            }
            FlowRow(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FattoSpacing.large),
                verticalArrangement = Arrangement.spacedBy(FattoSpacing.medium),
            ) {
                listOf(
                    Triple("Pending", report.pending, MaterialTheme.colorScheme.primary),
                    Triple("Completed", report.completed, MaterialTheme.colorScheme.secondary),
                    Triple("Deleted", report.deleted, MaterialTheme.colorScheme.error),
                ).forEach { (label, count, color) ->
                    Column(Modifier.weight(1f).widthIn(min = FattoMetrics.largeTouchTarget * effectiveFontScale().coerceAtLeast(1f))) {
                        Text("$count", style = MaterialTheme.typography.titleLarge)
                        Text(label, style = MaterialTheme.typography.bodySmall, color = color)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            FlowRow(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(FattoSpacing.small),
            ) {
                Text("Active ${report.active}", style = MaterialTheme.typography.bodyMedium)
                Text("Waiting ${report.waiting}", style = MaterialTheme.typography.bodyMedium)
            }
            Text("Of pending", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CollectionTile(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier,
) {
    Surface(modifier, color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(FattoSpacing.small), verticalArrangement = Arrangement.spacedBy(FattoSpacing.small)) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Icon(icon, null, Modifier.padding(FattoSpacing.small).size(FattoMetrics.smallIcon))
            }
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatsDetail(
    label: String,
    value: String,
) {
    FlowRow(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(FattoSpacing.small),
    ) {
        Text(
            label, Modifier.padding(end = FattoSpacing.medium), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CoverageRow(
    label: String,
    count: Int,
    percent: Double,
    icon: ImageVector,
    format: NumberFormat,
) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(FattoSpacing.large), verticalArrangement = Arrangement.spacedBy(FattoSpacing.small)) {
            Row(horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small)) {
                Icon(icon, null, Modifier.size(FattoMetrics.smallIcon), tint = MaterialTheme.colorScheme.primary)
                Text(label, style = MaterialTheme.typography.titleSmall)
            }
            FlowRow(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(FattoSpacing.small),
            ) {
                Text("$count tasks", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${format.format(percent)}%", style = MaterialTheme.typography.titleMedium)
            }
            LinearProgressIndicator(
                progress = { (percent / 100).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
    }
}
