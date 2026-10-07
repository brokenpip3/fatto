package com.brokenpip3.fatto.ui.projects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brokenpip3.fatto.ui.assertTextFits
import com.brokenpip3.fatto.ui.captureLayoutScreenshot
import com.brokenpip3.fatto.ui.theme.NordicTheme
import com.brokenpip3.fatto.vm.ProjectNode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectCardLayoutTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun progressStaysBesideProjectDetailsOnPhone() {
        composeTestRule.setContent {
            NordicTheme {
                Box(Modifier.width(288.dp)) {
                    ProjectCard(
                        node = ProjectNode("finance", "finance", 23, 77, 100, 0),
                        hasSubprojects = true,
                        onClick = {},
                    )
                }
            }
        }
        val name =
            composeTestRule.onNodeWithText("finance", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val count = composeTestRule.onNodeWithText("23 pending tasks", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val percentage =
            composeTestRule.onNodeWithText("77%", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Progress should be beside the details", percentage.center.y in name.top..count.bottom)
        assertTrue("Progress should be to the right of the details", percentage.left >= maxOf(name.right, count.right))
        captureLayoutScreenshot("project-normal")
    }

    @Test
    fun longProjectNamesAndPercentagesFitWithLargeText() {
        val configuration = mutableStateOf(Triple(248, 1f, 0))
        val appPercent = mutableStateOf(100)
        composeTestRule.setContent {
            val (width, scale, completed) = configuration.value
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                NordicTheme(darkTheme = completed == 77, fontSizePercent = appPercent.value) {
                    Box(Modifier.width(width.dp)) {
                        ProjectCard(
                            node =
                                ProjectNode(
                                    "Long project name for household finances",
                                    "home.finance",
                                    100 - completed,
                                    completed,
                                    100,
                                    0,
                                ),
                            hasSubprojects = completed != 0,
                            onClick = {},
                        )
                    }
                }
            }
        }
        for (width in listOf(248, 288, 312)) {
            for (scale in listOf(1f, 1.5f, 2f)) {
                for (percent in listOf(100, 150)) {
                    for (completed in listOf(0, 77, 100)) {
                        composeTestRule.runOnIdle {
                            configuration.value = Triple(width, scale, completed)
                            appPercent.value = percent
                        }
                        val range = ProgressBarRangeInfo(completed / 100f, 0f..1f)
                        val ring =
                            composeTestRule.onNode(hasProgressBarRangeInfo(range), useUnmergedTree = true)
                                .fetchSemanticsNode().boundsInRoot
                        val percentage =
                            composeTestRule.onNodeWithText("$completed%", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                        val name =
                            composeTestRule.onNodeWithText("Long project name for household finances", useUnmergedTree = true)
                                .fetchSemanticsNode().boundsInRoot
                        val navigation =
                            composeTestRule.onNodeWithTag("ProjectNavigationSlot", useUnmergedTree = true)
                                .fetchSemanticsNode().boundsInRoot
                        assertTrue("Project text must stay beside the ring", name.right <= ring.left + 1f)
                        assertTrue(
                            "Percentage must fit inside the ring",
                            percentage.left >= ring.left &&
                                percentage.right <= ring.right &&
                                percentage.top >= ring.top &&
                                percentage.bottom <= ring.bottom,
                        )
                        if (completed != 0) {
                            assertTrue("Navigation must stay beside the ring", navigation.left >= ring.right)
                        }
                        assertTextFits(composeTestRule.onNodeWithText("$completed%", useUnmergedTree = true))
                        if (width == 248 && scale == 2f && completed == 77) captureLayoutScreenshot("project-large")
                    }
                }
            }
        }
    }
}
