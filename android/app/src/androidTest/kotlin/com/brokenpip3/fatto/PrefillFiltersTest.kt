package com.brokenpip3.fatto

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class PrefillFiltersTest {
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
    fun testPrefillProjectFilter() {
        val projectName = "Work"
        val taskName = "Task in project"

        // 1. Create a task with a project first so it appears in the project list
        composeTestRule.onNodeWithContentDescription("Add Task").performClick()
        composeTestRule.onNode(hasTestTag("DescriptionInput")).performTextInput(taskName)
        composeTestRule.onNode(hasTestTag("ProjectInput")).performTextInput(projectName)
        composeTestRule.onNodeWithText("Create").performClick()
        composeTestRule.waitUntilDoesNotExist(hasTestTag("AddTaskDialog"), 10000)

        // 2. Go to Projects screen
        composeTestRule.onNodeWithText("Projects").performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasText(projectName), 15000)

        // 3. Select the project
        composeTestRule.onNodeWithText(projectName).performClick()

        // 4. Verify we are back on Tasks screen and filter is active
        composeTestRule.waitUntilAtLeastOneExists(hasText(taskName), 15000)

        // 5. Click Add Task
        composeTestRule.onNodeWithContentDescription("Add Task").performClick()

        // 6. Verify Project field is pre-filled
        composeTestRule.onNode(
            hasText(projectName) and hasAnyAncestor(hasTestTag("AddTaskDialog")),
        ).assertExists()
    }

    @Test
    fun testPrefillTagFilter() {
        val tagName = "urgent"
        val taskName = "Urgent task"

        // 1. Create a task with a tag first
        composeTestRule.onNodeWithContentDescription("Add Task").performClick()
        composeTestRule.onNode(hasTestTag("DescriptionInput")).performTextInput(taskName)
        composeTestRule.onNodeWithText("Add Tag").performTextInput(tagName)
        composeTestRule.onNodeWithContentDescription("AddTagButton").performClick()
        composeTestRule.onNodeWithText("Create").performClick()
        composeTestRule.waitUntilDoesNotExist(hasTestTag("AddTaskDialog"), 10000)

        // 2. Go to Tags screen
        composeTestRule.onNodeWithText("Tags").performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasText(tagName), 15000)

        // 3. Select the tag
        composeTestRule.onNodeWithText(tagName).performClick()

        // 4. Verify we are back on Tasks screen and filter is active
        composeTestRule.waitUntilAtLeastOneExists(hasText(taskName), 15000)

        // 5. Click Add Task
        composeTestRule.onNodeWithContentDescription("Add Task").performClick()

        // 6. Verify Tag is pre-filled (it should appear as a Chip with tag text)
        composeTestRule.onNode(
            hasText(tagName) and hasAnyAncestor(hasTestTag("AddTaskDialog")),
        ).assertExists()
    }
}
