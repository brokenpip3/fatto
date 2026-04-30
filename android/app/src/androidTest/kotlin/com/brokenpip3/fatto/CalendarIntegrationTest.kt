package com.brokenpip3.fatto

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class CalendarIntegrationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    @Before
    fun clearDatabase() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val dbDir = File(context.filesDir, "taskchampion")
        if (dbDir.exists()) {
            dbDir.deleteRecursively()
        }
    }

    @Test
    fun testDueDateRoundTrip() {
        val description = "Due date test ${System.currentTimeMillis()}"
        val todayStr = LocalDate.now().toString()

        // Create a task
        composeTestRule.onNodeWithContentDescription("Add Task").performClick()
        composeTestRule.onNodeWithText("Description").performTextInput(description)
        composeTestRule.onNodeWithText("Create").performClick()
        composeTestRule.waitUntilDoesNotExist(hasText("New Task"), 10000)
        composeTestRule.waitUntilAtLeastOneExists(
            hasText(description) and hasAnyAncestor(hasTestTag("TaskList")),
            15000,
        )

        // Open the detail bottom sheet
        composeTestRule.onNode(
            hasText(description) and hasAnyAncestor(hasTestTag("TaskList")),
        ).performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("TaskDetailBottomSheet"), 15000)

        // Click the Due date icon to open the DatePicker
        composeTestRule.onNode(
            hasContentDescription("Due"),
            useUnmergedTree = true,
        ).performScrollTo().performClick()

        // Tap today's day cell in the DatePicker (day buttons are labeled as "Today, ..." or "...")
        composeTestRule.waitUntilAtLeastOneExists(
            hasText("Today", substring = true),
            5000,
        )
        composeTestRule.onNodeWithText("Today", substring = true).performClick()

        // Click OK to confirm the date selection
        composeTestRule.onNodeWithText("OK").performClick()
        composeTestRule.waitForIdle()

        // Verify the due date now shows today's date in the bottom sheet
        composeTestRule.waitUntilAtLeastOneExists(
            hasText(todayStr) and hasAnyAncestor(hasTestTag("TaskDetailBottomSheet")),
            5000,
        )

        // Save and close
        composeTestRule.onNode(
            hasContentDescription("CloseButton"),
            useUnmergedTree = true,
        ).performScrollTo().performClick()
        composeTestRule.waitUntilDoesNotExist(hasTestTag("TaskDetailBottomSheet"), 15000)
        composeTestRule.waitForIdle()

        // Navigate to Calendar and verify task appears
        composeTestRule.onNodeWithText("Calendar").performClick()
        composeTestRule.waitForIdle()

        // Tap today's day number in the calendar grid to open the bottom sheet
        val todayDayNumber = LocalDate.now().dayOfMonth.toString()
        composeTestRule.waitUntilAtLeastOneExists(hasText(todayDayNumber), 5000)
        composeTestRule.onNodeWithText(todayDayNumber).performClick()
        composeTestRule.waitForIdle()

        // Verify the calendar's bottom sheet shows the task
        composeTestRule.waitUntilAtLeastOneExists(
            hasText(description),
            5000,
        )
    }
}
