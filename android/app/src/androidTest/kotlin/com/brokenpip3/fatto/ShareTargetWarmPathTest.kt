package com.brokenpip3.fatto

import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ShareTargetWarmPathTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    private fun shareIntent(text: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }

    private fun deliverShare(text: String) {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        composeTestRule.activityRule.scenario.onActivity { activity ->
            val originalIntent = activity.intent
            instrumentation.callActivityOnNewIntent(activity, shareIntent(text))
            // MainActivity.onNewIntent calls setIntent(), which changes getIntent() and
            // breaks ActivityScenario's lifecycle-event attribution. Restore it so the
            // rule's close() can observe the activity being destroyed.
            activity.setIntent(originalIntent)
        }
    }

    @Test
    fun testShareWhileOnSettingsNavigatesToTasksAndPrefills() {
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("TaskList"), 15000)
        composeTestRule.onNodeWithText("Settings").performClick()

        deliverShare("shared while on settings")

        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("AddTaskDialog"), 10000)
        composeTestRule.onNode(hasTestTag("DescriptionInput")).assertTextContains("shared while on settings")
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitUntilDoesNotExist(hasTestTag("AddTaskDialog"), 10000)
    }

    @Test
    fun testShareWhileDialogOpenReplacesContent() {
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("TaskList"), 15000)
        composeTestRule.onNodeWithContentDescription("Add Task").performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("AddTaskDialog"), 10000)
        composeTestRule.onNode(hasTestTag("DescriptionInput")).performTextInput("typed text")

        deliverShare("shared replacement")

        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("AddTaskDialog"), 10000)
        composeTestRule.onNode(hasTestTag("DescriptionInput")).assertTextContains("shared replacement")
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitUntilDoesNotExist(hasTestTag("AddTaskDialog"), 10000)
    }
}
