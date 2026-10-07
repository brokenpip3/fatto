package com.brokenpip3.fatto.ui.tasklist

import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.ui.assertTextFits
import com.brokenpip3.fatto.ui.captureLayoutScreenshot
import com.brokenpip3.fatto.ui.theme.NordicTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.taskchampion_android.TaskStatus

@RunWith(AndroidJUnit4::class)
class TaskFormLayoutTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private var originalFontScale: Float? = null

    @After
    fun restoreDeviceFontScale() {
        originalFontScale?.let { changeFontScale(it) }
    }

    @Test
    fun createFormKeepsEmptyDateControlsTogether() {
        composeTestRule.setContent {
            NordicTheme {
                AddTaskDialog(
                    availableProjects = emptyList(),
                    availableTags = emptyList(),
                    onDismiss = {},
                    onConfirm = { _, _, _, _, _, _, _, _, _, _ -> },
                )
            }
        }
        assertDateIconsShareRow()
        captureLayoutScreenshot("create-normal")
    }

    @Test
    fun editFormKeepsEmptyDateControlsTogether() {
        showEditor()
        assertDateIconsShareRow()
        captureLayoutScreenshot("edit-normal")
    }

    @Test
    fun editTagActionsRemainAdjacent() {
        showEditor()
        composeTestRule.onNodeWithTag("SelectTagsButton").performScrollTo()
        val add = composeTestRule.onNodeWithTag("AddTagButton").fetchSemanticsNode().boundsInRoot
        val select = composeTestRule.onNodeWithTag("SelectTagsButton").fetchSemanticsNode().boundsInRoot
        assertEquals("Tag actions should share a row", add.center.y, select.center.y, 1f)
    }

    @Test
    fun editorHeaderKeepsStartOnTheRight() {
        showEditor()
        val title = composeTestRule.onNodeWithText("Edit Task").fetchSemanticsNode().boundsInRoot
        val start = composeTestRule.onNodeWithContentDescription("Start").fetchSemanticsNode().boundsInRoot
        assertEquals("Start belongs beside the title", title.center.y, start.center.y, 2f)
        assertTrue("Start belongs to the right", start.left >= title.right)
        composeTestRule.onNodeWithContentDescription("Start").performClick()
        val stop = composeTestRule.onNodeWithContentDescription("Stop").fetchSemanticsNode().boundsInRoot
        assertEquals("Stop belongs beside the title", title.center.y, stop.center.y, 2f)
        assertTrue("Stop belongs to the right", stop.left >= title.right)
        captureLayoutScreenshot("editor-header")
    }

    @Test
    fun editorTagActionsStayInsideTheField() {
        showEditor()
        composeTestRule.onNodeWithTag("TagInput").performScrollTo()
        val field = composeTestRule.onNodeWithTag("TagInput").fetchSemanticsNode().boundsInRoot
        listOf("AddTagButton", "SelectTagsButton").forEach { tag ->
            val button = composeTestRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue("Tag action belongs inside the field", button.top >= field.top && button.bottom <= field.bottom)
        }
        captureLayoutScreenshot("editor-tags-inline")
    }

    @Test
    fun dateControlsUseTheAvailableWidthAndLargerIcons() {
        composeTestRule.setContent {
            NordicTheme {
                Box(Modifier.width(312.dp).testTag("DateArea")) {
                    TaskDateAndPriorityControls(null, null, null, null, {}, {})
                }
            }
        }
        val area = composeTestRule.onNodeWithTag("DateArea").fetchSemanticsNode().boundsInRoot
        val priority =
            composeTestRule.onNodeWithContentDescription("Set Priority", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
        val wait =
            composeTestRule.onNode(hasContentDescription("Wait") and hasParent(hasClickAction()), useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
        val iconSize = with(composeTestRule.density) { 32.dp.toPx() }
        assertTrue("Date icons should be larger", priority.width >= iconSize - 1f)
        assertTrue("Date controls should use the available width", wait.center.x - priority.center.x >= area.width * 0.75f)
    }

    @Test
    fun largeTextEditorKeepsControlsReachable() {
        showEditor(fontScale = 2f)
        val title = composeTestRule.onNodeWithText("Edit Task").fetchSemanticsNode().boundsInRoot
        val start = composeTestRule.onNodeWithContentDescription("Start").fetchSemanticsNode().boundsInRoot
        assertEquals("Large text keeps Start beside the title", title.center.y, start.center.y, 2f)
        assertTrue("Large text header must not overlap", start.left >= title.right)
        captureLayoutScreenshot("edit-large-initial")
        composeTestRule.onNodeWithTag("TaskDetailBottomSheet").performTouchInput { swipeUp() }
        listOf("Due", "Sch", "Wait").forEach { label ->
            composeTestRule.onNode(hasClickAction() and hasContentDescription(label)).performScrollTo().assertIsDisplayed()
        }
        composeTestRule.onNodeWithTag("SelectTagsButton").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("AddTagButton").assertIsDisplayed()
        val field = composeTestRule.onNodeWithTag("TagInput").fetchSemanticsNode().boundsInRoot
        val select = composeTestRule.onNodeWithTag("SelectTagsButton").fetchSemanticsNode().boundsInRoot
        assertTrue("Large text tag actions remain inside the field", select.top >= field.top && select.bottom <= field.bottom)
        captureLayoutScreenshot("edit-large")
    }

    @Test
    fun maximumAppFontWithLargeAndroidTextKeepsEditorReachable() {
        showEditor(fontScale = 2f, fontSizePercent = 150)
        composeTestRule.onNodeWithTag("TaskDetailBottomSheet").performTouchInput { swipeUp() }
        composeTestRule.onNodeWithTag("SelectTagsButton").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("AddTagButton").assertIsDisplayed()
        assertTextFits(composeTestRule.onNodeWithText("Add Tag", useUnmergedTree = true))
        captureLayoutScreenshot("font-editor-combined")
    }

    @Test
    fun largeTextCreationKeepsControlsAndConfirmationReachable() {
        changeFontScale(2f)
        composeTestRule.setContent {
            NordicTheme(darkTheme = true) {
                AddTaskDialog(
                    availableProjects = emptyList(),
                    availableTags = emptyList(),
                    initialDescription = "Review layout with large text",
                    onDismiss = {},
                    onConfirm = { _, _, _, _, _, _, _, _, _, _ -> },
                )
            }
        }
        listOf("Due", "Sch", "Wait").forEach { label ->
            composeTestRule.onNode(hasClickAction() and hasContentDescription(label)).performScrollTo().assertIsDisplayed()
        }
        composeTestRule.onNodeWithTag("OpenEditorAfterCreateSwitch").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Create").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
        captureLayoutScreenshot("create-large")
    }

    @Test
    fun populatedDatesAndTagActionsFitNarrowWidthsWithLargeText() {
        val configuration = mutableStateOf(Triple(216, 1f, false))
        val pickedDates = mutableListOf<DatePickerType>()
        composeTestRule.setContent {
            val (width, fontScale, dark) = configuration.value
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                NordicTheme(darkTheme = dark) {
                    Box(Modifier.width(width.dp).testTag("FormControls")) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TaskDateAndPriorityControls(
                                priority = null,
                                due = "2026-10-06T00:00:00Z",
                                scheduled = "2026-10-07T00:00:00Z",
                                wait = "2026-10-08T00:00:00Z",
                                onPriorityChange = {},
                                onDateClick = { pickedDates.add(it) },
                            )
                            TaskTagActions(onAdd = {}, onSelect = {}, addActionModifier = Modifier.testTag("AddTagButton"))
                        }
                    }
                }
            }
        }
        for (width in listOf(216, 264, 312)) {
            for (scale in listOf(1f, 1.5f, 2f)) {
                for (dark in listOf(false, true)) {
                    composeTestRule.runOnIdle { configuration.value = Triple(width, scale, dark) }
                    val container = composeTestRule.onNodeWithTag("FormControls").fetchSemanticsNode().boundsInRoot
                    val dates =
                        listOf("Due, 2026-10-06", "Sch, 2026-10-07", "Wait, 2026-10-08").map { label ->
                            composeTestRule.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
                        }
                    dates.forEach { bounds ->
                        assertTrue(
                            "Date control must fit at $width dp / $scale",
                            bounds.width > 0f && container.contains(bounds.topLeft) && bounds.right <= container.right + 1f,
                        )
                    }
                    dates.zipWithNext().forEach { (first, second) ->
                        assertFalse("Date controls must not overlap", first.overlaps(second))
                        if (kotlin.math.abs(first.top - second.top) < 1f) {
                            val minimumGap = with(composeTestRule.density) { 8.dp.toPx() }
                            assertTrue("Date labels need a readable gap", second.left - first.right >= minimumGap - 1f)
                        }
                    }
                    listOf("2026-10-06", "2026-10-07", "2026-10-08").forEach { date ->
                        assertTextFits(composeTestRule.onNodeWithText(date, useUnmergedTree = true))
                    }
                    val add = composeTestRule.onNodeWithTag("AddTagButton").fetchSemanticsNode().boundsInRoot
                    val select = composeTestRule.onNodeWithTag("SelectTagsButton").fetchSemanticsNode().boundsInRoot
                    assertEquals("Tag actions should share a row", add.center.y, select.center.y, 1f)
                    assertTrue("Tag actions should be adjacent without overlap", add.right <= select.left)
                    val minTouchSize = with(composeTestRule.density) { 48.dp.toPx() }
                    val addTouch = composeTestRule.onNodeWithTag("AddTagButton").fetchSemanticsNode().touchBoundsInRoot
                    val selectTouch = composeTestRule.onNodeWithTag("SelectTagsButton").fetchSemanticsNode().touchBoundsInRoot
                    assertTrue(
                        "Tag actions retain 48 dp height",
                        addTouch.height >= minTouchSize - 1f && selectTouch.height >= minTouchSize - 1f,
                    )
                    listOf("Due", "Sch", "Wait").forEach { label ->
                        val button = composeTestRule.onNode(hasClickAction() and hasContentDescription(label))
                        val bounds = button.fetchSemanticsNode().touchBoundsInRoot
                        assertTrue(
                            "Date button retains 48 dp bounds",
                            bounds.width >= minTouchSize - 1f && bounds.height >= minTouchSize - 1f,
                        )
                        button.performClick()
                    }
                    composeTestRule.runOnIdle {
                        assertEquals(listOf(DatePickerType.DUE, DatePickerType.SCHEDULED, DatePickerType.WAIT), pickedDates)
                        pickedDates.clear()
                    }
                    if (width == 216 && scale == 2f && dark) captureLayoutScreenshot("populated-dates-large")
                }
            }
        }
    }

    private fun assertDateIconsShareRow() {
        composeTestRule.onNodeWithContentDescription("Set Priority", useUnmergedTree = true).performScrollTo()
        val priority =
            composeTestRule.onNodeWithContentDescription("Set Priority", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
        listOf("Due", "Sch", "Wait").forEach { label ->
            val date =
                composeTestRule.onNode(hasContentDescription(label) and hasParent(hasClickAction()), useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
            assertEquals("$label should share the priority row", priority.center.y, date.center.y, 1f)
        }
    }

    private fun changeFontScale(fontScale: Float) {
        if (originalFontScale == null) originalFontScale = composeTestRule.activity.resources.configuration.fontScale
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand("settings put system font_scale $fontScale"),
        ).use { it.readBytes() }
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { composeTestRule.activity.resources.configuration.fontScale == fontScale }.getOrDefault(false)
        }
    }

    private fun showEditor(
        fontScale: Float = 1f,
        fontSizePercent: Int = 100,
    ) {
        if (fontScale != 1f) changeFontScale(fontScale)
        composeTestRule.setContent {
            NordicTheme(darkTheme = fontScale > 1f, fontSizePercent = fontSizePercent) {
                TaskDetailBottomSheet(
                    task =
                        Task(
                            uuid = "layout-test",
                            description = "Review layout",
                            status = TaskStatus.PENDING,
                            tags = emptyList(),
                            due = null,
                            entry = null,
                            project = null,
                            wait = null,
                            scheduled = null,
                            start = null,
                            priority = null,
                            urgency = 0f,
                            isBlocked = false,
                            isBlocking = false,
                            dependencies = emptyList(),
                            udas = emptyMap(),
                        ),
                    onDismiss = {},
                    onSave = {},
                    availableProjects = emptyList(),
                    availableTags = emptyList(),
                )
            }
        }
    }
}
