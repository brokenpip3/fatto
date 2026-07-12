package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.TaskRepository
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.TaskContext
import com.brokenpip3.fatto.vm.TaskViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
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
            repository.addTask(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Unit
        coEvery { repository.updateTask(any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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
            val viewModel = TaskViewModel(repository)

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
            val viewModel = TaskViewModel(repository)

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
            val viewModel = TaskViewModel(repository)

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
            val viewModel = TaskViewModel(repository)

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
                        status = TaskStatus.COMPLETED,
                        start = "2026-06-28T10:00:00Z",
                    ),
                    task(uuid = "pending", description = "Pending task"),
                )
            val viewModel = TaskViewModel(repository)

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
            val viewModel = TaskViewModel(repository)

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
        val viewModel = TaskViewModel(repository)

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
            val viewModel = TaskViewModel(repository)

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
            val viewModel = TaskViewModel(repository)

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
            val viewModel = TaskViewModel(repository)

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
            val viewModel = TaskViewModel(repository)

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
            val viewModel = TaskViewModel(repository)

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
            val viewModel = TaskViewModel(repository)
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

            viewModel.updateTask(edited)
            advanceUntilIdle()

            coVerify {
                repository.updateTask(
                    edited.copy(wait = "2030-09-03T00:00:00Z"),
                )
            }
        }

    @Test
    fun `update task does not override explicit wait when auto waiting is enabled`() =
        runTest {
            autoWaitingFlow.value = true
            val viewModel = TaskViewModel(repository)
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

            viewModel.updateTask(edited)
            advanceUntilIdle()

            coVerify { repository.updateTask(edited) }
        }

    private fun task(
        uuid: String,
        description: String,
        status: TaskStatus = TaskStatus.PENDING,
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
