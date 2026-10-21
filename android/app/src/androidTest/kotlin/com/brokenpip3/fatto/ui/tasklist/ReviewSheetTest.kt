package com.brokenpip3.fatto.ui.tasklist

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.view.inspector.WindowInspector
import androidx.activity.ComponentDialog
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brokenpip3.fatto.data.model.Annotation
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.review.ReviewAction
import com.brokenpip3.fatto.data.review.ReviewSettings
import com.brokenpip3.fatto.ui.theme.NordicTheme
import com.brokenpip3.fatto.vm.ReviewStage
import com.brokenpip3.fatto.vm.ReviewUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.taskchampion_android.TaskStatus

@RunWith(AndroidJUnit4::class)
class ReviewSheetTest {
    @get:Rule val compose = createComposeRule()
    private val task =
        Task(
            "test", "A task to review", TaskStatus.Pending, emptyList(), null, null, null,
            null, null, null, null, 0f, false, false, emptyList(), emptyMap(),
        )

    private fun pressDialogBack() {
        compose.runOnIdle {
            val dialog =
                WindowInspector.getGlobalWindowViews()
                    .firstNotNullOfOrNull { it.findViewTreeLifecycleOwner() as? ComponentDialog }
            checkNotNull(dialog) { "Review dialog is not attached" }.onBackPressedDispatcher.onBackPressed()
        }
    }

    @Test fun swipesDispatchTheSameActionsAsButtons() {
        var action: ReviewAction? = null
        compose.setContent {
            NordicTheme {
                ReviewSheet(
                    ReviewUiState(stage = ReviewStage.ACTIVE, currentTask = task, total = 1),
                    onAction = { action = it }, onAcceptNotice = {}, onStart = {}, onEdit = {}, onSettings = {}, onDismiss = {},
                )
            }
        }
        compose.onNodeWithTag("ReviewSwipeCard").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(ReviewAction.REVIEWED, action)
        compose.onNodeWithTag("ReviewSwipeCard").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(ReviewAction.SKIP, action)
    }

    @Test fun largeTextKeepsReviewButtonsVisible() {
        compose.setContent {
            NordicTheme(darkTheme = true, fontSizePercent = 150) {
                ReviewSheet(
                    ReviewUiState(stage = ReviewStage.ACTIVE, currentTask = task, total = 1),
                    onAction = {}, onAcceptNotice = {}, onStart = {}, onEdit = {}, onSettings = {}, onDismiss = {},
                )
            }
        }
        compose.onNode(hasText("Mark reviewed") and hasClickAction()).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Delete").performScrollTo().assertIsDisplayed()
    }

    @Test fun explicitEndRequiresConfirmationBeforeShowingSummary() {
        var state by mutableStateOf(ReviewUiState(stage = ReviewStage.ACTIVE, currentTask = task, total = 1))
        var dismissals = 0
        compose.setContent {
            NordicTheme {
                ReviewSheet(
                    state, onAction = {}, onAcceptNotice = {}, onStart = {}, onEdit = {}, onSettings = {},
                    onDismiss = {
                        dismissals++
                        state = state.copy(stage = if (dismissals == 1) ReviewStage.SUMMARY else ReviewStage.CLOSED, currentTask = null)
                    },
                )
            }
        }
        compose.onNode(hasText("Mark reviewed") and hasClickAction()).assertIsDisplayed()
        compose.onNodeWithText("End review").performClick()
        compose.onNodeWithText("End this review?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, dismissals) }
        compose.onAllNodesWithText("End review", substring = false).onLast().performClick()
        compose.onNodeWithText("Session summary").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, dismissals) }
    }

    @Test fun busyReviewCannotBeHiddenByBack() {
        compose.setContent {
            NordicTheme {
                ReviewSheet(
                    ReviewUiState(stage = ReviewStage.ACTIVE, currentTask = task, total = 1, busy = true),
                    onAction = {}, onAcceptNotice = {}, onStart = {}, onEdit = {}, onSettings = {}, onDismiss = {},
                )
            }
        }
        compose.onNode(hasText("Mark reviewed") and hasClickAction()).assertIsDisplayed()
        pressDialogBack()
        compose.onNode(hasText("Mark reviewed") and hasClickAction()).assertIsDisplayed()
    }

    @Test fun shortWindowAndCombinedFontScalingKeepTaskAndActionsReachable() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density * 2, 1.5f)) {
                NordicTheme(darkTheme = true, fontSizePercent = 150) {
                    ReviewSheet(
                        ReviewUiState(
                            stage = ReviewStage.ACTIVE,
                            currentTask =
                                task.copy(
                                    project = "a.long.project.name.that.wraps",
                                    tags = listOf("a-long-tag-that-needs-to-wrap", "documents", "waiting"),
                                ),
                            total = 1,
                        ),
                        onAction = {}, onAcceptNotice = {}, onStart = {}, onEdit = {}, onSettings = {}, onDismiss = {},
                    )
                }
            }
        }
        compose.onNodeWithText("A task to review").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Delete").performScrollTo().assertIsDisplayed()
    }

    @Test fun primaryAndSkipButtonsDispatchReviewActions() {
        var action: ReviewAction? = null
        compose.setContent {
            NordicTheme {
                ReviewSheet(
                    ReviewUiState(stage = ReviewStage.ACTIVE, currentTask = task, total = 1),
                    onAction = { action = it }, onAcceptNotice = {}, onStart = {}, onEdit = {}, onSettings = {}, onDismiss = {},
                )
            }
        }
        compose.onNode(hasText("Mark reviewed") and hasClickAction()).performClick()
        assertEquals(ReviewAction.REVIEWED, action)
        compose.onNode(hasText("Skip") and hasClickAction()).performClick()
        assertEquals(ReviewAction.SKIP, action)
    }

    @Test fun deleteRequiresConfirmationAndCancelDoesNotDispatch() {
        var action: ReviewAction? = null
        compose.setContent {
            NordicTheme {
                ReviewSheet(
                    ReviewUiState(stage = ReviewStage.ACTIVE, currentTask = task, total = 1),
                    onAction = { action = it }, onAcceptNotice = {}, onStart = {}, onEdit = {}, onSettings = {}, onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("Delete").performClick()
        assertEquals(null, action)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(null, action)
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete task").performClick()
        assertEquals(ReviewAction.DELETE, action)
    }

    @Test fun closeConfirmationCanBeCancelledWithoutEndingTheSession() {
        var dismissals = 0
        compose.setContent {
            NordicTheme {
                ReviewSheet(
                    ReviewUiState(stage = ReviewStage.ACTIVE, currentTask = task, total = 132),
                    onAction = {}, onAcceptNotice = {}, onStart = {}, onEdit = {}, onSettings = {},
                    onDismiss = { dismissals++ },
                )
            }
        }
        compose.onNodeWithText("End review").performClick()
        compose.onNodeWithText("End this review?").assertIsDisplayed()
        compose.onNodeWithText("Keep reviewing").performClick()
        compose.onNode(hasText("Mark reviewed") and hasClickAction()).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, dismissals) }
    }

    private fun captureWebLinks(block: (() -> String?) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var opened: String? = null
        val monitor =
            object : Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                    if (intent.action != Intent.ACTION_VIEW) return null
                    opened = intent.dataString
                    return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
                }
            }
        instrumentation.addMonitor(monitor)
        try {
            block { opened }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test fun reviewDescriptionAndAnnotationLinksOpenTheirDestinations() =
        captureWebLinks { opened ->
            compose.setContent {
                NordicTheme {
                    ReviewSheet(
                        ReviewUiState(
                            stage = ReviewStage.ACTIVE, total = 1,
                            currentTask =
                                task.copy(
                                    description = "https://example.com/task",
                                    annotations = listOf(Annotation("20261019T120000Z", "https://example.com/note")),
                                ),
                        ),
                        onAction = {}, onAcceptNotice = {}, onStart = {}, onEdit = {}, onSettings = {}, onDismiss = {},
                    )
                }
            }
            compose.onNodeWithText("https://example.com/task").performScrollTo().performFirstLinkClick()
            compose.runOnIdle { assertEquals("https://example.com/task", opened()) }
            compose.onNodeWithText("https://example.com/note").performScrollTo().performFirstLinkClick()
            compose.runOnIdle { assertEquals("https://example.com/note", opened()) }
        }

    @Test fun detailAnnotationLinksOpenTheirDestination() =
        captureWebLinks { opened ->
            compose.setContent {
                NordicTheme {
                    TaskDetailBottomSheet(
                        task = task.copy(annotations = listOf(Annotation("20261019T120000Z", "https://example.com/note"))),
                        onDismiss = {}, onSave = { error("No edits expected") },
                        availableProjects = emptyList(), availableTags = emptyList(),
                    )
                }
            }
            compose.onNodeWithText("https://example.com/note").performScrollTo().performFirstLinkClick()
            compose.runOnIdle { assertEquals("https://example.com/note", opened()) }
        }

    @Test fun projectAppearsBelowTheTaskDescription() {
        compose.setContent {
            NordicTheme { ReviewTaskCard(task.copy(project = "fatto.release")) }
        }
        val description = compose.onNodeWithText("A task to review").getUnclippedBoundsInRoot()
        val project = compose.onNodeWithText("fatto.release").getUnclippedBoundsInRoot()
        assertTrue(project.top > description.bottom)
    }

    @Test fun summaryShowsSeparateOutcomeCards() {
        compose.setContent {
            NordicTheme {
                ReviewSheet(
                    ReviewUiState(stage = ReviewStage.SUMMARY, total = 132, reviewed = 14, skipped = 4, completed = 3, deleted = 1),
                    onAction = {}, onAcceptNotice = {}, onStart = {}, onEdit = {}, onSettings = {}, onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("Your review progress").assertDoesNotExist()
        compose.onNodeWithText("Reviewed includes", substring = true).assertDoesNotExist()
        compose.onNodeWithText("tasks not reached this session", substring = true).assertDoesNotExist()
        compose.onNodeWithText("18 reviewed").assertIsDisplayed()
        compose.onNodeWithTag("ReviewStatReviewed").assertExists()
        compose.onNodeWithTag("ReviewStatSkipped").assertExists()
        compose.onNodeWithTag("ReviewStatCompleted").assertExists()
        compose.onNodeWithTag("ReviewStatDeleted").assertExists()
        compose.onNodeWithText("Done").assertIsDisplayed()
    }

    @Test fun draggingAndBackKeepTheReviewActive() {
        var dismissals = 0
        compose.setContent {
            NordicTheme {
                ReviewSheet(
                    ReviewUiState(stage = ReviewStage.ACTIVE, currentTask = task, total = 1),
                    onAction = {}, onAcceptNotice = {}, onStart = {}, onEdit = {}, onSettings = {}, onDismiss = { dismissals++ },
                )
            }
        }
        compose.onNodeWithTag("ReviewSheet").performTouchInput { swipeDown(startY = height * .03f, endY = height * .85f) }
        compose.onNodeWithTag("ReviewSheet").performTouchInput { click(Offset(centerX, -10f)) }
        pressDialogBack()
        compose.onNodeWithText("End this review?").assertDoesNotExist()
        compose.onNode(hasText("Mark reviewed") and hasClickAction()).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, dismissals) }
    }

    @Test fun physicalSwipeDirectionsRespectCustomAssignmentsInRtl() {
        var action: ReviewAction? = null
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                NordicTheme {
                    ReviewSheet(
                        ReviewUiState(
                            stage = ReviewStage.ACTIVE, currentTask = task, total = 1,
                            settings = ReviewSettings(leftAction = ReviewAction.REVIEWED, rightAction = ReviewAction.SKIP),
                        ),
                        onAction = { action = it }, onAcceptNotice = {}, onStart = {}, onEdit = {}, onSettings = {}, onDismiss = {},
                    )
                }
            }
        }
        compose.onNodeWithText("Swipe left").assertDoesNotExist()
        compose.onNodeWithText("Swipe right").assertDoesNotExist()
        compose.onNodeWithTag("ReviewSwipeCard").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(ReviewAction.SKIP, action) }
        compose.onNodeWithTag("ReviewSwipeCard").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(ReviewAction.REVIEWED, action) }
    }

    @Test fun noticeUsesContentHeightOnATallScreen() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density * .75f, 1f)) {
                NordicTheme {
                    ReviewSheet(
                        ReviewUiState(stage = ReviewStage.NOTICE),
                        onAction = {}, onAcceptNotice = {}, onStart = {}, onEdit = {}, onSettings = {}, onDismiss = {},
                    )
                }
            }
        }
        compose.onNodeWithText("reviewed").assertIsDisplayed()
        compose.onNodeWithText("Review tracking").assertIsDisplayed()
        compose.onNodeWithText("Syncs with your other taskwarrior clients and is compatible with taskshell review.").assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("ReviewSheet").getUnclippedBoundsInRoot().let { it.bottom - it.top } < 650.dp)
        compose.onNodeWithText("Continue").assertIsDisplayed()
    }

    @Test fun largeTextSummaryStacksOutcomeCards() {
        compose.setContent {
            NordicTheme(darkTheme = true, fontSizePercent = 150) {
                ReviewSheet(
                    ReviewUiState(stage = ReviewStage.SUMMARY, total = 180, reviewed = 132, skipped = 40, completed = 3, deleted = 1),
                    onAction = {}, onAcceptNotice = {}, onStart = {}, onEdit = {}, onSettings = {}, onDismiss = {},
                )
            }
        }
        val reviewed = compose.onNodeWithTag("ReviewStatReviewed").getUnclippedBoundsInRoot()
        val skipped = compose.onNodeWithTag("ReviewStatSkipped").getUnclippedBoundsInRoot()
        assertEquals(reviewed.left, skipped.left)
        for (name in listOf("Reviewed", "Skipped", "Completed", "Deleted")) {
            compose.onNodeWithTag("ReviewStat$name").performScrollTo().assertIsDisplayed()
        }
    }
}
