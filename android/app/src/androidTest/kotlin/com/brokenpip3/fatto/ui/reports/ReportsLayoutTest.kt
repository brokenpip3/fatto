package com.brokenpip3.fatto.ui.reports

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.brokenpip3.fatto.data.TaskTimeSnapshot
import com.brokenpip3.fatto.data.reports.ProjectScope
import com.brokenpip3.fatto.data.reports.ReportGrouping
import com.brokenpip3.fatto.data.reports.ReportStatus
import com.brokenpip3.fatto.data.reports.ReportTask
import com.brokenpip3.fatto.data.reports.calculateHistory
import com.brokenpip3.fatto.data.reports.calculateStats
import com.brokenpip3.fatto.data.reports.calculateTimesheet
import com.brokenpip3.fatto.data.reports.monthRange
import com.brokenpip3.fatto.data.reports.reportBuckets
import com.brokenpip3.fatto.ui.assertTextFits
import com.brokenpip3.fatto.ui.captureLayoutScreenshot
import com.brokenpip3.fatto.ui.theme.NordicTheme
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

class ReportsLayoutTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun narrowReportsRemainReadableAtCombinedFontScalingInBothThemes() {
        val presentation = mutableStateOf(com.brokenpip3.fatto.vm.HistoryPresentation.CHART)
        val page = mutableStateOf(ReportPage.HOME)
        val periodOffset = mutableStateOf(0)
        val dark = mutableStateOf(false)
        val large = mutableStateOf(true)
        val now = Instant.parse("2026-10-23T12:00:00Z")
        val zone = ZoneOffset.UTC
        val buckets = reportBuckets(monthRange(YearMonth.of(2026, 10), zone), ReportGrouping.WEEK, zone, DayOfWeek.MONDAY)
        val tasks =
            listOf(
                ReportTask(
                    "id",
                    "A long task description that must wrap instead of pushing content outside the screen",
                    ReportStatus.COMPLETED,
                    "Work.An exceptionally long project name for accessibility",
                    emptySet(),
                    0,
                    now,
                    null,
                    now,
                    null,
                ),
            )
        val history = calculateHistory(tasks, ProjectScope.All, buckets)
        val data =
            ReportsData(
                history,
                history,
                calculateTimesheet(
                    tasks,
                    ProjectScope.All,
                    buckets,
                    now,
                ),
                calculateStats(
                    tasks,
                    ProjectScope.All,
                    now,
                ),
            )
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (large.value) 2f else 1f)) {
                NordicTheme(darkTheme = dark.value, fontSizePercent = if (large.value) 150 else 100) {
                    Surface(
                        modifier = Modifier.width(320.dp),
                        color = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ) {
                        ReportsContent(
                            ReportsUiState(
                                ReportSelection(
                                    page = page.value,
                                    project = ProjectScope.Named(tasks.single().project!!),
                                    presentation = presentation.value,
                                    timesheetWindowOffset = periodOffset.value,
                                ),
                                if (periodOffset.value == 0) data else data.copy(timesheet = data.timesheet.copy(weeks = emptyList())),
                                loading = false, time = TaskTimeSnapshot(now, zone),
                            ),
                            { page.value = it }, { page.value = ReportPage.HOME }, {}, {},
                            { presentation.value = it }, { periodOffset.value += it }, {},
                        )
                    }
                }
            }
        }
        for ((theme, scaled) in listOf(false to false, true to false, false to true, true to true)) {
            rule.runOnIdle {
                dark.value = theme
                large.value = scaled
            }
            val appearance = "${if (theme) "dark" else "light"}-${if (scaled) "large" else "normal"}"
            for (destination in ReportPage.entries) {
                rule.runOnIdle {
                    page.value = destination
                    presentation.value = com.brokenpip3.fatto.vm.HistoryPresentation.CHART
                }
                rule.onNodeWithTag("ReportsTitle").performScrollTo().assertIsDisplayed()
                assertTextFits(rule.onNodeWithTag("ReportsTitle"))
                if (destination != ReportPage.HOME) {
                    rule.onNodeWithTag("ReportProject").performScrollTo().assertIsDisplayed()
                    rule.onNodeWithContentDescription("Back to reports").performScrollTo()
                        .assertWidthIsAtLeast(48.dp)
                        .assertHeightIsAtLeast(48.dp)
                }
                if (destination != ReportPage.HOME) {
                    val project =
                        rule.onNode(
                            hasText(tasks.single().project!!) and hasAnyAncestor(hasTestTag("ReportProject")),
                            useUnmergedTree = true,
                        ).performScrollTo()
                    assertTextFits(project)
                }
                if (destination == ReportPage.HISTORY || destination == ReportPage.TIMESHEET) {
                    for (label in listOf("Previous period", "Next period")) {
                        rule.onNodeWithContentDescription(label).performScrollTo()
                            .assertWidthIsAtLeast(48.dp)
                            .assertHeightIsAtLeast(48.dp)
                    }
                }
                if (destination == ReportPage.TIMESHEET) {
                    if (!scaled && !theme) {
                        rule.onNodeWithContentDescription("Collapse Completed").performScrollTo().performClick()
                        rule.onNodeWithTag("TimesheetTask_id").assertDoesNotExist()
                        rule.onNodeWithContentDescription("Previous period").performScrollTo().performClick()
                        rule.onNodeWithContentDescription("Next period").performScrollTo().performClick()
                        rule.onNodeWithTag("TimesheetTask_id").assertDoesNotExist()
                        rule.onNodeWithContentDescription("Expand Completed").performScrollTo().performClick()
                        rule.onNodeWithTag("TimesheetTask_id").assertExists()
                    }
                    val description = rule.onNodeWithText(tasks.single().description, useUnmergedTree = true).performScrollTo()
                    description.assertIsDisplayed()
                    assertTextFits(description)
                }
                if (destination == ReportPage.STATS && !scaled) {
                    rule.onNodeWithText("Projects").performScrollTo()
                    val tops =
                        listOf("Projects", "Tags", "Annotations").map {
                            rule.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top
                        }
                    tops.forEach { assertEquals(tops.first(), it, 1f) }
                    rule.onNodeWithText("With projects").performScrollTo().assertIsDisplayed()
                }
                if (destination == ReportPage.HISTORY) {
                    rule.onNodeWithTag("HistoryChart").performScrollTo().assertIsDisplayed()
                    captureLayoutScreenshot("reports-history-chart-$appearance")
                    rule.onNodeWithText("Table").performScrollTo().performClick()
                    rule.onNodeWithTag("HistoryTable").assertExists()
                    assertTextFits(rule.onNodeWithText("Deleted", useUnmergedTree = true).performScrollTo())
                }
                captureLayoutScreenshot("reports-${destination.name.lowercase()}-$appearance")
            }
        }
    }
}
