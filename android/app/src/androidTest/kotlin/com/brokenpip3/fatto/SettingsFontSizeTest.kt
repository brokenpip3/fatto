package com.brokenpip3.fatto

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import com.brokenpip3.fatto.data.SettingsRepositoryImpl
import com.brokenpip3.fatto.ui.captureLayoutScreenshot
import com.brokenpip3.fatto.ui.settings.SettingsScreen
import com.brokenpip3.fatto.ui.theme.NordicTheme
import com.brokenpip3.fatto.vm.SettingsViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SettingsFontSizeTest {
    @get:Rule
    val composeTestRule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        clearPreferences()
    }

    @After
    fun cleanup() {
        clearPreferences()
    }

    private fun clearPreferences() {
        context.getSharedPreferences("sync_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun fontSizePersistsAcrossRepositoryInstancesAndStaysInRange() {
        val repository = SettingsRepositoryImpl(context)
        assertEquals(100, repository.getFontSizePercent())
        repository.setFontSizePercent(130)
        assertEquals(130, repository.fontSizePercent.value)
        assertEquals(130, SettingsRepositoryImpl(context).getFontSizePercent())
        repository.setFontSizePercent(0)
        assertEquals(80, repository.fontSizePercent.value)
        repository.setFontSizePercent(1000)
        assertEquals(150, repository.fontSizePercent.value)
    }

    @Test
    fun displaySliderPersistsFontSizeAndResetRestoresDefault() {
        val viewModel = SettingsViewModel(SettingsRepositoryImpl(context))
        composeTestRule.setContent {
            val percent by viewModel.fontSizePercent.collectAsState()
            NordicTheme(fontSizePercent = percent) {
                SettingsScreen(viewModel, emptyList(), emptySet())
            }
        }
        composeTestRule.onNodeWithTag("SettingsTabDisplay").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("FontSizeSlider").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(150f) }
        composeTestRule.onNodeWithText("Font size: 150%").assertExists()
        assertEquals(150, SettingsRepositoryImpl(context).getFontSizePercent())
        val layouts = mutableListOf<TextLayoutResult>()
        composeTestRule.onNodeWithText("Font size: 150%")
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(24f, layouts.single().layoutInput.style.fontSize.value, 0.001f)
        captureLayoutScreenshot("font-settings-150")
        composeTestRule.onNodeWithTag("ResetFontSizeButton").performClick()
        composeTestRule.onNodeWithText("Font size: 100%").assertExists()
        assertEquals(100, SettingsRepositoryImpl(context).getFontSizePercent())
    }
}
