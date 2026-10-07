package com.brokenpip3.fatto.ui.theme

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.brokenpip3.fatto.data.SettingsRepositoryImpl
import com.brokenpip3.fatto.data.TaskRepository
import com.brokenpip3.fatto.ui.assertTextFits
import com.brokenpip3.fatto.ui.calendar.CalendarScreen
import com.brokenpip3.fatto.ui.captureLayoutScreenshot
import com.brokenpip3.fatto.ui.tasklist.AddTaskDialog
import com.brokenpip3.fatto.vm.TaskViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FontSizeRenderingTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun calendarDaysStayOnOneLineWithCombinedLargeFonts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val viewModel = TaskViewModel(TaskRepository(context, SettingsRepositoryImpl(context)))
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                NordicTheme(fontSizePercent = 150) {
                    CalendarScreen(viewModel, {})
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        composeTestRule.onNodeWithText("28", useUnmergedTree = true).performScrollTo()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals("A day number should fit on one line", 1, layouts.single().lineCount)
        assertTextFits(composeTestRule.onNodeWithText("Calendar", useUnmergedTree = true))
        captureLayoutScreenshot("font-calendar-combined")
    }

    @Test
    fun appFontSizeReachesDialogTypography() {
        composeTestRule.setContent {
            NordicTheme(darkTheme = true, fontSizePercent = 150) {
                AddTaskDialog(
                    availableProjects = emptyList(),
                    availableTags = emptyList(),
                    onDismiss = {},
                    onConfirm = { _, _, _, _, _, _, _, _, _, _ -> },
                )
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        composeTestRule.onNodeWithText("New Task")
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(36f, layouts.single().layoutInput.style.fontSize.value, 0.001f)
        captureLayoutScreenshot("font-create-150")
    }

    @Test
    fun layoutScaleCombinesAppAndSystemPreferences() {
        var effective = 0f
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                NordicTheme(fontSizePercent = 150) {
                    effective = effectiveFontScale()
                }
            }
        }
        composeTestRule.runOnIdle { assertEquals(3f, effective, 0.001f) }
    }
}
