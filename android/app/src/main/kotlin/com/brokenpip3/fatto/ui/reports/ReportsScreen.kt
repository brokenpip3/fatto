package com.brokenpip3.fatto.ui.reports

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brokenpip3.fatto.data.reports.ProjectScope
import com.brokenpip3.fatto.data.reports.ReportGrouping
import com.brokenpip3.fatto.ui.theme.FattoMetrics
import com.brokenpip3.fatto.ui.theme.FattoSpacing
import com.brokenpip3.fatto.vm.HistoryPresentation
import com.brokenpip3.fatto.vm.ReportPage
import com.brokenpip3.fatto.vm.ReportsUiState
import com.brokenpip3.fatto.vm.ReportsViewModel
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun ReportsScreen(viewModel: ReportsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(state.selection.page != ReportPage.HOME) { viewModel.back() }
    ReportsContent(
        state,
        viewModel::open,
        viewModel::back,
        viewModel::selectProject,
        viewModel::selectGrouping,
        viewModel::selectPresentation,
        viewModel::movePeriod,
        viewModel::retry,
    )
}

@Composable
fun ReportsContent(
    state: ReportsUiState,
    onOpen: (ReportPage) -> Unit,
    onBack: () -> Unit,
    onProject: (ProjectScope) -> Unit,
    onGrouping: (ReportGrouping) -> Unit,
    onPresentation: (HistoryPresentation) -> Unit,
    onMovePeriod: (Int) -> Unit,
    onRetry: () -> Unit,
) {
    val timesheetExpansionStates = rememberSaveableStateHolder()
    val page = state.selection.page
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(FattoSpacing.large),
        verticalArrangement = Arrangement.spacedBy(FattoSpacing.large),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (page != ReportPage.HOME) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(FattoMetrics.minTouchTarget),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        "Back to reports",
                    )
                }
            }
            Text(
                if (page ==
                    ReportPage.HOME
                ) {
                    "Reports"
                } else {
                    page.name.lowercase().replaceFirstChar { it.uppercase() }
                },
                Modifier.testTag("ReportsTitle"), style = MaterialTheme.typography.headlineSmall,
            )
        }
        if (state.loading) CircularProgressIndicator(Modifier.testTag("ReportsLoading"))
        if (state.refreshing && !state.loading) Text("Refreshing local reports…", style = MaterialTheme.typography.bodyMedium)
        state.error?.let { error ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(FattoSpacing.large)) {
                    Text(if (state.data != null) "$error. Showing the last available data." else error)
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
            }
        }
        state.syncError?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (page == ReportPage.HOME) {
            state.data?.home?.let { home ->
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraLarge) {
                    Column(
                        Modifier.fillMaxWidth().padding(FattoSpacing.large),
                        verticalArrangement = Arrangement.spacedBy(FattoSpacing.small),
                    ) {
                        Text(
                            YearMonth.from(state.time.now.atZone(state.time.zone)).format(DateTimeFormatter.ofPattern("MMMM uuuu")),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text("${home.totals.completed}", style = MaterialTheme.typography.displaySmall)
                        Text("Completed", style = MaterialTheme.typography.bodyMedium)
                        androidx.compose.foundation.layout.FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(FattoSpacing.small),
                        ) {
                            listOf("Added" to home.totals.added, "Deleted" to home.totals.deleted, "Net growth" to home.totals.net)
                                .forEach { (label, value) ->
                                    Column(Modifier.padding(end = FattoSpacing.medium)) {
                                        Text("$value", style = MaterialTheme.typography.titleMedium)
                                        Text(label, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                        }
                    }
                }
            }
            val cards =
                listOf(
                    ReportPage.HISTORY to Icons.Default.BarChart,
                    ReportPage.TIMESHEET to Icons.Default.CheckCircle,
                    ReportPage.STATS to Icons.Default.QueryStats,
                )
            cards.forEach { (target, icon) ->
                Card(
                    onClick = {
                        onOpen(target)
                    },
                    modifier = Modifier.fillMaxWidth().testTag("ReportCard_$target"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(
                        Modifier.padding(FattoSpacing.large),
                        horizontalArrangement = Arrangement.spacedBy(FattoSpacing.large),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val tileColor =
                            if (target == ReportPage.TIMESHEET) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            }
                        Surface(color = tileColor, shape = MaterialTheme.shapes.medium) {
                            Box(Modifier.size(FattoMetrics.minTouchTarget), contentAlignment = Alignment.Center) {
                                Icon(icon, contentDescription = null)
                            }
                        }
                        Text(
                            target.name.lowercase().replaceFirstChar { it.uppercase() },
                            Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                        )
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
        } else {
            ReportControls(state, onProject, onGrouping, onMovePeriod)
            state.data?.let { data ->
                when (page) {
                    ReportPage.HISTORY -> HistoryReportContent(data.history, state.selection.presentation, onPresentation)
                    ReportPage.TIMESHEET -> TimesheetReportContent(data.timesheet, state.time.zone, timesheetExpansionStates)
                    ReportPage.STATS -> StatsReportContent(data.stats, state.time.zone)
                }
            }
        }
    }
}
