package com.brokenpip3.fatto.ui.tasklist

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.brokenpip3.fatto.ui.common.LinkedTaskText
import com.brokenpip3.fatto.ui.common.TaskEditorLinks
import com.brokenpip3.fatto.ui.common.taskTextLinks
import com.brokenpip3.fatto.ui.theme.NordicTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TaskLinksTest {
    @get:Rule val compose = createComposeRule()

    @Test fun detectsMultipleWebLinksWithoutTrailingPunctuation() {
        val text = "Read https://example.com/spec, then https://example.org/notes."
        val links = taskTextLinks(text)
        assertEquals(listOf("https://example.com/spec", "https://example.org/notes"), links.map { it.url })
        assertEquals(links.map { it.url }, links.map { text.substring(it.start, it.end) })
        assertTrue(taskTextLinks("A note without links").isEmpty())
        assertTrue(taskTextLinks("javascript:alert(1)").isEmpty())
    }

    @Test fun inlineLinkOpensItsDestination() {
        var opened: String? = null
        compose.setContent {
            CompositionLocalProvider(
                LocalUriHandler provides
                    object : UriHandler {
                        override fun openUri(uri: String) {
                            opened = uri
                        }
                    },
            ) {
                NordicTheme { LinkedTaskText("https://example.com") }
            }
        }
        compose.onNodeWithText("https://example.com").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals("https://example.com", opened) }
    }

    @Test fun editorLinkUsesAnExplicitAction() {
        var opened: String? = null
        compose.setContent {
            CompositionLocalProvider(
                LocalUriHandler provides
                    object : UriHandler {
                        override fun openUri(uri: String) {
                            opened = uri
                        }
                    },
            ) {
                NordicTheme { TaskEditorLinks("See https://example.com/spec") }
            }
        }
        compose.onNodeWithText("Open link").performClick()
        compose.runOnIdle { assertEquals("https://example.com/spec", opened) }
    }
}
