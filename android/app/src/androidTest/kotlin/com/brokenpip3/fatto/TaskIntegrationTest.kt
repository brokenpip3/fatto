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
import androidx.compose.ui.test.performTextReplacement
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
class TaskIntegrationTest {
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

    private fun dismissBottomSheet() {
        composeTestRule.waitUntilAtLeastOneExists(hasContentDescription("CloseButton"), 15000)
        composeTestRule.onNode(hasContentDescription("CloseButton"), useUnmergedTree = true).performClick()
        composeTestRule.waitUntilDoesNotExist(hasTestTag("TaskDetailBottomSheet"), 15000)
        composeTestRule.waitForIdle()
    }

    @Test
    fun testAddTaskFlow() {
        val description = "Integration Task ${System.currentTimeMillis()}"
        composeTestRule.onNodeWithContentDescription("Add Task").performClick()
        composeTestRule.onNodeWithText("Description").performTextInput(description)
        composeTestRule.onNodeWithText("Create").performClick()

        composeTestRule.waitUntilDoesNotExist(hasText("New Task"), 10000)

        composeTestRule.waitUntilAtLeastOneExists(hasText(description) and hasAnyAncestor(hasTestTag("TaskList")), 15000)
    }

    @Test
    fun testEditTaskFlow() {
        val initialDescription = "Edit Me ${System.currentTimeMillis()}"
        composeTestRule.onNodeWithContentDescription("Add Task").performClick()
        composeTestRule.onNodeWithText("Description").performTextInput(initialDescription)
        composeTestRule.onNodeWithText("Create").performClick()

        composeTestRule.waitUntilDoesNotExist(hasText("New Task"), 10000)

        composeTestRule.waitUntilAtLeastOneExists(hasText(initialDescription) and hasAnyAncestor(hasTestTag("TaskList")), 15000)
        composeTestRule.onNode(hasText(initialDescription) and hasAnyAncestor(hasTestTag("TaskList"))).performClick()

        val updatedDescription = "Updated ${System.currentTimeMillis()}"
        composeTestRule.waitUntilAtLeastOneExists(hasContentDescription("TaskDescriptionInput"), 15000)
        composeTestRule.onNode(
            hasContentDescription("TaskDescriptionInput"),
            useUnmergedTree = true,
        ).performScrollTo().performTextReplacement(updatedDescription)

        dismissBottomSheet()
        composeTestRule.waitUntilAtLeastOneExists(hasText(updatedDescription) and hasAnyAncestor(hasTestTag("TaskList")), 15000)
    }

    @Test
    fun testToggleActiveFlow() {
        val testDescription = "Active toggle ${System.currentTimeMillis()}"

        composeTestRule.onNodeWithContentDescription("Add Task").performClick()
        composeTestRule.onNodeWithText("Description").performTextInput(testDescription)
        composeTestRule.onNodeWithText("Create").performClick()

        composeTestRule.waitUntilDoesNotExist(hasText("New Task"), 10000)

        composeTestRule.waitUntilAtLeastOneExists(hasText(testDescription) and hasAnyAncestor(hasTestTag("TaskList")), 15000)
        composeTestRule.onNode(hasText(testDescription) and hasAnyAncestor(hasTestTag("TaskList"))).performClick()

        composeTestRule.waitUntilAtLeastOneExists(hasContentDescription("Start"), 15000)
        composeTestRule.onNode(hasContentDescription("Start"), useUnmergedTree = true).performScrollTo().performClick()

        dismissBottomSheet()

        composeTestRule.waitUntilAtLeastOneExists(
            hasContentDescription("Active") and
                hasText(testDescription, substring = true) and
                hasAnyAncestor(hasTestTag("TaskList")),
            15000,
        )

        composeTestRule.onNode(hasText(testDescription, substring = true) and hasAnyAncestor(hasTestTag("TaskList"))).performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasContentDescription("Stop"), 15000)
        composeTestRule.onNode(hasContentDescription("Stop"), useUnmergedTree = true).performScrollTo().performClick()
        dismissBottomSheet()

        // Verify active icon is gone
        composeTestRule.onNode(
            hasContentDescription("Active") and
                hasText(testDescription, substring = true) and
                hasAnyAncestor(hasTestTag("TaskList")),
        ).assertDoesNotExist()
    }
}
