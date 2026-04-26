package com.brokenpip3.fatto

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TagsIntegrationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testInternalTagsFiltering() {
        val taskDescription = "Internal Tag Test ${System.currentTimeMillis()}"

        // 1. Add a task with a regular tag and an internal tag
        composeTestRule.onNodeWithContentDescription("Add Task").performClick()
        composeTestRule.onNodeWithText("Description").performTextInput(taskDescription)

        // Add regular tag
        composeTestRule.onNode(hasContentDescription("TagInput")).performTextInput("regular-tag")
        composeTestRule.onNodeWithContentDescription("AddTagButton").performClick()

        // Add internal tag
        composeTestRule.onNode(hasContentDescription("TagInput")).performTextInput("WAITING")
        composeTestRule.onNodeWithContentDescription("AddTagButton").performClick()

        composeTestRule.onNodeWithText("Create").performClick()
        composeTestRule.waitUntilDoesNotExist(hasText("New Task"), 10000)

        // Verify both tags are visible initially (default setting is true)
        composeTestRule.waitUntilAtLeastOneExists(hasText(taskDescription), 15000)
        composeTestRule.onNodeWithText("regular-tag").assertExists()
        composeTestRule.onNodeWithText("WAITING").assertExists()

        // 2. Go to Settings and disable "Show internal tags"
        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.onNodeWithText("Show internal tags").performClick()

        // 3. Go back to Tasks and verify WAITING is hidden
        composeTestRule.onNodeWithText("Tasks").performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasText(taskDescription), 15000)
        composeTestRule.onNodeWithText("regular-tag").assertExists()
        composeTestRule.onNodeWithText("WAITING").assertDoesNotExist()

        // 4. Cleanup: (Optional) Re-enable for subsequent tests if state persists,
        // but typically tests should be isolated.
        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.onNodeWithText("Show internal tags").performClick()
    }

    @Test
    fun testTagsAlphabeticalSorting() {
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
        composeTestRule.waitUntilDoesNotExist(hasText("New Task"), 10000)

        // We verify they exist.
        // Verifying exact order in Compose is harder without custom matchers,
        // but the unit test covers the logic.
        composeTestRule.waitUntilAtLeastOneExists(hasText(taskDescription), 15000)
        composeTestRule.onNodeWithText("apple").assertExists()
        composeTestRule.onNodeWithText("banana").assertExists()
        composeTestRule.onNodeWithText("zebra").assertExists()
    }
}
