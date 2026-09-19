package com.brokenpip3.fatto.ui.tasklist

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
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
class AddTaskDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testInitialProjectPrefill() {
        val initialProject = "Work"

        composeTestRule.setContent {
            AddTaskDialog(
                availableProjects = listOf("Work", "Home"),
                availableTags = emptyList(),
                initialProject = initialProject,
                onDismiss = {},
                onConfirm = { _, _, _, _, _, _, _, _, _ -> },
            )
        }

        composeTestRule.onNode(
            hasText(initialProject) and hasAnyAncestor(hasTestTag("AddTaskDialog")),
        ).assertExists()
    }

    @Test
    fun testInitialTagsPrefill() {
        val initialTags = listOf("urgent", "work")

        composeTestRule.setContent {
            AddTaskDialog(
                availableProjects = emptyList(),
                availableTags = listOf("urgent", "work", "home"),
                initialTags = initialTags,
                onDismiss = {},
                onConfirm = { _, _, _, _, _, _, _, _, _ -> },
            )
        }

        initialTags.forEach { tag ->
            composeTestRule.onNode(
                hasText(tag) and hasAnyAncestor(hasTestTag("AddTaskDialog")),
            ).assertExists()
        }
    }

    @Test
    fun projectPickerSelectionIsSubmittedWithNewTask() {
        var submittedProject: String? = null

        composeTestRule.setContent {
            AddTaskDialog(
                availableProjects = listOf("Home", "Work.Client"),
                availableTags = emptyList(),
                onDismiss = {},
                onConfirm = { _, project, _, _, _, _, _, _, _ -> submittedProject = project },
            )
        }

        composeTestRule.onNode(
            hasTestTag("SelectProjectButton") and hasAnyAncestor(hasTestTag("ProjectInput")),
            useUnmergedTree = true,
        ).assertExists()
        composeTestRule.onNode(
            hasTestTag("SelectProjectButton") and hasAnyAncestor(hasTestTag("ProjectInput")),
            useUnmergedTree = true,
        ).performClick()
        composeTestRule.onNodeWithTag("ProjectPickerOption-Work.Client").performClick()
        composeTestRule.onNodeWithTag("ProjectPickerConfirmButton").performClick()
        composeTestRule.onNodeWithTag("DescriptionInput").performTextInput("Plan launch")
        composeTestRule.onNodeWithText("Create").performClick()

        composeTestRule.runOnIdle {
            assertEquals("Work.Client", submittedProject)
        }
    }

    @Test
    fun tagPickerSelectionsAreSubmittedWithNewTask() {
        var submittedTags: List<String> = emptyList()

        composeTestRule.setContent {
            AddTaskDialog(
                availableProjects = emptyList(),
                availableTags = listOf("home", "urgent"),
                onDismiss = {},
                onConfirm = { _, _, tags, _, _, _, _, _, _ -> submittedTags = tags },
            )
        }

        composeTestRule.onNodeWithTag("SelectTagsButton").performClick()
        composeTestRule.onNodeWithTag("TagPickerOption-home").performClick()
        composeTestRule.onNodeWithTag("TagPickerOption-urgent").performClick()
        composeTestRule.onNodeWithTag("TagPickerConfirmButton").performClick()
        composeTestRule.onNodeWithTag("DescriptionInput").performTextInput("Plan launch")
        composeTestRule.onNodeWithText("Create").performClick()

        composeTestRule.runOnIdle {
            assertEquals(setOf("home", "urgent"), submittedTags.toSet())
        }
    }

    @Test
    fun projectPickerCancellationKeepsCurrentProject() {
        var submittedProject: String? = null

        composeTestRule.setContent {
            AddTaskDialog(
                availableProjects = listOf("Legacy", "Work.Client"),
                availableTags = emptyList(),
                initialProject = "Legacy",
                onDismiss = {},
                onConfirm = { _, project, _, _, _, _, _, _, _ -> submittedProject = project },
            )
        }

        composeTestRule.onNodeWithTag("SelectProjectButton").performClick()
        composeTestRule.onNodeWithTag("ProjectPickerOption-Work.Client").performClick()
        composeTestRule.onNodeWithTag("ProjectPickerCancelButton").performClick()
        composeTestRule.onNodeWithTag("DescriptionInput").performTextInput("Plan launch")
        composeTestRule.onNodeWithText("Create").performClick()

        composeTestRule.runOnIdle {
            assertEquals("Legacy", submittedProject)
        }
    }
}
