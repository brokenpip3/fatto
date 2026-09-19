package com.brokenpip3.fatto.ui.common

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StringSelectionDialogTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun projectPickerSearchesAndConfirmsOneValue() {
        var confirmed: String? = null
        composeTestRule.setContent {
            ProjectPickerDialog(
                projects = listOf("Work.Client", "Home", "Work"),
                selectedProject = "Work",
                onDismiss = {},
                onConfirm = { confirmed = it },
            )
        }

        composeTestRule.onNodeWithTag("ProjectPickerSearch").performTextInput("client")
        composeTestRule.onNodeWithTag("ProjectPickerOption-Work.Client").performClick()
        composeTestRule.onNodeWithTag("ProjectPickerConfirmButton").performClick()

        composeTestRule.runOnIdle { assertEquals("Work.Client", confirmed) }
    }

    @Test
    fun projectPickerDisablesConfirmationWhenNothingIsSelected() {
        composeTestRule.setContent {
            ProjectPickerDialog(
                projects = listOf("Work"),
                selectedProject = null,
                onDismiss = {},
                onConfirm = {},
            )
        }

        composeTestRule.onNodeWithTag("ProjectPickerConfirmButton").assertIsNotEnabled()
    }

    @Test
    fun projectPickerIncludesSelectedValueMissingFromCandidates() {
        var confirmed: String? = null
        composeTestRule.setContent {
            ProjectPickerDialog(
                projects = listOf("Current"),
                selectedProject = "Legacy",
                onDismiss = {},
                onConfirm = { confirmed = it },
            )
        }

        composeTestRule.onNodeWithTag("ProjectPickerOption-Legacy").assertExists()
        composeTestRule.onNodeWithTag("ProjectPickerConfirmButton").performClick()

        composeTestRule.runOnIdle { assertEquals("Legacy", confirmed) }
    }

    @Test
    fun projectPickerSortsOptionsCaseInsensitively() {
        composeTestRule.setContent {
            ProjectPickerDialog(
                projects = listOf("zebra", "Apple", "banana"),
                selectedProject = null,
                onDismiss = {},
                onConfirm = {},
            )
        }

        val appleTop = composeTestRule.onNodeWithTag("ProjectPickerOption-Apple").fetchSemanticsNode().boundsInRoot.top
        val bananaTop = composeTestRule.onNodeWithTag("ProjectPickerOption-banana").fetchSemanticsNode().boundsInRoot.top
        val zebraTop = composeTestRule.onNodeWithTag("ProjectPickerOption-zebra").fetchSemanticsNode().boundsInRoot.top

        assertTrue(appleTop < bananaTop && bananaTop < zebraTop)
    }

    @Test
    fun projectPickerShowsEmptySearchMessage() {
        composeTestRule.setContent {
            ProjectPickerDialog(
                projects = listOf("Work"),
                selectedProject = null,
                onDismiss = {},
                onConfirm = {},
            )
        }

        composeTestRule.onNodeWithTag("ProjectPickerSearch").performTextInput("none")

        composeTestRule.onNodeWithText("No projects found").assertExists()
    }

    @Test
    fun projectRowsExposeRadioButtonSelectionSemantics() {
        composeTestRule.setContent {
            ProjectPickerDialog(
                projects = listOf("Home", "Work"),
                selectedProject = "Home",
                onDismiss = {},
                onConfirm = {},
            )
        }

        composeTestRule.onNodeWithTag("ProjectPickerOption-Home")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsSelected()
        composeTestRule.onNodeWithTag("ProjectPickerOption-Work")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsNotSelected()
    }

    @Test
    fun tagPickerConfirmsMultipleValues() {
        var confirmed: Set<String>? = null
        composeTestRule.setContent {
            TagPickerDialog(
                tags = listOf("home", "urgent", "work"),
                selectedTags = setOf("home"),
                onDismiss = {},
                onConfirm = { confirmed = it },
            )
        }

        composeTestRule.onNodeWithTag("TagPickerOption-urgent").performClick()
        composeTestRule.onNodeWithTag("TagPickerConfirmButton").performClick()

        composeTestRule.runOnIdle { assertEquals(setOf("home", "urgent"), confirmed) }
    }

    @Test
    fun tagPickerAllowsClearingAllSelections() {
        var confirmed: Set<String>? = null
        composeTestRule.setContent {
            TagPickerDialog(
                tags = listOf("home"),
                selectedTags = setOf("home"),
                onDismiss = {},
                onConfirm = { confirmed = it },
            )
        }

        composeTestRule.onNodeWithTag("TagPickerOption-home").performClick()
        composeTestRule.onNodeWithTag("TagPickerConfirmButton").assertIsEnabled().performClick()

        composeTestRule.runOnIdle { assertEquals(emptySet<String>(), confirmed) }
    }

    @Test
    fun tagPickerCancelDoesNotConfirm() {
        var confirmed: Set<String>? = null
        var dismissed = false
        composeTestRule.setContent {
            TagPickerDialog(
                tags = listOf("home", "urgent"),
                selectedTags = setOf("home"),
                onDismiss = { dismissed = true },
                onConfirm = { confirmed = it },
            )
        }

        composeTestRule.onNodeWithTag("TagPickerOption-urgent").performClick()
        composeTestRule.onNodeWithText("Cancel").performClick()

        composeTestRule.runOnIdle {
            assertTrue(dismissed)
            assertEquals(null, confirmed)
        }
    }

    @Test
    fun tagPickerIncludesSelectedValueMissingFromCandidates() {
        composeTestRule.setContent {
            TagPickerDialog(
                tags = listOf("current"),
                selectedTags = setOf("legacy"),
                onDismiss = {},
                onConfirm = {},
            )
        }

        composeTestRule.onNodeWithTag("TagPickerOption-legacy").assertExists()
    }

    @Test
    fun tagRowsExposeCheckboxToggleSemantics() {
        composeTestRule.setContent {
            TagPickerDialog(
                tags = listOf("home", "urgent"),
                selectedTags = setOf("home"),
                onDismiss = {},
                onConfirm = {},
            )
        }

        composeTestRule.onNodeWithTag("TagPickerOption-home")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assertIsOn()
        composeTestRule.onNodeWithTag("TagPickerOption-urgent")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assertIsOff()
    }

    @Test
    fun tagPickerShowsEmptySearchMessage() {
        composeTestRule.setContent {
            TagPickerDialog(
                tags = listOf("home"),
                selectedTags = emptySet(),
                onDismiss = {},
                onConfirm = {},
            )
        }

        composeTestRule.onNodeWithTag("TagPickerSearch").performTextInput("none")

        composeTestRule.onNodeWithText("No tags found").assertExists()
        composeTestRule.onNodeWithTag("TagPickerOption-home").assertDoesNotExist()
    }
}
