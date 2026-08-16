package com.brokenpip3.fatto

import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ShareTargetBlankShareTest {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun closeScenario() {
        scenario?.close()
        scenario = null
    }

    @Test
    fun testBlankShareDoesNotOpenDialog() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        scenario =
            ActivityScenario.launch<MainActivity>(
                Intent(Intent.ACTION_SEND).apply {
                    setClass(context, MainActivity::class.java)
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "   ")
                },
            )
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("TaskList"), 15000)
        composeTestRule.onNodeWithTag("AddTaskDialog").assertDoesNotExist()
    }
}
