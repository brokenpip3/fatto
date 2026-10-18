package com.brokenpip3.fatto.ui.tasklist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brokenpip3.fatto.data.model.RecurrenceRule
import com.brokenpip3.fatto.data.model.TaskEditResult
import com.brokenpip3.fatto.ui.theme.NordicTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecurrenceCreationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun creationControlsFitOneRowAt256DpInBothThemes() {
        val dark = mutableStateOf(false)
        compose.setContent {
            NordicTheme(darkTheme = dark.value) {
                Box(Modifier.width(256.dp)) {
                    TaskDateAndPriorityControls(
                        priority = null, due = null, scheduled = null, wait = null,
                        onPriorityChange = {}, onDateClick = {},
                        trailingControl = { RecurrenceFields(null, null, {}, null) },
                    )
                }
            }
        }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark.value = theme }
            val repeatTop = compose.onNodeWithText("Repeat", useUnmergedTree = true).getUnclippedBoundsInRoot().top
            for (label in listOf("Priority", "Due", "Sch", "Wait")) {
                assertEquals(compose.onNodeWithText(label, useUnmergedTree = true).getUnclippedBoundsInRoot().top, repeatTop)
            }
            val repeatBounds = compose.onNodeWithTag("RepeatControl").getUnclippedBoundsInRoot()
            assertEquals(48.dp, repeatBounds.right - repeatBounds.left)
        }
    }

    @Test fun repeatDefaultsToNoneAndRequiresDueBeforeApplyingDaily() {
        compose.setContent {
            NordicTheme {
                AddTaskDialog(emptyList(), emptyList(), initialDescription = "New series", onDismiss = {}, onConfirm = { _, _, _ ->
                })
            }
        }
        compose.onNodeWithTag("RepeatControl").performScrollTo().performClick()
        compose.onNodeWithText("Repeat: None").performClick()
        compose.onNodeWithText("Daily", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Set a due date to repeat this task").assertExists()
        compose.onNodeWithText("Apply").performScrollTo().assertIsNotEnabled()
    }

    @Test fun createFailureKeepsEnteredDescription() {
        compose.setContent {
            NordicTheme {
                AddTaskDialog(
                    emptyList(), emptyList(), initialDescription = "Draft", onDismiss = {}, onConfirm = { _, _, _ ->
                    },
                    onCreate = { _, _, _, _ -> TaskEditResult.Failure("Storage unavailable") },
                )
            }
        }
        compose.onNodeWithText("Create").performClick()
        compose.onNodeWithText("Storage unavailable").assertExists()
        compose.onNodeWithText("Draft").assertExists()
    }

    @Test fun customIntervalAndClearUntilChangeOnlyTheRequestedRule() {
        val rule = mutableStateOf<RecurrenceRule?>(RecurrenceRule("daily", "2030-01-01T00:00:00Z"))
        compose.setContent { NordicTheme { RecurrenceFields(rule.value, "2030-01-01T00:00:00Z", { rule.value = it }, null) } }
        compose.onNodeWithTag("RepeatControl").performClick()
        compose.onNodeWithText("Repeat: Daily").performClick()
        compose.onNodeWithText("Custom", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("RecurrenceIntervalInput").performTextReplacement("3")
        compose.onNodeWithText("Days").performClick()
        compose.onNodeWithText("Months", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Clear Until").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(RecurrenceRule("daily", "2030-01-01T00:00:00Z"), rule.value) }
        compose.onNodeWithText("Apply").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(RecurrenceRule("3months", null), rule.value) }
    }

    @Test fun untilBeforeDueIsExplainedAndExplicitClearingRestoresValidRule() {
        val rule = mutableStateOf<RecurrenceRule?>(RecurrenceRule("daily", "2030-01-01T00:00:00Z"))
        val due = "2030-02-01T00:00:00Z"
        compose.setContent {
            NordicTheme { RecurrenceFields(rule.value, due, { rule.value = it }, rule.value?.creationError(due)) }
        }
        compose.onNodeWithTag("RepeatControl").performClick()
        compose.onNodeWithText("Until cannot be earlier than Due").assertExists()
        compose.onNodeWithText("Clear Until").performClick()
        compose.onNodeWithText("Until cannot be earlier than Due").assertDoesNotExist()
        compose.onNodeWithText("Apply").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(null, rule.value?.creationError(due)) }
    }

    @Test fun creationRepeatControlsRemainReachableAtCombinedFontScaleInBothThemes() {
        val dark = mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                NordicTheme(darkTheme = dark.value, fontSizePercent = 150) {
                    AddTaskDialog(
                        emptyList(), emptyList(), initialDescription = "A long description that wraps across lines",
                        onDismiss = {}, onConfirm = { _, _, _ -> },
                    )
                }
            }
        }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark.value = theme }
            compose.onNodeWithTag("RepeatControl").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Create").assertIsDisplayed()
        }
    }
}
