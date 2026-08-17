package com.brokenpip3.fatto

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DependencyIntegrationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    @Before
    fun clearDatabase() {
        val context =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val dbDir = File(context.filesDir, "taskchampion")
        if (dbDir.exists()) {
            dbDir.deleteRecursively()
        }
    }

    private fun createTask(description: String) {
        composeTestRule.onNodeWithContentDescription("Add Task").performClick()
        composeTestRule.onNodeWithText("Description").performTextInput(description)
        composeTestRule.onNodeWithText("Create").performClick()
        composeTestRule.waitUntilDoesNotExist(hasText("New Task"), 10000)
        composeTestRule.waitForIdle()
    }

    private fun pickerRow(text: String) =
        composeTestRule.onNode(
            hasAnyAncestor(hasTestTag("TaskPickerDialog")) and
                hasAnyDescendant(hasText(text)) and
                hasClickAction(),
            useUnmergedTree = true,
        )

    private fun deleteTask(description: String) {
        // The TaskItem Card (merged) carries the task text; the DeleteTask
        // button is a child of that card.
        composeTestRule.onNode(
            hasContentDescription("DeleteTask") and hasAnyAncestor(hasText(description)),
        ).performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasText("Confirm"), 15000)
        composeTestRule.onNodeWithText("Confirm").performClick()
        composeTestRule.waitUntilDoesNotExist(hasText(description), 15000)
    }

    @Test
    fun testAddAndRemoveDependencyFlow() {
        val suffix = System.currentTimeMillis()
        val blockerName = "Blocker Task $suffix"
        val blockedName = "Blocked Task $suffix"

        createTask(blockerName)
        createTask(blockedName)

        // Open the blocked task's detail sheet
        composeTestRule.onNodeWithText(blockedName).performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("TaskDetailBottomSheet"), 15000)

        // Add blocker via the picker
        composeTestRule.onNodeWithText("Blocked by").performClick()
        composeTestRule.onNodeWithContentDescription("AddBlockedByButton", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithContentDescription("TaskPickerSearch", useUnmergedTree = true)
            .performTextInput(blockerName)
        pickerRow(blockerName).performClick()
        composeTestRule.onNodeWithText("Add (1)").performClick()

        // Dependency row + "Task is blocked" chip appear
        composeTestRule.waitUntilAtLeastOneExists(
            hasText(blockerName) and hasAnyAncestor(hasTestTag("TaskDetailBottomSheet")),
            15000,
        )
        composeTestRule.waitUntilAtLeastOneExists(
            hasText("Task is blocked") and hasAnyAncestor(hasTestTag("TaskDetailBottomSheet")),
            15000,
        )

        // Close and reopen the blocker's sheet — "Blocking other tasks" chip + row
        composeTestRule.onNodeWithContentDescription("CloseButton", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        composeTestRule.waitUntilDoesNotExist(hasTestTag("TaskDetailBottomSheet"), 15000)

        composeTestRule.onNodeWithText(blockerName).performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("TaskDetailBottomSheet"), 15000)
        composeTestRule.waitUntilAtLeastOneExists(
            hasText("Blocking other tasks") and hasAnyAncestor(hasTestTag("TaskDetailBottomSheet")),
            15000,
        )
        composeTestRule.onNodeWithText("Blocking").performScrollTo().performClick()
        composeTestRule.onNode(
            hasText(blockedName) and hasAnyAncestor(hasTestTag("TaskDetailBottomSheet")),
        ).performScrollTo().assertExists()

        // Remove the dependency from the blocker's "Blocking" section
        composeTestRule.onNodeWithContentDescription("Remove dependency", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        composeTestRule.waitUntilDoesNotExist(
            hasText(blockedName) and hasAnyAncestor(hasTestTag("TaskDetailBottomSheet")),
            15000,
        )
        composeTestRule.waitUntilDoesNotExist(
            hasText("Blocking other tasks") and hasAnyAncestor(hasTestTag("TaskDetailBottomSheet")),
            15000,
        )

        // Reopen the blocked task: dependency gone, chip gone
        composeTestRule.onNodeWithContentDescription("CloseButton", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        composeTestRule.waitUntilDoesNotExist(hasTestTag("TaskDetailBottomSheet"), 15000)
        composeTestRule.onNodeWithText(blockedName).performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("TaskDetailBottomSheet"), 15000)
        composeTestRule.waitUntilDoesNotExist(
            hasText(blockerName) and hasAnyAncestor(hasTestTag("TaskDetailBottomSheet")),
            15000,
        )
        composeTestRule.waitUntilDoesNotExist(
            hasText("Task is blocked") and hasAnyAncestor(hasTestTag("TaskDetailBottomSheet")),
            15000,
        )

        // Cleanup: remove the created tasks so the shared suite state stays
        // clean for other test classes (e.g. TagsIntegrationTest expects a
        // single pending task when clicking "Complete").
        composeTestRule.onNodeWithContentDescription("CloseButton", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        composeTestRule.waitUntilDoesNotExist(hasTestTag("TaskDetailBottomSheet"), 15000)
        deleteTask(blockerName)
        deleteTask(blockedName)
    }
}
