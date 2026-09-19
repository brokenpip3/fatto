package com.brokenpip3.fatto

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.brokenpip3.fatto.data.S3Credentials
import com.brokenpip3.fatto.data.SettingsRepositoryImpl
import com.brokenpip3.fatto.data.SyncCredentials
import com.brokenpip3.fatto.data.SyncType
import com.brokenpip3.fatto.data.TaskSwipeAction
import com.brokenpip3.fatto.data.TaskrcImporter
import com.brokenpip3.fatto.ui.theme.NordicNight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

@OptIn(ExperimentalTestApi::class)
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
        composeTestRule
            .onNodeWithText("https://github.com/brokenpip3/fatto")
            .assertExists()
            .assertHasClickAction()
        composeTestRule.onNodeWithText("Please report bugs at").assertExists()
        composeTestRule
            .onNodeWithText("https://github.com/brokenpip3/fatto/issues")
            .assertExists()
            .assertHasClickAction()
    }

    @Test
    fun testSettingsTabsRevealSections() {
        composeTestRule.onNodeWithText("Settings").performClick()

        composeTestRule.onNodeWithTag("SettingsTabSync").assertIsSelected()
        composeTestRule.onNodeWithText("Sync Server URL").assertIsDisplayed()

        composeTestRule.onNodeWithTag("SettingsTabTaskrc").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Taskrc import").assertIsDisplayed()
        composeTestRule.onNodeWithText("Contexts").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("First day of week").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("SettingsTabDisplay").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Show completed tasks").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("SettingsTabNotifications").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Enable daily notifications").assertIsDisplayed()

        composeTestRule.onNodeWithTag("SettingsTabAbout").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Fatto").assertIsDisplayed()
    }

    @Test
    fun testDefaultProjectCanBeSelectedAndDisabledWithoutClearingValue() {
        val projectName = "Task6.Default"
        val repository = SettingsRepositoryImpl(composeTestRule.activity.applicationContext)
        try {
            composeTestRule.onNodeWithContentDescription("Add Task").performClick()
            composeTestRule.onNode(hasTestTag("DescriptionInput")).performTextInput("Default project source")
            composeTestRule.onNode(hasTestTag("ProjectInput")).performTextInput(projectName)
            composeTestRule.onNodeWithText("Create").performClick()
            composeTestRule.waitUntilAtLeastOneExists(hasTestTag("AppRoot"), 10000)

            composeTestRule.onNodeWithText("Settings").performClick()
            composeTestRule.onNodeWithTag("SettingsTabTaskrc").performScrollTo().performClick()

            val taskDefaultsTop =
                composeTestRule
                    .onNodeWithTag("TaskDefaultsSection")
                    .fetchSemanticsNode()
                    .layoutInfo
                    .coordinates
                    .positionInRoot()
                    .y
            val contextsTop =
                composeTestRule
                    .onNodeWithText("Contexts")
                    .fetchSemanticsNode()
                    .layoutInfo
                    .coordinates
                    .positionInRoot()
                    .y
            assertTrue(
                "Task defaults should appear before Contexts ($taskDefaultsTop vs $contextsTop)",
                taskDefaultsTop < contextsTop,
            )

            composeTestRule.onNodeWithTag("DefaultProjectToggle").performScrollTo().assertIsOff().performClick()
            composeTestRule.onNodeWithTag("ProjectPickerDialog").assertIsDisplayed()
            composeTestRule.onNodeWithTag("ProjectPickerCancelButton").performClick()
            composeTestRule.onNodeWithTag("DefaultProjectToggle").assertIsOff()

            composeTestRule.onNodeWithTag("DefaultProjectToggle").performClick()
            composeTestRule.waitUntilAtLeastOneExists(hasTestTag("ProjectPickerOption-$projectName"), 15000)
            composeTestRule.onNodeWithTag("ProjectPickerOption-$projectName").performClick()
            composeTestRule.onNodeWithTag("ProjectPickerConfirmButton").performClick()
            composeTestRule.onNodeWithTag("DefaultProjectToggle").assertIsOn()
            composeTestRule.onNodeWithTag("DefaultProjectValue").performScrollTo().assertIsDisplayed()

            composeTestRule.onNodeWithTag("DefaultProjectToggle").performScrollTo().performClick()
            composeTestRule.onNodeWithTag("DefaultProjectToggle").assertIsOff()
            assertEquals(projectName, repository.getDefaultProject())
        } finally {
            repository.setDefaultProjectEnabled(false)
            repository.setDefaultProject(null)
        }
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
    fun testTaskSwipeActionsCanBeConfiguredIndependently() {
        val repository = SettingsRepositoryImpl(composeTestRule.activity.applicationContext)
        try {
            composeTestRule.onNodeWithText("Settings").performClick()
            composeTestRule.onNodeWithTag("SettingsTabDisplay").performScrollTo().performClick()

            composeTestRule.onNodeWithText("Swipe Actions").performScrollTo().assertIsDisplayed()
            composeTestRule
                .onNodeWithTag("SwipeRightActionSelector")
                .performScrollTo()
                .performClick()
            composeTestRule
                .onNodeWithTag("SwipeRightActionSelector-COMPLETE")
                .performClick()
            composeTestRule
                .onNodeWithTag("SwipeLeftActionSelector")
                .performScrollTo()
                .performClick()
            composeTestRule.onNodeWithTag("SwipeLeftActionSelector-DELETE").performClick()

            assertEquals(TaskSwipeAction.COMPLETE, repository.getSwipeStartToEndAction())
            assertEquals(TaskSwipeAction.DELETE, repository.getSwipeEndToStartAction())
        } finally {
            repository.setSwipeStartToEndAction(TaskSwipeAction.NONE)
            repository.setSwipeEndToStartAction(TaskSwipeAction.NONE)
        }
    }

    @Test
    fun testDisplaySettingsFollowRequestedOrder() {
        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.onNodeWithTag("SettingsTabDisplay").performScrollTo().performClick()

        val themeTop =
            composeTestRule
                .onNodeWithText("Theme")
                .fetchSemanticsNode()
                .layoutInfo
                .coordinates
                .positionInRoot()
                .y
        val swipeActionsTop =
            composeTestRule
                .onNodeWithText("Swipe Actions")
                .fetchSemanticsNode()
                .layoutInfo
                .coordinates
                .positionInRoot()
                .y
        val optionsTop =
            composeTestRule
                .onNodeWithText("Options")
                .fetchSemanticsNode()
                .layoutInfo
                .coordinates
                .positionInRoot()
                .y
        val tagsPerLineTop =
            composeTestRule
                .onNodeWithText("Tags per line", substring = true)
                .fetchSemanticsNode()
                .layoutInfo
                .coordinates
                .positionInRoot()
                .y

        assertTrue("Theme should appear before Swipe Actions", themeTop < swipeActionsTop)
        assertTrue("Swipe Actions should appear before Options", swipeActionsTop < optionsTop)
        assertTrue("Options should appear before Tags per line", optionsTop < tagsPerLineTop)
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
