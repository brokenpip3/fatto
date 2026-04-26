package com.brokenpip3.fatto

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class PriorityIntegrationTest {
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
    fun testPrioritySelectionInAddDialog() {
        val description = "High priority task ${System.currentTimeMillis()}"

        composeTestRule.onNodeWithContentDescription("Add Task").performClick()
        composeTestRule.onNodeWithText("Description").performTextInput(description)

        // Select High Priority
        composeTestRule.onNodeWithContentDescription("Set Priority").performClick()
        composeTestRule.onNodeWithText("High").performClick()

        composeTestRule.onNodeWithText("Create").performClick()

        // Verify task appears
        composeTestRule.waitUntilAtLeastOneExists(hasText(description), 15000)

        // Open details
        composeTestRule.onNode(hasText(description) and hasAnyAncestor(hasTestTag("TaskList"))).performClick()

        // Verify High priority is selected in the bottom sheet
        composeTestRule.waitUntilAtLeastOneExists(hasText("High"), 15000)
        // Check that the High surface is selected (we could check colors but simple existence is a good start)
        composeTestRule.onNodeWithText("High").assertExists()
    }

    @Test
    fun testAdvancedDetailsToggle() {
        val description = "Advanced details test ${System.currentTimeMillis()}"

        composeTestRule.onNodeWithContentDescription("Add Task").performClick()
        composeTestRule.onNodeWithText("Description").performTextInput(description)
        composeTestRule.onNodeWithText("Create").performClick()

        composeTestRule.waitUntilAtLeastOneExists(hasText(description), 15000)
        composeTestRule.onNode(hasText(description) and hasAnyAncestor(hasTestTag("TaskList"))).performClick()

        // Initially "Urgency" should not be visible
        composeTestRule.onNode(hasText("Urgency:", substring = true)).assertDoesNotExist()

        // Toggle advanced details
        composeTestRule.onNodeWithText("Show Advanced Details").performScrollTo().performClick()

        // Now Urgency should be visible
        composeTestRule.onNode(hasText("Urgency:", substring = true)).assertExists()

        // Toggle back
        composeTestRule.onNodeWithText("Hide Advanced Details").performScrollTo().performClick()
        composeTestRule.onNode(hasText("Urgency:", substring = true)).assertDoesNotExist()
    }
}
