package com.brokenpip3.fatto.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.brokenpip3.fatto.data.reports.ProjectScope
import com.brokenpip3.fatto.data.reports.ReportBucket
import com.brokenpip3.fatto.data.reports.ReportGrouping
import com.brokenpip3.fatto.ui.theme.FattoMetrics
import com.brokenpip3.fatto.ui.theme.FattoSpacing
import com.brokenpip3.fatto.vm.ReportPage
import com.brokenpip3.fatto.vm.ReportsUiState
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun ReportControls(
    state: ReportsUiState,
    onProject: (ProjectScope) -> Unit,
    onGrouping: (ReportGrouping) -> Unit,
    onMovePeriod: (Int) -> Unit,
) {
    var projectMenu by remember { mutableStateOf(false) }
    val selection = state.selection
    val label =
        when (val p = selection.project) {
            ProjectScope.All -> "All projects"
            ProjectScope.Unassigned -> "No project"
            is ProjectScope.Named -> p.name
        }
    Box {
        OutlinedButton(
            onClick = { projectMenu = true },
            modifier = Modifier.fillMaxWidth().testTag("ReportProject"),
            shape = MaterialTheme.shapes.medium,
        ) { Text(label) }
        DropdownMenu(expanded = projectMenu, onDismissRequest = {
            projectMenu = false
        }, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
            val scopes =
                listOf("All projects" to ProjectScope.All, "No project" to ProjectScope.Unassigned) +
                    state.projects.map { it to ProjectScope.Named(it) }
            scopes.forEach { (name, scope) ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    onProject(scope)
                    projectMenu = false
                })
            }
        }
    }
    val annual = selection.page == ReportPage.HISTORY && selection.grouping == ReportGrouping.YEAR
    if (annual) Text("All years", style = MaterialTheme.typography.titleMedium)
    if (selection.page != ReportPage.STATS && !annual) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(
                onClick = { onMovePeriod(-1) },
                modifier = Modifier.size(FattoMetrics.minTouchTarget),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    "Previous period",
                )
            }
            val dateLabel =
                if (selection.page ==
                    ReportPage.HISTORY
                ) {
                    selection.month.format(DateTimeFormatter.ofPattern("MMMM uuuu"))
                } else {
                    val weeks = state.data?.timesheet?.weeks.orEmpty()
                    if (weeks.isEmpty()) {
                        "Four weeks"
                    } else {
                        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                        val start = weeks.first().bucket.startDate.format(formatter)
                        val end = weeks.last().bucket.endDateExclusive.minusDays(1).format(formatter)
                        "$start – $end"
                    }
                }
            Text(dateLabel, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            val canNext =
                if (selection.page ==
                    ReportPage.HISTORY
                ) {
                    selection.month < YearMonth.from(state.time.now.atZone(state.time.zone))
                } else {
                    selection.timesheetWindowOffset <
                        0
                }
            IconButton(onClick = {
                onMovePeriod(1)
            }, enabled = canNext, modifier = Modifier.size(FattoMetrics.minTouchTarget)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next period")
            }
        }
    }
    if (selection.page == ReportPage.HISTORY) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small)) {
            ReportGrouping.entries.forEach { grouping ->
                FilterChip(selected = selection.grouping == grouping, onClick = {
                    onGrouping(grouping)
                }, label = { Text(grouping.name.lowercase().replaceFirstChar { it.uppercase() }) })
            }
        }
    }
}

@Composable
internal fun ReportMetrics(values: List<Pair<String, String>>) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small),
        verticalArrangement = Arrangement.spacedBy(FattoSpacing.small),
    ) {
        values.forEach { (label, value) ->
            Card(
                Modifier.weight(1f).widthIn(min = FattoMetrics.largeTouchTarget * 2),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(FattoSpacing.large)) {
                    Text(value, style = MaterialTheme.typography.headlineSmall)
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

internal fun bucketLabel(bucket: ReportBucket): String {
    if (bucket.startDate.dayOfYear == 1 && bucket.endDateExclusive == bucket.startDate.plusYears(1)) {
        return bucket.startDate.year.toString()
    }
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val start = bucket.startDate.format(formatter)
    val end = bucket.endDateExclusive.minusDays(1)
    return if (end == bucket.startDate) start else "$start – ${end.format(formatter)}"
}
