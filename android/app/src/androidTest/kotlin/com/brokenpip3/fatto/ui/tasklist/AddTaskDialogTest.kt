package com.brokenpip3.fatto.ui.tasklist

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
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
}
