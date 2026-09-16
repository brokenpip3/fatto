package com.brokenpip3.fatto

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.brokenpip3.fatto.data.SettingsRepositoryImpl
import com.brokenpip3.fatto.data.TaskSwipeAction
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TaskSwipeIntegrationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    @After
    fun resetSwipeActions() {
        SettingsRepositoryImpl(composeTestRule.activity.applicationContext).apply {
            setSwipeStartToEndAction(TaskSwipeAction.NONE)
            setSwipeEndToStartAction(TaskSwipeAction.NONE)
        }
    }

    private fun taskRow(description: String) =
        composeTestRule.onNode(
            hasTestTag("SwipeableTaskRow") and hasAnyDescendant(hasText(description)),
            useUnmergedTree = true,
        )

    private fun taskAction(
        description: String,
        contentDescription: String,
    ): SemanticsMatcher {
        return hasContentDescription(contentDescription) and hasAnyAncestor(hasText(description))
    }

    private fun setSwipeAction(
        selectorTag: String,
        action: TaskSwipeAction,
    ) {
        composeTestRule.onNodeWithTag(selectorTag).performClick()
        composeTestRule.onNodeWithTag("$selectorTag-${action.name}").performClick()
    }

    private fun setConfirmActions(enabled: Boolean) {
        val node = composeTestRule.onNodeWithText("Confirm complete/delete")
        val current =
            node.fetchSemanticsNode().config[
                SemanticsProperties.ToggleableState,
            ] == ToggleableState.On
        if (current != enabled) node.performClick()
    }

    private fun configureActions(
        right: TaskSwipeAction,
        left: TaskSwipeAction,
        confirm: Boolean,
    ) {
        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.onNodeWithTag("SettingsTabDisplay").performScrollTo().performClick()
        setConfirmActions(confirm)
        composeTestRule.onNodeWithTag("SwipeRightActionSelector").performScrollTo()
        setSwipeAction("SwipeRightActionSelector", right)
        composeTestRule.onNodeWithTag("SwipeLeftActionSelector").performScrollTo()
        setSwipeAction("SwipeLeftActionSelector", left)
        composeTestRule.onNodeWithText("Tasks").performClick()
    }

    private fun createTask(description: String) {
        composeTestRule.onNodeWithContentDescription("Add Task").performClick()
        composeTestRule.onNodeWithText("Description").performTextInput(description)
        composeTestRule.onNodeWithText("Create").performClick()
        composeTestRule.waitUntilDoesNotExist(hasText("New Task"), 10_000)
        composeTestRule.waitUntilAtLeastOneExists(hasText(description), 15_000)
    }

    @Test
    fun disabledSwipesLeavePendingTaskUntouched() {
        val description = "Disabled swipe ${System.currentTimeMillis()}"
        configureActions(TaskSwipeAction.NONE, TaskSwipeAction.NONE, confirm = true)
        createTask(description)

        taskRow(description).performTouchInput { swipeRight() }

        composeTestRule.onNodeWithText(description).assertExists()
        composeTestRule.onNodeWithText("Complete Task").assertDoesNotExist()
        composeTestRule.onNodeWithText("Are you sure you want to delete this task?").assertDoesNotExist()
    }

    @Test
    fun toggleSwipeUsesCompletionConfirmation() {
        val description = "Confirmed swipe ${System.currentTimeMillis()}"
        configureActions(TaskSwipeAction.COMPLETE, TaskSwipeAction.NONE, confirm = true)
        createTask(description)

        taskRow(description).performTouchInput { swipeRight() }

        composeTestRule.onNodeWithText("Complete Task").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.onNodeWithText(description).assertExists()
    }

    @Test
    fun completeSwipeOnCompletedTaskLeavesItCompleted() {
        val description = "Restore swipe ${System.currentTimeMillis()}"
        configureActions(TaskSwipeAction.COMPLETE, TaskSwipeAction.NONE, confirm = false)
        createTask(description)

        taskRow(description).performTouchInput { swipeRight() }
        composeTestRule.onNodeWithText("Completed", substring = true).performClick()
        composeTestRule.onNode(taskAction(description, "Restore")).assertIsDisplayed()
        taskRow(description).performTouchInput { swipeRight() }

        composeTestRule.onNodeWithText("Complete Task").assertDoesNotExist()
        composeTestRule.onNode(taskAction(description, "Complete")).assertDoesNotExist()
        composeTestRule.onNode(taskAction(description, "Restore")).assertIsDisplayed()
    }

    @Test
    fun deleteSwipeUsesDeleteConfirmation() {
        val description = "Delete swipe ${System.currentTimeMillis()}"
        configureActions(TaskSwipeAction.NONE, TaskSwipeAction.DELETE, confirm = true)
        createTask(description)

        taskRow(description).performTouchInput { swipeLeft() }

        composeTestRule.onNodeWithText("Are you sure you want to delete this task?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.onNodeWithText(description).assertExists()
    }
}
