package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.TaskRepository
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.TaskContext
import com.brokenpip3.fatto.data.model.TaskEditResult
import com.brokenpip3.fatto.data.model.calendarDateModes
import com.brokenpip3.fatto.data.model.toEditPatch
import com.brokenpip3.fatto.vm.TaskViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import uniffi.taskchampion_android.TaskAddProps
import uniffi.taskchampion_android.TaskStatus

@OptIn(ExperimentalCoroutinesApi::class)
class TaskContextViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<TaskRepository>(relaxed = true)
    private val tasksFlow = MutableStateFlow<List<Task>>(emptyList())
    private val contextsFlow = MutableStateFlow<List<TaskContext>>(emptyList())
    private val activeContextIdFlow = MutableStateFlow<String?>(null)
    private val showCompletedFlow = MutableStateFlow(false)
    private val autoWaitingFlow = MutableStateFlow(false)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { repository.tasks } returns tasksFlow
        every { repository.showCompleted } returns showCompletedFlow
        every { repository.hideBlockedTasksWaiting } returns MutableStateFlow(false)
        every { repository.showWaitingTasks } returns MutableStateFlow(true)
        every { repository.sortOrder } returns MutableStateFlow("DATE_CREATED")
        every { repository.sortDirection } returns MutableStateFlow("")
        every { repository.taskContexts } returns contextsFlow
        every { repository.activeTaskContextId } returns activeContextIdFlow
        every { repository.autoWaiting } returns autoWaitingFlow
        coEvery {
            repository.addTask(any<TaskAddProps>())
        } returns task(uuid = "added", description = "Added")
        coEvery { repository.updateTask(any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `clock updates waiting context membership without task mutations`() =
        runTest {
            val source = FixedTaskTimeSource(java.time.Instant.parse("2030-01-01T12:00:00Z"))
            val context = TaskContext(id = "ready", name = "Ready", expressionText = "-WAITING")
            contextsFlow.value = listOf(context)
            activeContextIdFlow.value = context.id
            tasksFlow.value = listOf(task(uuid = "expires", description = "Expires").copy(wait = "2030-01-01T12:00:10Z"))
            val viewModel = TaskViewModel(repository, source)
            val job = launch { viewModel.activeTasks.collect {} }
            advanceUntilIdle()
            assertEquals(emptyList<Task>(), viewModel.activeTasks.value)
            source.snapshots.value = source.snapshots.value.copy(now = java.time.Instant.parse("2030-01-01T12:00:11Z"))
            advanceUntilIdle()
            assertEquals(listOf("expires"), viewModel.activeTasks.value.map { it.uuid })
            coVerify(exactly = 0) { repository.updateTask(any()) }
            viewModel.refreshTaskTime()
            assertEquals(1, source.refreshes)
            job.cancel()
        }

    @Test
    fun `clock updates selected WAITING tags search tags and counts without a repository emission`() =
        runTest {
            every { repository.showInternalTags } returns MutableStateFlow(true)
            for (search in listOf(false, true)) {
                val source = FixedTaskTimeSource(java.time.Instant.parse("2030-01-01T12:00:00Z"))
                val original =
                    task(uuid = "expires", description = "Expires").copy(
                        wait = "2030-01-01T12:00:10Z", tags = listOf("WAITING", "user"),
                    )
                tasksFlow.value = listOf(original)
                val viewModel = TaskViewModel(repository, source)
                if (search) viewModel.onSearchQueryChange("tags:WAITING") else viewModel.toggleTag("WAITING")
                val jobs =
                    listOf(
                        launch { viewModel.activeTasks.collect {} }, launch { viewModel.waitingTasks.collect {} },
                        launch { viewModel.availableTags.collect {} }, launch { viewModel.tagCounts.collect {} },
                    )
                advanceUntilIdle()
                assertEquals(listOf("expires"), viewModel.waitingTasks.value.map { it.uuid })
                assertEquals(1, viewModel.tagCounts.value["WAITING"])
                assertTrue("WAITING" in viewModel.availableTags.value)
                source.snapshots.value = source.snapshots.value.copy(now = java.time.Instant.parse("2030-01-01T12:00:11Z"))
                advanceUntilIdle()
                assertEquals(emptyList<Task>(), viewModel.waitingTasks.value)
                assertEquals(emptyList<Task>(), viewModel.activeTasks.value)
                assertNull(viewModel.tagCounts.value["WAITING"])
                assertTrue("WAITING" !in viewModel.availableTags.value)
                assertEquals(1, viewModel.tagCounts.value["user"])
                assertEquals(listOf(original), tasksFlow.value)
                coVerify(exactly = 0) { repository.updateTask(any()) }
                jobs.forEach { it.cancel() }
            }
        }

    @Test
    fun `calendar grouping follows marked zone changes while legacy dates stay fixed`() =
        runTest {
            val source = FixedTaskTimeSource(java.time.Instant.parse("2026-04-28T12:00:00Z"), java.time.ZoneId.of("Europe/Rome"))
            val legacy = task(uuid = "legacy", description = "Legacy").copy(due = "2026-04-28T22:30:45Z")
            val marked =
                legacy.copy(
                    uuid = "timed",
                    dateModes =
                        calendarDateModes().copy(
                            due = uniffi.taskchampion_android.DateDisplayMode.LOCAL_DATE_TIME,
                        ),
                )
            tasksFlow.value = listOf(legacy, marked)
            val viewModel = TaskViewModel(repository, source)
            val job = launch { viewModel.tasksByDate.collect {} }
            advanceUntilIdle()
            assertEquals(listOf("legacy"), viewModel.tasksByDate.value[java.time.LocalDate.parse("2026-04-28")]?.map { it.uuid })
            assertEquals(listOf("timed"), viewModel.tasksByDate.value[java.time.LocalDate.parse("2026-04-29")]?.map { it.uuid })
            source.snapshots.value = source.snapshots.value.copy(zone = java.time.ZoneId.of("America/New_York"))
            advanceUntilIdle()
            assertEquals(listOf("legacy", "timed"), viewModel.tasksByDate.value[java.time.LocalDate.parse("2026-04-28")]?.map { it.uuid })
            assertNull(viewModel.tasksByDate.value[java.time.LocalDate.parse("2026-04-29")])
            job.cancel()
        }

    @Test
    fun `explicit clear Wait suppresses automatic Wait during creation`() =
        runTest {
            autoWaitingFlow.value = true
            val props =
                TaskAddProps(
                    "No wait", null, emptyList(), "2030-01-02T00:00:00Z", null, null, null, null, emptyList(),
                    calendarDateModes(),
                )
            coEvery { repository.addTask(props) } returns task(uuid = "added", description = "No wait")
            val viewModel = TaskViewModel(repository, FixedTaskTimeSource())
            assertEquals(TaskEditResult.Success, viewModel.createTask(props, null, false, explicitDateClears = setOf("wait")))
            coVerify(exactly = 1) { repository.addTask(props) }
        }

    @Test
    fun `checked creation retains modes in ordinary repository path`() =
        runTest {
            val props =
                TaskAddProps(
                    "Timed", null, emptyList(), "2030-01-02T00:00:00Z", null, null, null, null, emptyList(),
                    calendarDateModes().copy(due = uniffi.taskchampion_android.DateDisplayMode.LOCAL_DATE_TIME),
                )
            coEvery { repository.addTask(props) } returns task(uuid = "added", description = "Timed")
            val viewModel = TaskViewModel(repository, FixedTaskTimeSource())
            assertEquals(TaskEditResult.Success, viewModel.createTask(props, null, false))
            coVerify(exactly = 1) { repository.addTask(props) }
        }

    @Test
    fun `checked creation reports an add task error`() =
        runTest {
            coEvery { repository.addTask(any<TaskAddProps>()) } throws
                IllegalArgumentException("Invalid tag")
            val viewModel = TaskViewModel(repository, FixedTaskTimeSource())
            val props =
                TaskAddProps(
                    "Added", null, listOf("bad tag"), null, null, null, null, null, emptyList(),
                    dateModes = calendarDateModes(),
                )
            val result = viewModel.createTask(props, null, false)
            assertEquals(TaskEditResult.Failure("Failed to add task: Invalid tag"), result)
        }

    @Test
    fun `checked creation returns while a previous creation snackbar is still displayed`() =
        runTest {
            val viewModel = TaskViewModel(repository, FixedTaskTimeSource())
            val release = CompletableDeferred<Unit>()
            val collector =
                launch(testDispatcher) {
                    viewModel.taskCreatedEvent.collect { release.await() }
                }
            advanceUntilIdle()
            val props =
                TaskAddProps(
                    "Added", null, emptyList(), null, null, null, null, null, emptyList(),
                    dateModes = calendarDateModes(),
                )
            val first = async { viewModel.createTask(props, null, false) }
            advanceUntilIdle()
            assertTrue(first.isCompleted)
            val second = async { viewModel.createTask(props, null, false) }
            advanceUntilIdle()
            try {
                assertTrue("A displayed snackbar must not block committing or closing another creation", second.isCompleted)
                assertEquals(TaskEditResult.Success, second.await())
            } finally {
                release.complete(Unit)
                collector.cancel()
            }
        }

    @Test
    fun `sync failure emits sanitized repository error`() =
        runTest {
            coEvery { repository.sync() } throws IllegalStateException("private sync secret")
            every { repository.safeSyncError(any()) } returns "[redacted]"
            val viewModel = TaskViewModel(repository, FixedTaskTimeSource())
            val event = async { viewModel.uiEvent.first() }

            viewModel.sync()
            advanceUntilIdle()

            assertEquals("Sync failed: [redacted]", event.await())
        }

    @Test
    fun `active context filters active tasks`() =
        runTest {
            val context = TaskContext(id = "work", name = "Work", expressionText = "project:Work +office")
            contextsFlow.value = listOf(context)
            activeContextIdFlow.value = context.id
            tasksFlow.value =
                listOf(
                    task(uuid = "match", description = "Call client", project = "Work.Mobile", tags = listOf("office")),
                    task(uuid = "wrong-project", description = "Call plumber", project = "Home", tags = listOf("office")),
                    task(uuid = "wrong-tag", description = "Call vendor", project = "Work", tags = listOf("remote")),
                )
            val viewModel = TaskViewModel(repository, FixedTaskTimeSource())

            val job = launch { viewModel.activeTasks.collect {} }
            advanceUntilIdle()

            assertEquals(listOf("match"), viewModel.activeTasks.value.map { it.uuid })
            job.cancel()
        }

    @Test
    fun `active context combines with normal search`() =
        runTest {
            val context = TaskContext(id = "calls", name = "Calls", expressionText = "project:Work")
            contextsFlow.value = listOf(context)
            activeContextIdFlow.value = context.id
            tasksFlow.value =
                listOf(
                    task(uuid = "call", description = "Call client", project = "Work"),
                    task(uuid = "email", description = "Email client", project = "Work"),
                    task(uuid = "home", description = "Call plumber", project = "Home"),
                )
            val viewModel = TaskViewModel(repository, FixedTaskTimeSource())

            viewModel.onSearchQueryChange("call")
            val job = launch { viewModel.activeTasks.collect {} }
            advanceUntilIdle()

            assertEquals(listOf("call"), viewModel.activeTasks.value.map { it.uuid })
            job.cancel()
        }

    @Test
    fun `active context exposes selected context`() =
        runTest {
            val context = TaskContext(id = "work", name = "Work")
            contextsFlow.value = listOf(context)
            activeContextIdFlow.value = context.id
            val viewModel = TaskViewModel(repository, FixedTaskTimeSource())

            val job = launch { viewModel.activeTaskContext.collect {} }
            advanceUntilIdle()

            assertEquals(context, viewModel.activeTaskContext.value)

            activeContextIdFlow.value = "missing"
            advanceUntilIdle()

            assertNull(viewModel.activeTaskContext.value)
            job.cancel()
        }

    @Test
    fun `invalid active context matches no tasks and exposes error`() =
        runTest {
            val context = TaskContext(id = "bad", name = "Bad", expressionText = "priority:H")
            contextsFlow.value = listOf(context)
            activeContextIdFlow.value = context.id
            tasksFlow.value = listOf(task(uuid = "task", description = "Task"))
            val viewModel = TaskViewModel(repository, FixedTaskTimeSource())

            val job = launch { viewModel.activeTasks.collect {} }
            val errorJob = launch { viewModel.activeTaskContextError.collect {} }
            advanceUntilIdle()

            assertEquals(emptyList<String>(), viewModel.activeTasks.value.map { it.uuid })
            assertTrue(viewModel.activeTaskContextError.value.orEmpty().contains("Unsupported attribute"))
            job.cancel()
            errorJob.cancel()
        }

    @Test
    fun `active-only toggle filters tasks currently being worked on`() =
        runTest {
            showCompletedFlow.value = true
            tasksFlow.value =
                listOf(
                    task(uuid = "started", description = "Started task", start = "2026-06-28T10:00:00Z"),
                    task(uuid = "active-tag", description = "Active tag task", tags = listOf("ACTIVE")),
                    task(
                        uuid = "completed-started",
                        description = "Completed started task",
                        status = TaskStatus.Completed,
                        start = "2026-06-28T10:00:00Z",
                    ),
                    task(uuid = "pending", description = "Pending task"),
                )
            val viewModel = TaskViewModel(repository, FixedTaskTimeSource())

            val job = launch { viewModel.activeTasks.collect {} }
            val completedJob = launch { viewModel.completedTasks.collect {} }
            advanceUntilIdle()

            assertEquals(listOf("started", "active-tag", "pending"), viewModel.activeTasks.value.map { it.uuid })
            assertEquals(listOf("completed-started"), viewModel.completedTasks.value.map { it.uuid })

            viewModel.toggleShowOnlyActiveTasks()
            advanceUntilIdle()

            assertEquals(listOf("started", "active-tag"), viewModel.activeTasks.value.map { it.uuid })
            assertEquals(emptyList<String>(), viewModel.completedTasks.value.map { it.uuid })

            viewModel.toggleShowOnlyActiveTasks()
            advanceUntilIdle()

            assertEquals(listOf("started", "active-tag", "pending"), viewModel.activeTasks.value.map { it.uuid })
            assertEquals(listOf("completed-started"), viewModel.completedTasks.value.map { it.uuid })
            job.cancel()
            completedJob.cancel()
        }

    @Test
    fun `urgency bar max remains based on all tasks while filtered`() =
        runTest {
            tasksFlow.value =
                listOf(
                    task(uuid = "low", description = "Low urgency", project = "Work", urgency = 2f),
                    task(uuid = "high", description = "High urgency", project = "Home", urgency = 10f),
                )
            val viewModel = TaskViewModel(repository, FixedTaskTimeSource())

            viewModel.setActiveProject("Work")
            val job = launch { viewModel.activeTasks.collect {} }
            val maxJob = launch { viewModel.maxUrgency.collect {} }
            advanceUntilIdle()

            assertEquals(listOf("low"), viewModel.activeTasks.value.map { it.uuid })
            assertEquals(10f, viewModel.maxUrgency.value)
            job.cancel()
            maxJob.cancel()
        }

    @Test
    fun `context actions delegate to repository`() {
        val context = TaskContext(id = "work", name = "Work")
        val viewModel = TaskViewModel(repository, FixedTaskTimeSource())

        viewModel.saveTaskContext(context)
        viewModel.deleteTaskContext(context.id)
        viewModel.setActiveTaskContextId(context.id)
        viewModel.setActiveTaskContextId(null)

        verify { repository.saveTaskContext(context) }
        verify { repository.deleteTaskContext(context.id) }
        verify { repository.setActiveTaskContextId(context.id) }
        verify { repository.setActiveTaskContextId(null) }
    }

    @Test
    fun `add task applies auto wait one week before due when enabled and wait is empty`() =
        runTest {
            autoWaitingFlow.value = true
            val viewModel = TaskViewModel(repository, FixedTaskTimeSource())

            viewModel.addTask(
                description = "Prepare release",
                project = null,
                tags = emptyList(),
                wait = null,
                due = "2030-08-15T00:00:00Z",
                scheduled = null,
            )
            advanceUntilIdle()

            coVerify {
                repository.addTask(
                    "Prepare release",
                    null,
                    emptyList(),
                    "2030-08-08T00:00:00Z",
                    "2030-08-15T00:00:00Z",
                    null,
                    null,
                    null,
                    emptyList(),
                )
            }
        }

    @Test
    fun `add task does not apply auto wait when setting is disabled`() =
        runTest {
            autoWaitingFlow.value = false
            val viewModel = TaskViewModel(repository, FixedTaskTimeSource())

            viewModel.addTask(
                description = "Visible due task",
                project = null,
                tags = emptyList(),
                wait = null,
                due = "2030-08-15T00:00:00Z",
                scheduled = null,
            )
            advanceUntilIdle()

            coVerify {
                repository.addTask(
                    "Visible due task",
                    null,
                    emptyList(),
                    null,
                    "2030-08-15T00:00:00Z",
                    null,
                    null,
                    null,
                    emptyList(),
                )
            }
        }

    @Test
    fun `add task applies auto wait one week before scheduled when enabled and wait is empty`() =
        runTest {
            autoWaitingFlow.value = true
            val viewModel = TaskViewModel(repository, FixedTaskTimeSource())

            viewModel.addTask(
                description = "Start implementation",
                project = null,
                tags = emptyList(),
                wait = null,
                due = null,
                scheduled = "2030-09-10T00:00:00Z",
            )
            advanceUntilIdle()

            coVerify {
                repository.addTask(
                    "Start implementation",
                    null,
                    emptyList(),
                    "2030-09-03T00:00:00Z",
                    null,
                    "2030-09-10T00:00:00Z",
                    null,
                    null,
                    emptyList(),
                )
            }
        }

    @Test
    fun `add task does not apply auto wait without due or scheduled date`() =
        runTest {
            autoWaitingFlow.value = true
            val viewModel = TaskViewModel(repository, FixedTaskTimeSource())

            viewModel.addTask(
                description = "Plain task",
                project = null,
                tags = emptyList(),
                wait = null,
                due = null,
                scheduled = null,
            )
            advanceUntilIdle()

            coVerify {
                repository.addTask(
                    "Plain task",
                    null,
                    emptyList(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    emptyList(),
                )
            }
        }

    @Test
    fun `add task does not override explicit wait when auto waiting is enabled`() =
        runTest {
            autoWaitingFlow.value = true
            val viewModel = TaskViewModel(repository, FixedTaskTimeSource())

            viewModel.addTask(
                description = "Manual wait",
                project = null,
                tags = emptyList(),
                wait = "2030-08-01T00:00:00Z",
                due = "2030-08-15T00:00:00Z",
                scheduled = null,
            )
            advanceUntilIdle()

            coVerify {
                repository.addTask(
                    "Manual wait",
                    null,
                    emptyList(),
                    "2030-08-01T00:00:00Z",
                    "2030-08-15T00:00:00Z",
                    null,
                    null,
                    null,
                    emptyList(),
                )
            }
        }

    @Test
    fun `update task applies auto wait one week before earliest target when enabled and wait is empty`() =
        runTest {
            autoWaitingFlow.value = true
            val viewModel = TaskViewModel(repository, FixedTaskTimeSource())
            val edited =
                task(
                    uuid = "task-1",
                    description = "Edited task",
                    dates =
                        TaskDates(
                            due = "2030-09-15T00:00:00Z",
                            scheduled = "2030-09-10T00:00:00Z",
                        ),
                )

            val original = edited.copy(due = null, scheduled = null)
            viewModel.saveTask(edited.toEditPatch(original, autoWaiting = true))
            advanceUntilIdle()

            coVerify {
                repository.updateTask(
                    edited.copy(wait = "2030-09-03T00:00:00Z").toEditPatch(original),
                )
            }
        }

    @Test
    fun `update task does not override explicit wait when auto waiting is enabled`() =
        runTest {
            autoWaitingFlow.value = true
            val viewModel = TaskViewModel(repository, FixedTaskTimeSource())
            val edited =
                task(
                    uuid = "task-1",
                    description = "Edited task",
                    dates =
                        TaskDates(
                            wait = "2030-08-01T00:00:00Z",
                            due = "2030-08-15T00:00:00Z",
                        ),
                )

            val original = edited.copy(wait = null, due = null)
            val patch = edited.toEditPatch(original, autoWaiting = true)
            viewModel.saveTask(patch)
            advanceUntilIdle()

            coVerify { repository.updateTask(edited.toEditPatch(original)) }
        }

    @Test
    fun `restore task emits failure event when repository restore fails`() =
        runTest {
            coEvery { repository.restoreTask("task-1") } throws IllegalStateException("disk error")
            val viewModel = TaskViewModel(repository, FixedTaskTimeSource())
            val message = async { viewModel.uiEvent.first() }

            viewModel.restoreTask("task-1")
            advanceUntilIdle()

            assertEquals("Failed to restore task: disk error", message.await())
        }

    private fun task(
        uuid: String,
        description: String,
        status: TaskStatus = TaskStatus.Pending,
        project: String? = null,
        tags: List<String> = emptyList(),
        dates: TaskDates = TaskDates(),
        start: String? = null,
        urgency: Float = 0f,
    ): Task =
        Task(
            uuid = uuid,
            description = description,
            status = status,
            tags = tags,
            project = project,
            entry = "2026-01-01T00:00:00Z",
            wait = dates.wait,
            due = dates.due,
            scheduled = dates.scheduled,
            start = start,
            priority = null,
            urgency = urgency,
            isBlocked = false,
            isBlocking = false,
            dependencies = emptyList(),
            udas = emptyMap(),
        )

    private data class TaskDates(
        val wait: String? = null,
        val due: String? = null,
        val scheduled: String? = null,
    )
}
