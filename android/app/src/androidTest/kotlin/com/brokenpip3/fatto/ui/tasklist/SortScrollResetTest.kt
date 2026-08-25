package com.brokenpip3.fatto.ui.tasklist

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.brokenpip3.fatto.MainActivity
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Regression test for the bug where selecting a sort method kept the list
 * scrolled at its previous position instead of jumping back to the top.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SortScrollResetTest {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun cleanState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbDir = File(context.filesDir, "taskchampion")
        if (dbDir.exists()) {
            dbDir.deleteRecursively()
        }
        // Fresh settings too, so the sort order/direction defaults to
        // DATE_CREATED (descending) regardless of previous test runs.
        context.getSharedPreferences("sync_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun closeScenario() {
        scenario?.close()
        scenario = null
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbDir = File(context.filesDir, "taskchampion")
        if (dbDir.exists()) {
            dbDir.deleteRecursively()
        }
    }

    private fun launchActivity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        scenario = ActivityScenario.launch<MainActivity>(android.content.Intent(context, MainActivity::class.java))
    }

    private fun addTask(description: String) {
        composeTestRule.onNodeWithContentDescription("Add Task").performClick()
        composeTestRule.onNodeWithText("Description").performTextInput(description)
        composeTestRule.onNodeWithText("Create").performClick()
        composeTestRule.waitUntilDoesNotExist(hasText("New Task"), 10000)
        composeTestRule.waitForIdle()
    }

    @Test
    fun testSelectingSortMethodScrollsBackToTop() {
        launchActivity()
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("TaskList"), 15000)

        // Seed 16 tasks. With the default sort (DATE_CREATED descending),
        // "Task 16" is the newest task (top of list) and "Task 01" the oldest
        // (bottom of list). Alphabetically, "Task 01" comes first.
        repeat(16) { i ->
            val number = (i + 1).toString().padStart(2, '0')
            addTask("Task $number")
        }
        composeTestRule.waitForIdle()

        // Scroll to the very bottom of the list: the top item must no longer
        // be composed, proving we are no longer at the top.
        composeTestRule.onNodeWithTag("TaskList").performScrollToIndex(15)
        composeTestRule.waitForIdle()
        composeTestRule.onNode(
            hasText("Task 16") and hasAnyAncestor(hasTestTag("TaskList")),
        ).assertDoesNotExist()
        composeTestRule.onNode(
            hasText("Task 01") and hasAnyAncestor(hasTestTag("TaskList")),
        ).assertExists()

        // Switch to alphabetical sorting: the list must jump back to the top,
        // where "Task 01" (alphabetically first) is now visible.
        composeTestRule.onNodeWithContentDescription("Sort").performClick()
        composeTestRule.onNodeWithText("Alphabetical").performClick()

        composeTestRule.waitUntilAtLeastOneExists(
            hasText("Task 01") and hasAnyAncestor(hasTestTag("TaskList")),
            15000,
        )
    }
}
