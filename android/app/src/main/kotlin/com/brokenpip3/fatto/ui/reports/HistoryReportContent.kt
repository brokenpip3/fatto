package com.brokenpip3.fatto.ui.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.brokenpip3.fatto.data.reports.HistoryReport
import com.brokenpip3.fatto.ui.theme.FattoSpacing
import com.brokenpip3.fatto.ui.theme.effectiveFontScale
import com.brokenpip3.fatto.vm.HistoryPresentation

@Composable
internal fun HistoryReportContent(
    report: HistoryReport,
    presentation: HistoryPresentation,
    onPresentation: (HistoryPresentation) -> Unit,
) {
    ReportMetrics(
        listOf(
            "Added" to "${report.totals.added}", "Completed" to "${report.totals.completed}",
            "Net growth" to "${report.totals.net}",
        ),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small)) {
        HistoryPresentation.entries.forEach { mode ->
            FilterChip(selected = mode == presentation, onClick = {
                onPresentation(mode)
            }, label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) })
        }
    }
    if (presentation == HistoryPresentation.CHART) HistoryChart(report)
    if (presentation == HistoryPresentation.TABLE) {
        Column(Modifier.horizontalScroll(rememberScrollState()).testTag("HistoryTable")) {
            HistoryTableRow(listOf("Period", "Added", "Completed", "Deleted", "Net"), heading = true)
            report.rows.forEach { row ->
                HistoryTableRow(
                    listOf(
                        bucketLabel(row.bucket),
                        "${row.counts.added}",
                        "${row.counts.completed}",
                        "${row.counts.deleted}",
                        "${row.counts.net}",
                    ),
                )
            }
        }
    }
    if (report.totals.added + report.totals.completed + report.totals.deleted ==
        0
    ) {
        Text("No recorded events in this period.", style = MaterialTheme.typography.bodyLarge)
    }
    if (report.missingEntryCount + report.missingEndCount >
        0
    ) {
        Text(
            "Missing dates: ${report.missingEntryCount} entry · ${report.missingEndCount} end",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun HistoryTableRow(
    values: List<String>,
    heading: Boolean = false,
) {
    val columnScale = effectiveFontScale().coerceAtLeast(1f)
    Row(Modifier.padding(vertical = FattoSpacing.medium)) {
        values.forEachIndexed { index, value ->
            Text(
                value,
                Modifier.width(
                    if (index ==
                        0
                    ) {
                        210.dp * columnScale
                    } else {
                        100.dp * columnScale
                    },
                ).padding(end = FattoSpacing.small),
                style = if (heading) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun HistoryChart(report: HistoryReport) {
    val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.error)
    val summary =
        report.rows.joinToString("; ") {
            "${bucketLabel(it.bucket)}: ${it.counts.added} added, ${it.counts.completed} completed, ${it.counts.deleted} deleted"
        }
    Column(Modifier.testTag("HistoryChart").horizontalScroll(rememberScrollState())) {
        val width = maxOf(280, report.rows.size * 100).dp * effectiveFontScale().coerceAtLeast(1f)
        val maxCount = report.rows.maxOfOrNull { maxOf(it.counts.added, it.counts.completed, it.counts.deleted) }?.coerceAtLeast(1) ?: 1
        Canvas(Modifier.width(width).height(160.dp).semantics { contentDescription = summary }) {
            val group = size.width / report.rows.size.coerceAtLeast(1)
            report.rows.forEachIndexed { i, row ->
                listOf(row.counts.added, row.counts.completed, row.counts.deleted).forEachIndexed { series, value ->
                    val height = size.height * value / maxCount
                    drawRect(
                        colors[series],
                        Offset(
                            i * group + group * (.12f + series * .25f),
                            size.height - height,
                        ),
                        Size(
                            group * .18f,
                            height,
                        ),
                    )
                }
            }
        }
        Row(Modifier.width(width)) {
            report.rows.forEach {
                Text(
                    bucketLabel(it.bucket),
                    Modifier.width(
                        width / report.rows.size.coerceAtLeast(1),
                    ).padding(FattoSpacing.xSmall),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(FattoSpacing.large)) {
        listOf("Added", "Completed", "Deleted").forEachIndexed {
            i,
            name,
            ->
            Text("■ $name", color = colors[i], style = MaterialTheme.typography.bodyMedium)
        }
    }
}
