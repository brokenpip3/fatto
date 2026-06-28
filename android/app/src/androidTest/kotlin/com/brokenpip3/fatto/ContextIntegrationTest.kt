package com.brokenpip3.fatto

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
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
class ContextIntegrationTest {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun clearState() {
        clearAppState()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun closeScenario() {
        if (::scenario.isInitialized) {
            scenario.close()
        }
        clearAppState()
    }

    @Test
    fun saveClearAndSelectContextFromTaskList() {
        val suffix = System.currentTimeMillis()
        val matchingTask = "Context alpha $suffix"
        val hiddenTask = "Context beta $suffix"
        val contextName = "Context $suffix"

        createTask(matchingTask)
        createTask(hiddenTask)

        composeTestRule.onNodeWithContentDescription("Contexts").performClick()
        composeTestRule.onNodeWithText("New context").performClick()
        composeTestRule.onNodeWithText("Search").performTextInput(matchingTask)
        composeTestRule.onNodeWithText("Save").performClick()
        composeTestRule.onNodeWithText("Context name").performTextClearance()
        composeTestRule.onNodeWithText("Context name").performTextInput(contextName)
        composeTestRule.onNodeWithContentDescription("Confirm save context").performClick()

        composeTestRule.waitUntilAtLeastOneExists(hasText("Context: $contextName"), 15000)
        composeTestRule.waitUntilAtLeastOneExists(hasTaskListText(matchingTask), 15000)
        composeTestRule.waitUntilDoesNotExist(hasTaskListText(hiddenTask), 15000)

        composeTestRule.onNodeWithContentDescription("Clear context").performClick()
        composeTestRule.waitUntilDoesNotExist(hasText("Context: $contextName"), 15000)
        composeTestRule.waitUntilAtLeastOneExists(hasTaskListText(hiddenTask), 15000)

        composeTestRule.onNodeWithContentDescription("Contexts").performClick()
        composeTestRule.onNodeWithText(contextName).performClick()

        composeTestRule.waitUntilAtLeastOneExists(hasText("Context: $contextName"), 15000)
        composeTestRule.waitUntilAtLeastOneExists(hasTaskListText(matchingTask), 15000)
        composeTestRule.waitUntilDoesNotExist(hasTaskListText(hiddenTask), 15000)
    }

    private fun createTask(description: String) {
        composeTestRule.onNodeWithContentDescription("Add Task").performClick()
        composeTestRule.onNodeWithText("Description").performTextInput(description)
        composeTestRule.onNodeWithText("Create").performClick()
        composeTestRule.waitUntilDoesNotExist(hasText("New Task"), 10000)
        composeTestRule.waitUntilAtLeastOneExists(hasTaskListText(description), 15000)
    }

    private fun hasTaskListText(text: String) = hasText(text) and hasAnyAncestor(hasTestTag("TaskList"))

    private fun clearAppState() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val dbDir = File(context.filesDir, "taskchampion")
        if (dbDir.exists()) {
            dbDir.deleteRecursively()
        }
        context.deleteSharedPreferences("sync_settings")
    }
}
