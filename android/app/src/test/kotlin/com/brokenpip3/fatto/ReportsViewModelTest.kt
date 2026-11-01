package com.brokenpip3.fatto

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.brokenpip3.fatto.data.reports.ProjectScope
import com.brokenpip3.fatto.data.reports.ReportGrouping
import com.brokenpip3.fatto.data.reports.ReportSourceState
import com.brokenpip3.fatto.data.reports.ReportTaskSource
import com.brokenpip3.fatto.vm.HistoryPresentation
import com.brokenpip3.fatto.vm.ReportPage
import com.brokenpip3.fatto.vm.ReportsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {
    private class Source : ReportTaskSource {
        override val state = MutableStateFlow(ReportSourceState())
        var refreshCount = 0

        override suspend fun refresh() {
            refreshCount++
        }
    }

    @Test fun loadingEmptyFailureAndSavedSelectionRemainDistinct() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            val models = mutableListOf<ReportsViewModel>()
            try {
                val source = Source()
                val saved = SavedStateHandle()
                val vm = ReportsViewModel(source, FixedTaskTimeSource(), MutableStateFlow(2), saved, dispatcher).also { models += it }
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
                runCurrent()
                assertTrue(vm.state.value.loading)
                assertNull(vm.state.value.data)
                source.state.value = ReportSourceState(tasks = emptyList(), revision = 1)
                runCurrent()
                assertFalse(vm.state.value.loading)
                assertEquals(0, vm.state.value.data!!.stats.total)
                vm.open(ReportPage.HISTORY)
                vm.selectProject(ProjectScope.Named("Work"))
                vm.selectPresentation(HistoryPresentation.TABLE)
                vm.movePeriod(-1)
                runCurrent()
                assertEquals(YearMonth.of(2026, 9), vm.state.value.selection.month)
                source.state.value = source.state.value.copy(error = "Read failed")
                runCurrent()
                assertNotNull(vm.state.value.data)
                assertEquals("Read failed", vm.state.value.error)
                val restored = ReportsViewModel(source, FixedTaskTimeSource(), MutableStateFlow(2), saved, dispatcher).also { models += it }
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { restored.state.collect {} }
                runCurrent()
                assertEquals(vm.state.value.selection, restored.state.value.selection)
                vm.retry()
                runCurrent()
                assertEquals(1, source.refreshCount)
            } finally {
                models.forEach { it.viewModelScope.cancel() }
                backgroundScope.coroutineContext.cancelChildren()
                runCurrent()
                Dispatchers.resetMain()
            }
        }

    @Test fun nextPeriodCannotMoveIntoFutureAndBackRetainsFilter() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            val models = mutableListOf<ReportsViewModel>()
            try {
                val vm =
                    ReportsViewModel(Source(), FixedTaskTimeSource(), MutableStateFlow(2), SavedStateHandle(), dispatcher).also {
                        models +=
                            it
                    }
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
                runCurrent()
                vm.open(ReportPage.HISTORY)
                vm.movePeriod(1)
                vm.selectProject(ProjectScope.Unassigned)
                vm.back()
                runCurrent()
                assertEquals(ReportPage.HOME, vm.state.value.selection.page)
                assertEquals(YearMonth.of(2026, 10), vm.state.value.selection.month)
                assertEquals(ProjectScope.Unassigned, vm.state.value.selection.project)
                vm.open(ReportPage.TIMESHEET)
                vm.movePeriod(1)
                runCurrent()
                assertEquals(0, vm.state.value.selection.timesheetWindowOffset)
            } finally {
                models.forEach { it.viewModelScope.cancel() }
                backgroundScope.coroutineContext.cancelChildren()
                runCurrent()
                Dispatchers.resetMain()
            }
        }

    @Test fun latestSourceProjectZoneAndWeekSettingUpdateTheSameReportState() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            val models = mutableListOf<ReportsViewModel>()
            try {
                val source = Source()
                val time = FixedTaskTimeSource(java.time.Instant.parse("2026-10-31T23:30:00Z"))
                val firstDay = MutableStateFlow(2)
                val vm = ReportsViewModel(source, time, firstDay, SavedStateHandle(), dispatcher).also { models += it }
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
                source.state.value = ReportSourceState(tasks = listOf(reportTaskFixture("first")), revision = 1)
                runCurrent()
                vm.selectProject(ProjectScope.Named("Work"))
                source.state.value =
                    ReportSourceState(
                        tasks = listOf(reportTaskFixture("latest"), reportTaskFixture("personal", project = "Personal")), revision = 2,
                    )
                runCurrent()
                assertEquals(1, vm.state.value.data!!.stats.total)
                assertEquals(2, vm.state.value.data!!.home.totals.added)
                time.snapshots.value = time.snapshots.value.copy(zone = java.time.ZoneId.of("Europe/Rome"))
                firstDay.value = 1
                runCurrent()
                assertEquals(0, vm.state.value.data!!.home.totals.added)
                assertEquals(java.time.DayOfWeek.SUNDAY, vm.state.value.data!!.timesheet.weeks.first().bucket.startDate.dayOfWeek)
                assertEquals(ProjectScope.Named("Work"), vm.state.value.selection.project)
            } finally {
                models.forEach { it.viewModelScope.cancel() }
                backgroundScope.coroutineContext.cancelChildren()
                runCurrent()
                Dispatchers.resetMain()
            }
        }

    @Test fun reattachmentRetainsRawSnapshotThroughFailureAndAllowsNewFiltersUntilRetrySucceeds() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            val models = mutableListOf<ReportsViewModel>()
            try {
                val first = Source()
                first.state.value =
                    ReportSourceState(
                        tasks =
                            listOf(
                                reportTaskFixture("work"),
                                reportTaskFixture(
                                    "none",
                                    project = null,
                                ),
                            ),
                        revision = 5,
                    )
                val time = FixedTaskTimeSource()
                val day = MutableStateFlow(2)
                val vm = ReportsViewModel(first, time, day, SavedStateHandle(), dispatcher).also { models += it }
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
                runCurrent()
                assertEquals(2, vm.state.value.data!!.stats.total)
                val replacement = Source()
                vm.attach(replacement, time, day)
                runCurrent()
                assertNotNull(vm.state.value.data)
                replacement.state.value = ReportSourceState(error = "Cannot reopen local replica")
                vm.selectProject(ProjectScope.Unassigned)
                runCurrent()
                assertEquals(1, vm.state.value.data!!.stats.total)
                assertEquals("Cannot reopen local replica", vm.state.value.error)
                replacement.state.value = ReportSourceState(tasks = emptyList(), revision = 1)
                runCurrent()
                assertEquals(0, vm.state.value.data!!.stats.total)
                assertNull(vm.state.value.error)
            } finally {
                models.forEach { it.viewModelScope.cancel() }
                backgroundScope.coroutineContext.cancelChildren()
                runCurrent()
                Dispatchers.resetMain()
            }
        }

    @Test fun annualHistoryIncludesOtherMonthsMissingEntryEndEventsAndZeroYears() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            val source = Source()
            source.state.value =
                ReportSourceState(
                    tasks =
                        listOf(
                            reportTaskFixture("old", entry = "2024-06-01T12:00:00Z"),
                            reportTaskFixture("current", entry = "2026-01-02T12:00:00Z"),
                            reportTaskFixture("other", project = "Personal", entry = "2022-01-02T12:00:00Z"),
                            reportTaskFixture("deleted", entry = "").copy(
                                status = uniffi.taskchampion_android.TaskStatus.Deleted,
                                end = "2025-02-01T12:00:00Z",
                            ),
                        ),
                    revision = 1,
                )
            val vm = ReportsViewModel(source, FixedTaskTimeSource(), MutableStateFlow(2), SavedStateHandle(), dispatcher)
            try {
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
                runCurrent()
                vm.open(ReportPage.HISTORY)
                vm.selectProject(ProjectScope.Named("Work"))
                vm.selectGrouping(ReportGrouping.YEAR)
                runCurrent()
                val report = vm.state.value.data!!.history
                assertEquals(listOf(2024, 2025, 2026), report.rows.map { it.bucket.startDate.year })
                assertEquals(listOf(1, 0, 1), report.rows.map { it.counts.added })
                assertEquals(listOf(0, 1, 0), report.rows.map { it.counts.deleted })
                assertEquals(0, vm.state.value.data!!.home.totals.added)
                vm.selectGrouping(ReportGrouping.WEEK)
                runCurrent()
                assertEquals(0, vm.state.value.data!!.history.totals.added)
            } finally {
                vm.viewModelScope.cancel()
                backgroundScope.coroutineContext.cancelChildren()
                runCurrent()
                Dispatchers.resetMain()
            }
        }
}
