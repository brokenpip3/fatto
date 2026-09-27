package com.brokenpip3.fatto.ui.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brokenpip3.fatto.data.SettingsRepositoryImpl
import com.brokenpip3.fatto.data.SyncCredentials
import com.brokenpip3.fatto.data.SyncDiagnosticEvent
import com.brokenpip3.fatto.data.SyncDiagnosticsRepositoryImpl
import com.brokenpip3.fatto.vm.SettingsViewModel
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncSettingsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var settingsRepository: SettingsRepositoryImpl
    private lateinit var diagnosticsRepository: SyncDiagnosticsRepositoryImpl

    @Before
    fun setUp() {
        settingsRepository = SettingsRepositoryImpl(context)
        diagnosticsRepository = SyncDiagnosticsRepositoryImpl(context)
        settingsRepository.clearCredentials()
        diagnosticsRepository.clear()
    }

    @After
    fun tearDown() {
        settingsRepository.clearCredentials()
        diagnosticsRepository.clear()
    }

    @Test
    fun invalidClientIdShowsInlineErrorWithoutStartingSync() {
        var syncCalls = 0
        val viewModel =
            SettingsViewModel(
                repository = settingsRepository,
                syncAction = { syncCalls++ },
                diagnosticsRepository = diagnosticsRepository,
            )
        setServerForm(viewModel, clientId = "not-a-uuid")
        composeTestRule.setContent { SettingsScreen(viewModel, emptyList(), emptySet()) }

        composeTestRule.onNodeWithTag("SaveAndTestButton").performClick()

        composeTestRule.onNodeWithText("Enter a valid UUID").assertIsDisplayed()
        assertEquals(0, syncCalls)
    }

    @Test
    fun invalidUrlShowsFieldErrorWithoutStartingSync() {
        var syncCalls = 0
        val viewModel =
            SettingsViewModel(
                repository = settingsRepository,
                syncAction = { syncCalls++ },
                diagnosticsRepository = diagnosticsRepository,
            )
        setServerForm(viewModel)
        viewModel.onUrlChange("ftp://sync.example.com")
        composeTestRule.setContent { SettingsScreen(viewModel, emptyList(), emptySet()) }

        composeTestRule.onNodeWithTag("SaveAndTestButton").performClick()

        composeTestRule.onNodeWithText("Enter an absolute HTTP or HTTPS URL with a host").assertIsDisplayed()
        assertEquals(0, syncCalls)
    }

    @Test
    fun s3FormUsesSaveAndTestAndClear() {
        val viewModel =
            SettingsViewModel(
                repository = settingsRepository,
                diagnosticsRepository = diagnosticsRepository,
            )
        viewModel.onSyncTypeChange(com.brokenpip3.fatto.data.SyncType.S3)
        composeTestRule.setContent { SettingsScreen(viewModel, emptyList(), emptySet()) }

        composeTestRule.onNodeWithText("Bucket").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save & Test").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Save", substring = false).assertDoesNotExist()
        composeTestRule.onNodeWithText("Clear").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("SaveAndTestButton").assertExists()
    }

    @Test
    fun serverFormRequiresSaveAndTestInsteadOfSaveOnly() {
        val viewModel =
            SettingsViewModel(
                repository = settingsRepository,
                diagnosticsRepository = diagnosticsRepository,
            )
        composeTestRule.setContent { SettingsScreen(viewModel, emptyList(), emptySet()) }

        composeTestRule.onNodeWithText("Save & Test").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Save", substring = false).assertDoesNotExist()
        composeTestRule.onNodeWithText("Clear", substring = false).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun diagnosticsEntryPointAppearsBelowServerActionButtons() {
        val viewModel =
            SettingsViewModel(
                repository = settingsRepository,
                diagnosticsRepository = diagnosticsRepository,
            )
        composeTestRule.setContent { SettingsScreen(viewModel, emptyList(), emptySet()) }

        composeTestRule.onNodeWithTag("SaveAndTestButton").performScrollTo()
        composeTestRule.onNodeWithText("Clear", substring = false).performScrollTo()
        composeTestRule.onNodeWithTag("ViewDiagnosticsButton").performScrollTo()

        val saveAndTestTop = composeTestRule.onNodeWithTag("SaveAndTestButton").fetchSemanticsNode().boundsInRoot.top
        val clearTop = composeTestRule.onNodeWithText("Clear", substring = false).fetchSemanticsNode().boundsInRoot.top
        val diagnosticsTop = composeTestRule.onNodeWithTag("ViewDiagnosticsButton").fetchSemanticsNode().boundsInRoot.top
        assertTrue(diagnosticsTop > saveAndTestTop)
        assertTrue(diagnosticsTop > clearTop)
    }

    @Test
    fun saveAndTestShowsProgressThenSuccessAndDisablesDuplicateAction() {
        val syncStarted = CompletableDeferred<Unit>()
        val finishSync = CompletableDeferred<Unit>()
        val viewModel =
            SettingsViewModel(
                repository = settingsRepository,
                syncAction = {
                    syncStarted.complete(Unit)
                    finishSync.await()
                },
                diagnosticsRepository = diagnosticsRepository,
            )
        setServerForm(viewModel)
        composeTestRule.setContent { SettingsScreen(viewModel, emptyList(), emptySet()) }

        composeTestRule.onNodeWithTag("SaveAndTestButton").performClick()
        composeTestRule.waitUntil(5_000) { syncStarted.isCompleted }

        composeTestRule.onNodeWithTag("SaveAndTestButton").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Testing").assertIsDisplayed()
        finishSync.complete(Unit)
        composeTestRule.waitUntil(5_000) { viewModel.syncTestState.value is com.brokenpip3.fatto.vm.SyncTestState.Succeeded }

        composeTestRule.onNodeWithTag("SyncConnectionStatusText").assertTextEquals("Sync successful")
    }

    @Test
    fun failedSyncShowsFailureStateAndKeepsDiagnosticsAvailable() {
        val secret = "diagnostic-secret"
        val viewModel =
            SettingsViewModel(
                repository = settingsRepository,
                syncAction = { throw IllegalStateException("connection failed with $secret") },
                diagnosticsRepository = diagnosticsRepository,
            )
        setServerForm(viewModel, secret = secret)
        composeTestRule.setContent { SettingsScreen(viewModel, emptyList(), emptySet()) }

        composeTestRule.onNodeWithTag("SaveAndTestButton").performClick()
        composeTestRule.waitUntil(5_000) { viewModel.syncTestState.value is com.brokenpip3.fatto.vm.SyncTestState.Failed }

        composeTestRule.onNodeWithTag("SyncConnectionStatusText").assertTextEquals("Sync failed")
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("Settings saved, but sync failed").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithTag("ViewDiagnosticsButton").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("DiagnosticsView").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("connection failed with [redacted]", substring = true).assertCountEquals(2)
    }

    @Test
    fun diagnosticsViewShowsEventsAndClearRemovesOnlyDiagnostics() {
        val savedCredentials = SyncCredentials("https://sync.example.com", "768d9f09-accd-406d-8685-7b977b83d5c6", "persisted-secret")
        assertTrue(settingsRepository.saveCredentials(savedCredentials.url, savedCredentials.clientId, savedCredentials.secret))
        diagnosticsRepository.append(
            SyncDiagnosticEvent(
                timestampEpochMillis = 1234L,
                stage = "sync_test",
                outcome = "failed",
                summary = "Could not resolve server host",
                serverOrigin = "https://sync.example.com",
                elapsedMillis = 25L,
                appVersion = "0.12.0",
            ),
        )
        val viewModel =
            SettingsViewModel(
                repository = settingsRepository,
                diagnosticsRepository = diagnosticsRepository,
            )
        composeTestRule.setContent { SettingsScreen(viewModel, emptyList(), emptySet()) }

        composeTestRule.onNodeWithTag("ViewDiagnosticsButton").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("DiagnosticEvent").assertIsDisplayed()
        composeTestRule.onNodeWithText("Copy").performClick()
        composeTestRule.onNodeWithText("Diagnostics copied").assertIsDisplayed()
        composeTestRule.mainClock.advanceTimeBy(5_000)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("ViewDiagnosticsButton").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("ClearDiagnosticsButton").performClick()

        composeTestRule.onNodeWithText("No diagnostics yet").assertIsDisplayed()
        assertEquals(savedCredentials, settingsRepository.getCredentials())
    }

    private fun setServerForm(
        viewModel: SettingsViewModel,
        clientId: String = "768d9f09-accd-406d-8685-7b977b83d5c6",
        secret: String = "encryption-secret",
    ) {
        viewModel.onUrlChange("https://sync.example.com")
        viewModel.onClientIdChange(clientId)
        viewModel.onSecretChange(secret)
    }
}
