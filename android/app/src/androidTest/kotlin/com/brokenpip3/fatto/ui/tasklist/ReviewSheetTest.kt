package com.brokenpip3.fatto.ui.tasklist

import android.view.inspector.WindowInspector
import androidx.activity.ComponentDialog
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

    @Test fun backRequiresConfirmationBeforeShowingSummary() {
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
        pressDialogBack()
        compose.onNodeWithText("End this review?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, dismissals) }
        compose.onNodeWithText("End review", substring = false).performClick()
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
                        ReviewUiState(stage = ReviewStage.ACTIVE, currentTask = task, total = 1),
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
        compose.onNodeWithContentDescription("Close review").performClick()
        compose.onNodeWithText("End this review?").assertIsDisplayed()
        compose.onNodeWithText("Keep reviewing").performClick()
        compose.onNode(hasText("Mark reviewed") and hasClickAction()).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, dismissals) }
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
        compose.onNodeWithText("18 reviewed").assertIsDisplayed()
        compose.onNodeWithTag("ReviewStatReviewed").assertExists()
        compose.onNodeWithTag("ReviewStatSkipped").assertExists()
        compose.onNodeWithTag("ReviewStatCompleted").assertExists()
        compose.onNodeWithTag("ReviewStatDeleted").assertExists()
        compose.onNodeWithText("Done").assertIsDisplayed()
    }

    @Test fun draggingTheSheetDownRequiresEndConfirmation() {
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
        compose.onNodeWithText("End this review?").assertIsDisplayed()
        compose.onNodeWithText("Keep reviewing").performClick()
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
        compose.onNodeWithText("Swipe left").assertIsDisplayed()
        compose.onNodeWithText("Swipe right").assertIsDisplayed()
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
        compose.onNodeWithText("UDA key · date").assertIsDisplayed()
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
