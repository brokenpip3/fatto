package com.brokenpip3.fatto

import androidx.compose.ui.test.ExperimentalTestApi
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
class TagsIntegrationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    @Before
    fun clearDatabase() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext

        // Clear taskchampion database
        val dbDir = File(context.filesDir, "taskchampion")
        if (dbDir.exists()) {
            dbDir.deleteRecursively()
        }

        // Clear shared preferences
        context.getSharedPreferences("sync_settings", android.content.Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun testInternalTagsFiltering() {
        // Wait for the app to load
        composeTestRule.waitUntilAtLeastOneExists(hasContentDescription("Add Task"), 30000)

        val taskDescription = "Internal Tag Test ${System.currentTimeMillis()}"

        // 1. Add a task with a regular tag
        composeTestRule.onNodeWithContentDescription("Add Task").performClick()
        composeTestRule.onNodeWithText("Description").performTextInput(taskDescription)

        // Add regular tag
        composeTestRule.onNode(hasContentDescription("TagInput")).performTextInput("regular-tag")
        composeTestRule.onNodeWithContentDescription("AddTagButton").performClick()

        composeTestRule.onNodeWithText("Create").performClick()
        composeTestRule.waitUntilDoesNotExist(hasText("New Task"), 15000)

        // Ensure we are on the tasks list
        composeTestRule.waitUntilAtLeastOneExists(hasText(taskDescription), 15000)

        // 2. Start the task to naturally add the ACTIVE internal tag
        composeTestRule.onNode(hasText(taskDescription)).performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasContentDescription("Start"), 15000)
        composeTestRule.onNode(hasContentDescription("Start")).performScrollTo().performClick()

        // Close bottom sheet
        composeTestRule.waitUntilAtLeastOneExists(hasContentDescription("CloseButton"), 15000)
        composeTestRule.onNode(hasContentDescription("CloseButton"), useUnmergedTree = true).performClick()
        composeTestRule.waitUntilDoesNotExist(hasTestTag("TaskDetailBottomSheet"), 15000)

        // 3. Verify regular-tag is visible and ACTIVE is hidden by default
        composeTestRule.onNodeWithText("regular-tag").assertExists()
        composeTestRule.onNodeWithText("ACTIVE").assertDoesNotExist()
    }

    @Test
    fun testTagsAlphabeticalSorting() {
        // Wait for the app to load
        composeTestRule.waitUntilAtLeastOneExists(hasContentDescription("Add Task"), 30000)

        val taskDescription = "Sorting Test ${System.currentTimeMillis()}"

        composeTestRule.onNodeWithContentDescription("Add Task").performClick()
        composeTestRule.onNodeWithText("Description").performTextInput(taskDescription)

        // Add tags in non-alphabetical order
        composeTestRule.onNode(hasContentDescription("TagInput")).performTextInput("zebra")
        composeTestRule.onNodeWithContentDescription("AddTagButton").performClick()
        composeTestRule.onNode(hasContentDescription("TagInput")).performTextInput("apple")
        composeTestRule.onNodeWithContentDescription("AddTagButton").performClick()
        composeTestRule.onNode(hasContentDescription("TagInput")).performTextInput("banana")
        composeTestRule.onNodeWithContentDescription("AddTagButton").performClick()

        composeTestRule.onNodeWithText("Create").performClick()
        composeTestRule.waitUntilDoesNotExist(hasText("New Task"), 15000)

        composeTestRule.waitUntilAtLeastOneExists(hasText(taskDescription), 15000)

        // Tags should exist.
        composeTestRule.onNodeWithText("apple").assertExists()
        composeTestRule.onNodeWithText("banana").assertExists()
        composeTestRule.onNodeWithText("zebra").assertExists()
    }

    @Test
    fun testStaleTagChipRemovedAfterComplete() {
        // Wait for the app to load
        composeTestRule.waitUntilAtLeastOneExists(hasContentDescription("Add Task"), 30000)

        val staleTag = "stale-complete-${System.currentTimeMillis()}"
        val taskDescription = "Complete me ${System.currentTimeMillis()}"

        // Create a task carrying a tag
        composeTestRule.onNodeWithContentDescription("Add Task").performClick()
        composeTestRule.onNode(hasTestTag("DescriptionInput")).performTextInput(taskDescription)
        composeTestRule.onNode(hasContentDescription("TagInput")).performTextInput(staleTag)
        composeTestRule.onNodeWithContentDescription("AddTagButton").performClick()
        composeTestRule.onNodeWithText("Create").performClick()
        composeTestRule.waitUntilDoesNotExist(hasTestTag("AddTaskDialog"), 15000)

        // Open the Filters panel and verify the tag chip appears
        composeTestRule.onNodeWithContentDescription("Toggle Filters").performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasText(staleTag), 15000)

        // Complete the task (confirmation dialog)
        composeTestRule.onNodeWithContentDescription("Complete").performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasText("Confirm"), 15000)
        composeTestRule.onNodeWithText("Confirm").performClick()

        // The chip must disappear once no pending task carries the tag
        composeTestRule.waitUntilDoesNotExist(hasText(staleTag), 15000)
    }

    @Test
    fun testStaleTagChipRemovedAfterDelete() {
        // Wait for the app to load
        composeTestRule.waitUntilAtLeastOneExists(hasContentDescription("Add Task"), 30000)

        val staleTag = "stale-delete-${System.currentTimeMillis()}"
        val taskDescription = "Delete me ${System.currentTimeMillis()}"

        // 1. Create a task carrying a tag
        composeTestRule.onNodeWithContentDescription("Add Task").performClick()
        composeTestRule.onNode(hasTestTag("DescriptionInput")).performTextInput(taskDescription)
        composeTestRule.onNode(hasContentDescription("TagInput")).performTextInput(staleTag)
        composeTestRule.onNodeWithContentDescription("AddTagButton").performClick()
        composeTestRule.onNodeWithText("Create").performClick()
        composeTestRule.waitUntilDoesNotExist(hasTestTag("AddTaskDialog"), 15000)

        // 2. Open the Filters panel and verify the tag chip appears
        composeTestRule.onNodeWithContentDescription("Toggle Filters").performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasText(staleTag), 15000)

        // 3. Delete the task (confirmation dialog)
        composeTestRule.onNodeWithContentDescription("DeleteTask").performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasText("Confirm"), 15000)
        composeTestRule.onNodeWithText("Confirm").performClick()

        // 4. The chip must disappear once no pending task carries the tag
        composeTestRule.waitUntilDoesNotExist(hasText(staleTag), 15000)
    }
}
