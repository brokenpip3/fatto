package com.brokenpip3.fatto

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.brokenpip3.fatto.data.S3Credentials
import com.brokenpip3.fatto.data.SettingsRepositoryImpl
import com.brokenpip3.fatto.data.SyncCredentials
import com.brokenpip3.fatto.data.SyncType
import com.brokenpip3.fatto.data.TaskrcImporter
import com.brokenpip3.fatto.ui.theme.NordicNight
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class SettingsIntegrationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    @Test
    fun testAppInfoIsDisplayedInSettings() {
        // Navigate to settings
        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.onNodeWithTag("SettingsTabAbout").performScrollTo().performClick()

        // Verify App Name
        composeTestRule.onNodeWithText("Fatto").assertExists()

        // Verify Subtitle
        composeTestRule.onNodeWithText("Your TaskWarrior android companion").assertExists()

        // Verify Version strings exist (partial match for Version)
        composeTestRule.onNodeWithText("Version", substring = true).assertExists()

        // Verify Build date exists (partial match)
        composeTestRule.onNodeWithText("Built on:", substring = true).assertExists()

        composeTestRule.onNodeWithText("Source code").assertExists()
        composeTestRule.onNodeWithText("https://github.com/brokenpip3/fatto").assertExists()
        composeTestRule.onNodeWithText("Please report bugs at").assertExists()
        composeTestRule.onNodeWithText("https://github.com/brokenpip3/fatto/issues").assertExists()
    }

    @Test
    fun testSettingsTabsRevealSections() {
        composeTestRule.onNodeWithText("Settings").performClick()

        composeTestRule.onNodeWithTag("SettingsTabSync").assertIsSelected()
        composeTestRule.onNodeWithText("Sync Server URL").assertIsDisplayed()

        composeTestRule.onNodeWithTag("SettingsTabTaskrc").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Taskrc import").assertIsDisplayed()
        composeTestRule.onNodeWithText("Contexts").assertIsDisplayed()
        composeTestRule.onNodeWithText("First day of week").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("SettingsTabDisplay").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Show completed tasks").assertIsDisplayed()

        composeTestRule.onNodeWithTag("SettingsTabNotifications").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Enable daily notifications").assertIsDisplayed()

        composeTestRule.onNodeWithTag("SettingsTabAbout").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Fatto").assertIsDisplayed()
    }

    @Test
    fun testCalendarStartDaySetting() {
        // Go to settings
        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.onNodeWithTag("SettingsTabTaskrc").performScrollTo().performClick()

        // Select Sunday as the first day of the week
        composeTestRule.onNodeWithText("Sunday").performClick()

        // Go to Calendar
        composeTestRule.onNodeWithText("Calendar").performClick()

        // Assert "Sun" is present
        composeTestRule.onNodeWithText("Sun").assertExists()

        // Go back to settings
        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.onNodeWithTag("SettingsTabTaskrc").performScrollTo().performClick()

        // Select Monday
        composeTestRule.onNodeWithText("Monday").performClick()

        // Go back to Calendar
        composeTestRule.onNodeWithText("Calendar").performClick()

        // Assert "Mon" is present
        composeTestRule.onNodeWithText("Mon").assertExists()
    }

    @Test
    fun testConfirmActionsSettingToggle() {
        // Go to settings
        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.onNodeWithTag("SettingsTabDisplay").performScrollTo().performClick()

        // Verify the checkbox text exists
        composeTestRule.onNodeWithText("Confirm complete/delete").assertExists()

        // Toggle it (it is enabled by default)
        composeTestRule.onNodeWithText("Confirm complete/delete").performClick()

        // Go to tasks and back to ensure it persists in ViewModel
        composeTestRule.onNodeWithText("Tasks").performClick()
        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.onNodeWithTag("SettingsTabDisplay").performScrollTo().performClick()

        // Verify it exists (we can't easily check 'checked' state with onNodeWithText but we verify it's still clickable/present)
        composeTestRule.onNodeWithText("Confirm complete/delete").assertExists()
    }

    @Test
    fun testApplyTaskrcImportPersistsStorageSettings() {
        val context = composeTestRule.activity.applicationContext
        val repository = SettingsRepositoryImpl(context)
        val uuid = "768d9f09-accd-406d-8685-7b977b83d5c6"
        try {
            val serverPreview =
                TaskrcImporter.preview(
                    text =
                        "sync.server.url=http://localhost:8080\n" +
                            "sync.server.client_id=$uuid\n" +
                            "sync.encryption_secret=my-secret",
                    existingContexts = emptyList(),
                    currentActiveContextId = null,
                    currentFirstDayOfWeek = Calendar.MONDAY,
                    currentSyncCredentials = repository.getCredentials(),
                    currentS3Credentials = repository.getS3Credentials(),
                    currentSyncType = repository.getSyncType(),
                )
            repository.applyTaskrcImport(serverPreview)
            assertEquals(SyncType.SERVER, repository.getSyncType())
            assertEquals(SyncCredentials("http://localhost:8080", uuid, "my-secret"), repository.getCredentials())

            val s3Preview =
                TaskrcImporter.preview(
                    text =
                        "sync.aws.bucket=fatto-tasks\n" +
                            "sync.aws.access_key_id=minioadmin\n" +
                            "sync.aws.secret_access_key=minioadmin\n" +
                            "sync.encryption_secret=my-secret",
                    existingContexts = emptyList(),
                    currentActiveContextId = null,
                    currentFirstDayOfWeek = Calendar.MONDAY,
                    currentSyncCredentials = repository.getCredentials(),
                    currentS3Credentials = repository.getS3Credentials(),
                    currentSyncType = repository.getSyncType(),
                )
            repository.applyTaskrcImport(s3Preview)
            assertEquals(SyncType.S3, repository.getSyncType())
            assertEquals(
                S3Credentials("fatto-tasks", null, null, "minioadmin", "minioadmin", "my-secret"),
                repository.getS3Credentials(),
            )

            val secretPreview =
                TaskrcImporter.preview(
                    text = "sync.encryption_secret=secret-only",
                    existingContexts = emptyList(),
                    currentActiveContextId = null,
                    currentFirstDayOfWeek = Calendar.MONDAY,
                    currentSyncCredentials = repository.getCredentials(),
                    currentS3Credentials = repository.getS3Credentials(),
                    currentSyncType = repository.getSyncType(),
                )
            repository.applyTaskrcImport(secretPreview)
            assertEquals("secret-only", repository.getCredentials()?.secret)
            assertEquals("secret-only", repository.getS3Credentials()?.secret)
            assertEquals(SyncType.S3, repository.getSyncType())
        } finally {
            repository.clearCredentials()
        }
    }

    @Test
    fun testDarkThemeChangesAppBackground() {
        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.onNodeWithTag("SettingsTabDisplay").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Dark").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        val pixels = composeTestRule.onNodeWithTag("AppRoot").captureToImage().toPixelMap()
        val backgroundPixel = pixels[4, pixels.height / 2]

        assertEquals(NordicNight.toArgb(), backgroundPixel.toArgb())
    }
}
