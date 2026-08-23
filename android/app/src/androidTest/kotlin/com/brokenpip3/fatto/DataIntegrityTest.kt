package com.brokenpip3.fatto

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DataIntegrityTest {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun clearDatabase() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val dbDir = File(context.filesDir, "taskchampion")
        if (dbDir.exists()) {
            dbDir.deleteRecursively()
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun closeActivity() {
        scenario.close()
    }

    private fun dismissBottomSheet() {
        composeTestRule.waitUntilAtLeastOneExists(hasContentDescription("CloseButton"), 15000)
        composeTestRule.onNode(hasContentDescription("CloseButton"), useUnmergedTree = true).performScrollTo().performClick()
        composeTestRule.waitUntilDoesNotExist(hasTestTag("TaskDetailBottomSheet"), 15000)
        composeTestRule.waitForIdle()
    }

    @Test
    fun testVeryLongDescription() {
        val longDesc = "Long ".repeat(50) + System.currentTimeMillis()
        composeTestRule.onNodeWithContentDescription("Add Task").performClick()
        composeTestRule.onNodeWithText("Description").performTextInput(longDesc)
        composeTestRule.onNodeWithText("Create").performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasText(longDesc, substring = true), 15000)
    }

    @Test
    fun testTagAdditionAndRemoval() {
        val testDesc = "Tag test ${System.currentTimeMillis()}"
        val tagName = "test-tag"

        composeTestRule.onNodeWithContentDescription("Add Task").performClick()
        composeTestRule.onNodeWithText("Description").performTextInput(testDesc)
        composeTestRule.onNodeWithText("Create").performClick()

        composeTestRule.waitUntilAtLeastOneExists(hasText(testDesc), 15000)
        composeTestRule.onNode(hasText(testDesc) and hasAnyAncestor(hasTestTag("TaskList"))).performClick()

        // Wait for sheet
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("TaskDetailBottomSheet"), 15000)

        // Add tag. Espresso.closeSoftKeyboard / touch clicks are avoided on
        // purpose: the emulator does not reliably give the app window focus
        // while the soft keyboard is shown, which intermittently times out
        // Espresso's onView(isRoot()). A semantics click works regardless of
        // the keyboard covering the button.
        composeTestRule.onNode(hasContentDescription("TagInput"), useUnmergedTree = true).performScrollTo().performTextInput(tagName)
        composeTestRule.onNode(
            hasContentDescription("AddTagButton"),
            useUnmergedTree = true,
        ).performClick()

        // Verify tag chip appears in sheet
        composeTestRule.waitUntilAtLeastOneExists(hasText(tagName) and hasAnyAncestor(hasTestTag("TaskDetailBottomSheet")), 15000)
        composeTestRule.onNode(hasText(tagName) and hasAnyAncestor(hasTestTag("TaskDetailBottomSheet"))).performScrollTo().assertExists()

        dismissBottomSheet()

        // Verify persistence in list
        composeTestRule.waitUntilAtLeastOneExists(hasText(tagName) and hasAnyAncestor(hasTestTag("TaskList")), 15000)

        // Re-open to remove
        composeTestRule.onNode(hasText(testDesc) and hasAnyAncestor(hasTestTag("TaskList"))).performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasContentDescription("Remove $tagName"), 15000)
        composeTestRule.onNode(hasContentDescription("Remove $tagName"), useUnmergedTree = true).performScrollTo().performClick()

        // Wait for chip to disappear from sheet
        composeTestRule.waitUntilDoesNotExist(hasText(tagName) and hasAnyAncestor(hasTestTag("TaskDetailBottomSheet")), 15000)

        dismissBottomSheet()

        // Verify removal in list
        composeTestRule.onAllNodes(hasText(tagName) and hasAnyAncestor(hasTestTag("TaskList"))).assertCountEquals(0)
    }

    @Test
    fun testStartStopInDetailSheet() {
        val testDesc = "Start stop test ${System.currentTimeMillis()}"
        composeTestRule.onNodeWithContentDescription("Add Task").performClick()
        composeTestRule.onNodeWithText("Description").performTextInput(testDesc)
        composeTestRule.onNodeWithText("Create").performClick()

        composeTestRule.waitUntilAtLeastOneExists(hasText(testDesc), 15000)
        composeTestRule.onNode(hasText(testDesc) and hasAnyAncestor(hasTestTag("TaskList"))).performClick()

        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("TaskDetailBottomSheet"), 15000)

        composeTestRule.onNode(hasContentDescription("Start"), useUnmergedTree = true).performScrollTo().performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasContentDescription("Stop"), 15000)
        composeTestRule.onNode(hasContentDescription("Stop"), useUnmergedTree = true).performScrollTo().performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasContentDescription("Start"), 15000)

        dismissBottomSheet()
    }
}
