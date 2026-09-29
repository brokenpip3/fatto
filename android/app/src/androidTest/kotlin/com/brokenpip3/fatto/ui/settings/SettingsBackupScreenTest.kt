package com.brokenpip3.fatto.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brokenpip3.fatto.data.SettingsRepositoryImpl
import com.brokenpip3.fatto.vm.SettingsViewModel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsBackupScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var settingsRepository: SettingsRepositoryImpl

    @Before
    fun setUp() {
        settingsRepository = SettingsRepositoryImpl(context)
        settingsRepository.clearCredentials()
    }

    @After
    fun tearDown() {
        settingsRepository.clearCredentials()
    }

    @Test
    fun backupTabShowsPlaintextWarning() {
        composeTestRule.setContent { SettingsScreen(SettingsViewModel(settingsRepository), emptyList(), emptySet()) }

        composeTestRule.onNodeWithText("Backup").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Settings backup").assertIsDisplayed()
        composeTestRule.onNodeWithText("unencrypted", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("plain text", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("TSS", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("S3", substring = true).assertIsDisplayed()
    }

    @Test
    fun exportButtonShowsUnencryptedConfirmationBeforePicker() {
        composeTestRule.setContent { SettingsScreen(SettingsViewModel(settingsRepository), emptyList(), emptySet()) }

        composeTestRule.onNodeWithText("Backup").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ExportSettingsButton").performClick()

        composeTestRule.onNodeWithText("Export unencrypted settings?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Export anyway").assertIsDisplayed()
    }

    @Test
    fun importButtonShowsOverwriteWarningCopy() {
        composeTestRule.setContent { SettingsScreen(SettingsViewModel(settingsRepository), emptyList(), emptySet()) }

        composeTestRule.onNodeWithText("Backup").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Import settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("overwrite", substring = true).assertIsDisplayed()
    }
}
