package com.brokenpip3.fatto

import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class ShareTargetColdStartTest {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun clearDatabase() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val dbDir = File(context.filesDir, "taskchampion")
        if (dbDir.exists()) {
            dbDir.deleteRecursively()
        }
    }

    @After
    fun closeScenario() {
        scenario?.close()
        scenario = null

        // Tests in this class create tasks (e.g. testShareColdStartCreateAddsTask).
        // Delete the database so no tasks leak into subsequent test classes, which
        // rely on starting from an empty task list.
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val dbDir = File(context.filesDir, "taskchampion")
        if (dbDir.exists()) {
            dbDir.deleteRecursively()
        }
    }

    private fun launchWithShare(text: String) {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        scenario =
            ActivityScenario.launch<MainActivity>(
                Intent(Intent.ACTION_SEND).apply {
                    setClass(context, MainActivity::class.java)
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                },
            )
    }

    @Test
    fun testShareColdStartOpensDialogPrefilled() {
        launchWithShare("https://example.com/docs")
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("AddTaskDialog"), 10000)
        composeTestRule.onNode(hasTestTag("DescriptionInput")).assertTextContains("https://example.com/docs")
    }

    @Test
    fun testShareColdStartCreateAddsTask() {
        launchWithShare("https://example.com/docs")
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("AddTaskDialog"), 10000)
        composeTestRule.onNodeWithText("Create").performClick()
        composeTestRule.waitUntilDoesNotExist(hasTestTag("AddTaskDialog"), 10000)
        composeTestRule.waitUntilAtLeastOneExists(hasText("https://example.com/docs"), 15000)
    }
}
