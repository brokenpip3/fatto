package com.brokenpip3.fatto.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brokenpip3.fatto.data.S3Credentials
import com.brokenpip3.fatto.data.SettingsRepositoryImpl
import com.brokenpip3.fatto.data.SyncCredentials
import com.brokenpip3.fatto.data.SyncType
import com.brokenpip3.fatto.vm.SettingsViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun replacingSyncSettingsPersistsOnlyCredentialsFromTheBackup() {
        val serverCredentials = SyncCredentials("https://sync.example.com", "client-id", "server-secret")
        val s3Credentials = S3Credentials("bucket", null, null, "access-key", "access-secret", "s3-secret")

        assertTrue(settingsRepository.replaceSyncSettings(SyncType.S3, serverCredentials, s3Credentials))
        val saved = SettingsRepositoryImpl(context)
        assertEquals(serverCredentials, saved.getCredentials())
        assertEquals(s3Credentials, saved.getS3Credentials())
        assertTrue(saved.replaceSyncSettings(SyncType.SERVER, null, null))

        val cleared = SettingsRepositoryImpl(context)
        assertEquals(null, cleared.getCredentials())
        assertEquals(null, cleared.getS3Credentials())
        assertEquals(SyncType.SERVER, cleared.getSyncType())
    }

    @Test
    fun backupTabShowsPlaintextWarning() {
        composeTestRule.setContent { SettingsScreen(SettingsViewModel(settingsRepository), emptyList(), emptySet()) }

        composeTestRule.onNodeWithText("Backup").performScrollTo().performClick()
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

        composeTestRule.onNodeWithText("Backup").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ExportSettingsButton").performClick()

        composeTestRule.onNodeWithText("Export unencrypted settings?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Export anyway").assertIsDisplayed()
    }

    @Test
    fun importButtonShowsOverwriteWarningCopy() {
        composeTestRule.setContent { SettingsScreen(SettingsViewModel(settingsRepository), emptyList(), emptySet()) }

        composeTestRule.onNodeWithText("Backup").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Import settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("overwrite", substring = true).assertIsDisplayed()
    }
}
