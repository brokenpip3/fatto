package com.brokenpip3.fatto.ui.tasklist

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskFilterBuilderSheetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun newContextShowsOnlySaveAction() {
        composeTestRule.setContent {
            TaskFilterBuilderSheet(
                initialState = TaskFilterState(),
                availableProjects = listOf("Work"),
                availableTags = setOf("office"),
                contextName = "",
                purpose = TaskFilterBuilderPurpose.CONTEXT,
                onDismiss = {},
                onApply = null,
                onSaveContext = { _, _ -> },
            )
        }

        composeTestRule.onNodeWithText("Save").assertExists()
        composeTestRule.onNodeWithText("Update").assertDoesNotExist()
        composeTestRule.onNodeWithText("Apply").assertDoesNotExist()
    }

    @Test
    fun existingContextShowsOnlyUpdateAction() {
        composeTestRule.setContent {
            TaskFilterBuilderSheet(
                initialState = TaskFilterState(),
                availableProjects = listOf("Work"),
                availableTags = setOf("office"),
                contextName = "Work",
                purpose = TaskFilterBuilderPurpose.CONTEXT,
                onDismiss = {},
                onApply = {},
                onSaveContext = { _, _ -> },
            )
        }

        composeTestRule.onNodeWithText("Update").assertExists()
        composeTestRule.onNodeWithText("Save").assertDoesNotExist()
        composeTestRule.onNodeWithText("Apply").assertDoesNotExist()
    }

    @Test
    fun contextBuilderSectionsStartCollapsed() {
        composeTestRule.setContent {
            TaskFilterBuilderSheet(
                initialState = TaskFilterState(),
                availableProjects = listOf("Work"),
                availableTags = setOf("office"),
                contextName = "",
                purpose = TaskFilterBuilderPurpose.CONTEXT,
                onDismiss = {},
                onApply = null,
                onSaveContext = { _, _ -> },
            )
        }

        composeTestRule.onNodeWithText("Projects").assertExists()
        composeTestRule.onNodeWithText("Tags").assertExists()
        composeTestRule.onNodeWithText("Virtual tags").assertExists()
        composeTestRule.onNodeWithText("Find project").assertDoesNotExist()
        composeTestRule.onNodeWithText("Find tag").assertDoesNotExist()
        composeTestRule.onNodeWithText("PENDING").assertDoesNotExist()
    }

    @Test
    fun contextSaveIncludesPendingSearchText() {
        var savedExpression: String? = null

        composeTestRule.setContent {
            TaskFilterBuilderSheet(
                initialState = TaskFilterState(),
                availableProjects = listOf("Work"),
                availableTags = setOf("office"),
                contextName = "",
                purpose = TaskFilterBuilderPurpose.CONTEXT,
                onDismiss = {},
                onApply = null,
                onSaveContext = { _, state -> savedExpression = state.expressionText() },
            )
        }

        composeTestRule.onNodeWithText("Search").performTextInput("alpha task")
        composeTestRule.onNodeWithText("Save").performClick()
        composeTestRule.onNodeWithText("Context name").performTextInput("Alpha")
        composeTestRule.onNodeWithContentDescription("Confirm save context").performClick()

        assertEquals("\"alpha task\"", savedExpression)
    }

    @Test
    fun contextExpressionModeShowsValidationError() {
        composeTestRule.setContent {
            TaskFilterBuilderSheet(
                initialState = TaskFilterState(),
                availableProjects = listOf("Work"),
                availableTags = setOf("office"),
                contextName = "",
                purpose = TaskFilterBuilderPurpose.CONTEXT,
                onDismiss = {},
                onApply = null,
                onSaveContext = { _, _ -> },
            )
        }

        composeTestRule.onNodeWithTag("ContextExpressionTab").performClick()
        composeTestRule.onNodeWithTag("ContextExpressionInput").performTextInput("(+work or +home")

        composeTestRule.onNodeWithText("Missing closing parenthesis").assertExists()
    }
}
