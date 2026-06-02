package com.brokenpip3.fatto.ui.tasklist

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brokenpip3.fatto.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PullToRefreshSyncTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testTaskListRenders() {
        composeTestRule.onNodeWithTag("TaskList").assertExists()
    }

    @Test
    fun testPullDownOnTaskListDoesNotCrash() {
        val taskList = composeTestRule.onNodeWithTag("TaskList")
        taskList.assertExists()
        taskList.performTouchInput {
            swipeDown(
                startY = 100f,
                endY = 300f,
                durationMillis = 300,
            )
        }
        taskList.assertExists()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Sync").assertExists()
    }
}
