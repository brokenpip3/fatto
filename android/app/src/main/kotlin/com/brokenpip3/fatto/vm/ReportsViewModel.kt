package com.brokenpip3.fatto.vm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brokenpip3.fatto.data.TaskTimeSnapshot
import com.brokenpip3.fatto.data.TaskTimeSource
import com.brokenpip3.fatto.data.reports.HistoryReport
import com.brokenpip3.fatto.data.reports.ProjectScope
import com.brokenpip3.fatto.data.reports.ReportGrouping
import com.brokenpip3.fatto.data.reports.ReportSourceState
import com.brokenpip3.fatto.data.reports.ReportTask
import com.brokenpip3.fatto.data.reports.ReportTaskSource
import com.brokenpip3.fatto.data.reports.StatsReport
import com.brokenpip3.fatto.data.reports.TimesheetReport
import com.brokenpip3.fatto.data.reports.annualHistoryRange
import com.brokenpip3.fatto.data.reports.calculateHistory
import com.brokenpip3.fatto.data.reports.calculateStats
import com.brokenpip3.fatto.data.reports.calculateTimesheet
import com.brokenpip3.fatto.data.reports.mapReportTasks
import com.brokenpip3.fatto.data.reports.monthRange
import com.brokenpip3.fatto.data.reports.reportBuckets
import com.brokenpip3.fatto.data.reports.timesheetRange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.Calendar

enum class ReportPage { HOME, HISTORY, TIMESHEET, STATS }

enum class HistoryPresentation { CHART, TABLE }

data class ReportSelection(
    val page: ReportPage = ReportPage.HOME,
    val project: ProjectScope = ProjectScope.All,
    val month: YearMonth = YearMonth.now(),
    val grouping: ReportGrouping = ReportGrouping.WEEK,
    val presentation: HistoryPresentation = HistoryPresentation.CHART,
    val timesheetWindowOffset: Int = 0,
)

data class ReportsData(val home: HistoryReport, val history: HistoryReport, val timesheet: TimesheetReport, val stats: StatsReport)

data class ReportsUiState(
    val selection: ReportSelection = ReportSelection(),
    val data: ReportsData? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val syncError: String? = null,
    val projects: List<String> = emptyList(),
    val time: TaskTimeSnapshot = TaskTimeSnapshot(Instant.now(), ZoneId.systemDefault()),
)

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(
    source: ReportTaskSource,
    timeSource: TaskTimeSource,
    firstDayOfWeek: StateFlow<Int>,
    private val savedStateHandle: SavedStateHandle,
    private val calculationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private data class Dependencies(val source: ReportTaskSource, val time: TaskTimeSource, val firstDay: StateFlow<Int>)

    private val dependencies = MutableStateFlow(Dependencies(source, timeSource, firstDayOfWeek))
    private val selection = MutableStateFlow(restoreSelection())
    private var initializedMonth = savedStateHandle.contains("reports.month")
    private var lastSuccessfulSnapshot: ReportSourceState? = null

    val state: StateFlow<ReportsUiState> =
        dependencies.flatMapLatest { deps ->
            var revision = Long.MIN_VALUE
            var records = emptyList<ReportTask>()
            combine(deps.source.state, deps.time.snapshots, deps.firstDay, selection) { snapshot, time, firstDay, selected ->
                ReportInputs(snapshot, time, firstDay, selected)
            }.mapLatest { input ->
                val incoming = input.snapshot
                if (incoming.tasks != null) lastSuccessfulSnapshot = incoming
                val snapshot =
                    if (incoming.tasks == null && lastSuccessfulSnapshot != null) {
                        incoming.copy(tasks = lastSuccessfulSnapshot!!.tasks, revision = lastSuccessfulSnapshot!!.revision)
                    } else {
                        incoming
                    }
                val time = input.time
                val firstDay = input.firstDay
                val selected = input.selection
                if (!initializedMonth) {
                    initializedMonth = true
                    update(selected.copy(month = YearMonth.from(time.now.atZone(time.zone))))
                }
                if (snapshot.tasks == null) {
                    ReportsUiState(
                        selected,
                        loading = snapshot.error == null,
                        refreshing = snapshot.refreshing,
                        error = snapshot.error,
                        syncError = snapshot.syncError,
                        time = time,
                    )
                } else {
                    withContext(calculationDispatcher) {
                        if (revision != snapshot.revision) {
                            records = mapReportTasks(snapshot.tasks)
                            revision = snapshot.revision
                        }
                        val day = if (firstDay == Calendar.SUNDAY) DayOfWeek.SUNDAY else DayOfWeek.of((firstDay - 1).coerceIn(1, 7))
                        val today = time.now.atZone(time.zone).toLocalDate()
                        val historyRange =
                            if (selected.grouping == ReportGrouping.YEAR) {
                                annualHistoryRange(records, selected.project, today, time.zone)
                            } else {
                                monthRange(selected.month, time.zone)
                            }
                        val historyBuckets = reportBuckets(historyRange, selected.grouping, time.zone, day)
                        val homeBuckets = reportBuckets(monthRange(YearMonth.from(today), time.zone), ReportGrouping.MONTH, time.zone, day)
                        val weeks =
                            reportBuckets(
                                timesheetRange(
                                    today,
                                    day,
                                    selected.timesheetWindowOffset,
                                    time.zone,
                                ),
                                ReportGrouping.WEEK,
                                time.zone,
                                day,
                            )
                        ReportsUiState(
                            selected,
                            ReportsData(
                                calculateHistory(
                                    records,
                                    ProjectScope.All,
                                    homeBuckets,
                                ),
                                calculateHistory(
                                    records,
                                    selected.project,
                                    historyBuckets,
                                ),
                                calculateTimesheet(
                                    records,
                                    selected.project,
                                    weeks,
                                    time.now,
                                ),
                                calculateStats(
                                    records,
                                    selected.project,
                                    time.now,
                                ),
                            ),
                            loading = false, refreshing = snapshot.refreshing, error = snapshot.error, syncError = snapshot.syncError,
                            projects =
                                records.mapNotNull { it.project }.flatMap { name ->
                                    name.split('.').indices.map { name.split('.').take(it + 1).joinToString(".") }
                                }.distinct().sorted(),
                            time = time,
                        )
                    }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState(selection.value))

    private data class ReportInputs(
        val snapshot: ReportSourceState,
        val time: TaskTimeSnapshot,
        val firstDay: Int,
        val selection: ReportSelection,
    )

    suspend fun attach(
        source: ReportTaskSource,
        timeSource: TaskTimeSource,
        firstDayOfWeek: StateFlow<Int>,
    ) {
        dependencies.value = Dependencies(source, timeSource, firstDayOfWeek)
    }

    fun open(page: ReportPage) = update(selection.value.copy(page = page))

    fun back() = open(ReportPage.HOME)

    fun selectProject(scope: ProjectScope) = update(selection.value.copy(project = scope))

    fun selectGrouping(grouping: ReportGrouping) = update(selection.value.copy(grouping = grouping))

    fun selectPresentation(mode: HistoryPresentation) = update(selection.value.copy(presentation = mode))

    fun movePeriod(delta: Int) {
        val current = selection.value
        val changed =
            when (current.page) {
                ReportPage.HISTORY ->
                    if (current.grouping == ReportGrouping.YEAR) {
                        current
                    } else {
                        current.copy(
                            month =
                                minOf(
                                    current.month.plusMonths(delta.toLong()),
                                    YearMonth.from(state.value.time.now.atZone(state.value.time.zone)),
                                ),
                        )
                    }
                ReportPage.TIMESHEET -> current.copy(timesheetWindowOffset = (current.timesheetWindowOffset + delta).coerceAtMost(0))
                else -> current
            }
        update(changed)
    }

    fun retry() {
        viewModelScope.launch {
            try {
                dependencies.value.source.refresh()
            } catch (
                e: CancellationException,
            ) {
                throw e
            } catch (_: Exception) {
                // Source publishes safe load errors.
            }
        }
    }

    fun refreshTime() = dependencies.value.time.refresh()

    private fun update(value: ReportSelection) {
        selection.value = value
        savedStateHandle["reports.page"] = value.page.name
        savedStateHandle["reports.month"] = value.month.toString()
        savedStateHandle["reports.grouping"] = value.grouping.name
        savedStateHandle["reports.presentation"] = value.presentation.name
        savedStateHandle["reports.window"] = value.timesheetWindowOffset
        savedStateHandle["reports.project"] =
            when (val p = value.project) {
                ProjectScope.All -> "all"
                ProjectScope.Unassigned -> "none"
                is ProjectScope.Named -> "name:${p.name}"
            }
    }

    private fun restoreSelection(): ReportSelection {
        val project = savedStateHandle.get<String>("reports.project")
        return ReportSelection(
            page =
                savedStateHandle.get<String>("reports.page")?.let { runCatching { ReportPage.valueOf(it) }.getOrNull() } ?: ReportPage.HOME,
            project =
                when {
                    project == "none" -> ProjectScope.Unassigned
                    project?.startsWith("name:") == true -> ProjectScope.Named(project.removePrefix("name:"))
                    else -> ProjectScope.All
                },
            month =
                savedStateHandle.get<String>("reports.month")?.let { runCatching { YearMonth.parse(it) }.getOrNull() } ?: YearMonth.now(),
            grouping =
                savedStateHandle.get<String>("reports.grouping")?.let { runCatching { ReportGrouping.valueOf(it) }.getOrNull() }
                    ?: ReportGrouping.WEEK,
            presentation =
                savedStateHandle.get<String>("reports.presentation")?.let { runCatching { HistoryPresentation.valueOf(it) }.getOrNull() }
                    ?: HistoryPresentation.CHART,
            timesheetWindowOffset = (savedStateHandle.get<Int>("reports.window") ?: 0).coerceAtMost(0),
        )
    }
}
