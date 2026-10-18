package com.brokenpip3.fatto

import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.brokenpip3.fatto.data.SettingsRepositoryImpl
import com.brokenpip3.fatto.data.TaskDateTime
import com.brokenpip3.fatto.data.TaskDateValue
import com.brokenpip3.fatto.data.TaskRepository
import com.brokenpip3.fatto.data.model.RecurrenceRule
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.calendarDateModes
import com.brokenpip3.fatto.data.model.toEditPatch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.taskchampion_android.DateDisplayMode
import uniffi.taskchampion_android.TaskAddProps
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone
import java.util.UUID

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TaskEditorRecreationTest {
    @get:Rule val dateDisplayConsent = DateDisplayConsentTestRule(accepted = false)

    @get:Rule val compose = createEmptyComposeRule()

    @get:Rule val permission: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "time-recreate-${UUID.randomUUID()}"
    private lateinit var repository: TaskRepository
    private lateinit var previousZone: TimeZone
    private var scenario: ActivityScenario<MainActivity>? = null
    private val clockFields = hasSetTextAction() and hasAnyAncestor(hasTestTag("TaskDateTimeInput"))

    @Before fun setup() =
        runBlocking {
            previousZone = TimeZone.getDefault()
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Rome"))
            repository = TaskRepository(context, SettingsRepositoryImpl(context))
            repository.init()
        }

    @After fun cleanup() {
        scenario?.close()
        TimeZone.setDefault(previousZone)
        runBlocking {
            repository.loadTasks()
            repository.tasks.value.filter { it.description.startsWith(prefix) }.forEach {
                if (it.isRecurrenceTemplate) repository.stopRecurrence(it.uuid) else repository.deleteTask(it.uuid)
            }
        }
    }

    private fun setClock() {
        compose.onAllNodes(clockFields)[0].performTextReplacement("10")
        compose.onAllNodes(clockFields)[1].performTextReplacement("15")
    }

    @Test fun creationRestoresParentDraftClockAndCapturedZoneWithoutSaving() {
        val title = "$prefix create"
        scenario =
            ActivityScenario.launch(
                Intent(Intent.ACTION_SEND).apply {
                    setClass(context, MainActivity::class.java)
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, title)
                },
            )
        compose.waitUntilAtLeastOneExists(hasTestTag("AddTaskDialog"), 10_000)
        compose.onNodeWithTag("DescriptionInput").performTextReplacement("$title edited")
        compose.onNode(hasContentDescription("Due") and hasClickAction()).performScrollTo().performClick()
        val date = LocalDate.now(ZoneOffset.UTC)
        compose.onNodeWithText("Today", substring = true).performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Set time").performScrollTo().performClick()
        compose.onNodeWithText("Continue").performClick()
        setClock()
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        scenario!!.recreate()
        compose.waitUntilAtLeastOneExists(hasTestTag("TaskDateTimeInput"), 10_000)
        compose.onAllNodes(clockFields)[0].assertTextEquals("10")
        compose.onAllNodes(clockFields)[1].assertTextEquals("15")
        compose.onNodeWithText("Europe/Rome", substring = true).assertExists()
        compose.onNodeWithText("Today", substring = true).assertIsSelected()
        runBlocking {
            repository.loadTasks()
            assertTrue(repository.tasks.value.none { it.description.startsWith(prefix) })
        }
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithTag("DescriptionInput").assertTextContains("$title edited")
        compose.onNodeWithText("Create").performClick()
        compose.waitUntilDoesNotExist(hasTestTag("AddTaskDialog"), 10_000)
        runBlocking {
            repository.loadTasks()
            val created = repository.tasks.value.single { it.description == "$title edited" }
            assertEquals("Created due=${created.due}, modes=${created.dateModes}", DateDisplayMode.LOCAL_DATE_TIME, created.dateModes.due)
            val selected = TaskDateTime.decode(TaskDateValue(created.due, created.dateModes.due), ZoneId.of("Europe/Rome"))!!
            assertEquals(date, selected.date)
            assertEquals(LocalTime.of(10, 15), selected.time)
        }
        scenario!!.recreate()
        compose.waitForIdle()
        compose.onNodeWithTag("AddTaskDialog").assertDoesNotExist()
    }

    private fun fixture(series: Boolean): Task =
        runBlocking {
            val props =
                TaskAddProps(
                    description = "$prefix ${if (series) "series" else "detail"}", project = null, tags = emptyList(),
                    due = "2030-04-28T22:30:45Z", wait = null, scheduled = null, start = null,
                    priority = null, dependencies = emptyList(),
                    dateModes = calendarDateModes().copy(due = DateDisplayMode.LOCAL_DATE_TIME),
                )
            if (series) repository.addRecurringTask(props, RecurrenceRule("daily")) else repository.addTask(props)
        }

    private fun launch(task: Task) {
        scenario =
            ActivityScenario.launch(
                Intent(context, MainActivity::class.java).apply {
                    putExtra(com.brokenpip3.fatto.notification.NotificationNavigation.EXTRA_TASK_UUID, task.uuid)
                },
            )
    }

    @Test fun detailRestoresOriginalConflictBaselineInsteadOfOverwritingRemoteDate() {
        val original = fixture(false)
        launch(original)
        compose.waitUntilAtLeastOneExists(hasText(original.description), 10_000)
        compose.onNodeWithText(original.description).performClick()
        compose.onNodeWithTag("TaskDescriptionInput").performTextReplacement("${original.description} edited")
        compose.onNode(hasContentDescription("Due") and hasClickAction()).performScrollTo().performClick()
        setClock()
        runBlocking { repository.updateTask(original.copy(due = "2030-04-30T22:30:45Z").toEditPatch(original)) }
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        scenario!!.recreate()
        compose.waitUntilAtLeastOneExists(hasTestTag("TaskDateTimeInput"), 10_000)
        compose.onAllNodes(clockFields)[0].assertTextEquals("10")
        compose.onAllNodes(clockFields)[1].assertTextEquals("15")
        compose.onNodeWithText("Europe/Rome", substring = true).assertExists()
        runBlocking {
            repository.loadTasks()
            assertEquals(original.description, repository.tasks.value.single { it.uuid == original.uuid }.description)
        }
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithTag("TaskDescriptionInput").assertTextContains("${original.description} edited")
        compose.onNodeWithTag("CloseButton").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Reload latest task"), 10_000)
        runBlocking {
            repository.loadTasks()
            val actual = repository.tasks.value.single { it.uuid == original.uuid }
            assertEquals(Instant.parse("2030-04-30T22:30:45Z"), Instant.parse(actual.due))
            assertEquals(original.description, actual.description)
        }
    }

    @Test fun seriesRestoresScheduleModeClockZoneAndUnrelatedDraft() {
        val original = fixture(true)
        launch(original)
        compose.waitUntilAtLeastOneExists(hasContentDescription("More task options"), 10_000)
        compose.onNodeWithContentDescription("More task options").performClick()
        compose.onNodeWithText("Recurring").performClick()
        compose.onNode(hasText("Edit series") and hasAnySibling(hasText(original.description)))
            .performScrollTo().performClick()
        compose.onNodeWithTag("SeriesDescriptionInput").performTextReplacement("${original.description} edited")
        compose.onNodeWithText("Change schedule").performScrollTo().performClick()
        compose.onNodeWithText("First due date").performScrollTo().performClick()
        setClock()
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        scenario!!.recreate()
        compose.waitUntilAtLeastOneExists(hasTestTag("TaskDateTimeInput"), 10_000)
        compose.onAllNodes(clockFields)[0].assertTextEquals("10")
        compose.onAllNodes(clockFields)[1].assertTextEquals("15")
        compose.onNodeWithText("Europe/Rome", substring = true).assertExists()
        runBlocking {
            repository.loadTasks()
            val unchanged = repository.tasks.value.single { it.uuid == original.uuid }
            assertEquals(original.due, unchanged.due)
            assertEquals(original.description, unchanged.description)
        }
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("Save schedule").performScrollTo().performClick()
        compose.waitUntilDoesNotExist(hasText("Save schedule"), 10_000)
        runBlocking {
            repository.loadTasks()
            val saved = repository.tasks.value.single { it.uuid == original.uuid }
            assertEquals("${original.description} edited", saved.description)
            assertEquals(Instant.parse("2030-04-29T08:15:00Z"), Instant.parse(saved.due))
            assertEquals(DateDisplayMode.LOCAL_DATE_TIME, saved.dateModes.due)
            assertTrue(repository.tasks.value.none { it.recurrence?.parent == original.uuid })
        }
    }

    @Test fun pendingTimeNoticeSurvivesActivityRecreationWithoutAcceptingOrSaving() {
        val title = "$prefix pending notice"
        scenario =
            ActivityScenario.launch(
                Intent(Intent.ACTION_SEND).apply {
                    setClass(context, MainActivity::class.java)
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, title)
                },
            )
        compose.waitUntilAtLeastOneExists(hasTestTag("AddTaskDialog"), 10_000)
        compose.onNodeWithTag("DescriptionInput").performTextReplacement("$title edited")
        compose.onNode(hasContentDescription("Due") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Set time").performScrollTo().performClick()
        compose.onNodeWithText("Task time display").assertExists()
        scenario!!.recreate()
        compose.waitUntilAtLeastOneExists(hasText("Task time display"), 10_000)
        compose.onNode(hasText("Cancel") and hasAnyAncestor(hasTestTag("DateDisplayUdaNotice"))).performClick()
        compose.onNodeWithTag("TaskDatePickerCancel").performClick()
        compose.onNodeWithTag("DescriptionInput").assertTextContains("$title edited")
        assertTrue(!java.io.File(context.noBackupFilesDir, "date-display-uda-notice-v1.accepted").exists())
        runBlocking {
            repository.loadTasks()
            assertTrue(repository.tasks.value.none { it.description.startsWith(prefix) })
        }
    }
}
