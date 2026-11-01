package com.brokenpip3.fatto.ui.reports

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.brokenpip3.fatto.data.TaskTimeSnapshot
import com.brokenpip3.fatto.data.reports.ProjectScope
import com.brokenpip3.fatto.data.reports.ReportGrouping
import com.brokenpip3.fatto.data.reports.calculateHistory
import com.brokenpip3.fatto.data.reports.calculateStats
import com.brokenpip3.fatto.data.reports.calculateTimesheet
import com.brokenpip3.fatto.data.reports.monthRange
import com.brokenpip3.fatto.data.reports.reportBuckets
import com.brokenpip3.fatto.ui.theme.NordicTheme
import com.brokenpip3.fatto.vm.HistoryPresentation
import com.brokenpip3.fatto.vm.ReportPage
import com.brokenpip3.fatto.vm.ReportSelection
import com.brokenpip3.fatto.vm.ReportsData
import com.brokenpip3.fatto.vm.ReportsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

class ReportsScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun homeOpensAllThreeReportsAndDetailBackReturnsHome() {
        var selection by mutableStateOf(ReportSelection())
        rule.setContent {
            NordicTheme {
                ReportsContent(
                    state = ReportsUiState(selection = selection, loading = false),
                    onOpen = { selection = selection.copy(page = it) },
                    onBack = { selection = selection.copy(page = ReportPage.HOME) },
                    onProject = {}, onGrouping = {}, onPresentation = {}, onMovePeriod = {}, onRetry = {},
                )
            }
        }
        for ((name, page) in listOf("History" to ReportPage.HISTORY, "Timesheet" to ReportPage.TIMESHEET, "Stats" to ReportPage.STATS)) {
            rule.onNodeWithTag("ReportCard_$page").performScrollTo().performClick()
            rule.runOnIdle { assertEquals(page, selection.page) }
            rule.onNodeWithContentDescription("Back to reports").performClick()
            rule.onNodeWithTag("ReportCard_$page").assertExists()
        }
    }

    @Test fun initialLoadAndFailureOfferDistinctFeedbackAndRetry() {
        var state by mutableStateOf(ReportsUiState())
        var retries = 0
        rule.setContent {
            NordicTheme {
                ReportsContent(state, {}, {}, {}, {}, {}, {}, { retries++ })
            }
        }
        rule.onNodeWithTag("ReportsLoading").assertExists()
        rule.runOnIdle { state = state.copy(loading = false, error = "Could not load local tasks") }
        rule.onNodeWithText("Could not load local tasks").assertExists()
        rule.onNodeWithText("Retry").performClick()
        rule.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun chartTableProjectGroupingAndPeriodControlsPreserveSelection() {
        val now = Instant.parse("2026-10-23T12:00:00Z")
        val zone = ZoneOffset.UTC
        val buckets = reportBuckets(monthRange(YearMonth.of(2026, 10), zone), ReportGrouping.WEEK, zone, DayOfWeek.MONDAY)
        val history = calculateHistory(emptyList(), ProjectScope.All, buckets)
        val data =
            ReportsData(
                history,
                history,
                calculateTimesheet(
                    emptyList(),
                    ProjectScope.All,
                    buckets,
                    now,
                ),
                calculateStats(
                    emptyList(),
                    ProjectScope.All,
                    now,
                ),
            )
        var state by mutableStateOf(
            ReportsUiState(
                selection = ReportSelection(page = ReportPage.HISTORY, month = YearMonth.of(2026, 10)),
                data = data, loading = false, time = TaskTimeSnapshot(now, zone),
            ),
        )
        rule.setContent {
            NordicTheme {
                ReportsContent(
                    state, {}, {},
                    { state = state.copy(selection = state.selection.copy(project = it)) },
                    { state = state.copy(selection = state.selection.copy(grouping = it)) },
                    { state = state.copy(selection = state.selection.copy(presentation = it)) },
                    { state = state.copy(selection = state.selection.copy(month = state.selection.month.plusMonths(it.toLong()))) }, {},
                )
            }
        }
        rule.onNodeWithTag("HistoryChart").assertExists()
        rule.onNodeWithText("Table").performScrollTo().performClick()
        rule.onNodeWithTag("HistoryTable").assertExists()
        rule.onNodeWithTag("ReportProject").performScrollTo().performClick()
        rule.onNodeWithText("No project").performClick()
        rule.onNodeWithText("Day").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Previous period").performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals(ProjectScope.Unassigned, state.selection.project)
            assertEquals(ReportGrouping.DAY, state.selection.grouping)
            assertEquals(HistoryPresentation.TABLE, state.selection.presentation)
            assertEquals(YearMonth.of(2026, 9), state.selection.month)
        }
        rule.onNodeWithText("Year").performScrollTo().performClick()
        rule.onNodeWithText("All years").assertExists()
        rule.onNodeWithContentDescription("Previous period").assertDoesNotExist()
        rule.onNodeWithContentDescription("Next period").assertDoesNotExist()
    }
}
