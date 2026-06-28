package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.TaskRepository
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.vm.TaskViewModel
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Before
import org.junit.Test
import uniffi.taskchampion_android.TaskStatus

@OptIn(ExperimentalCoroutinesApi::class)
class WaitingTasksFilterTest {
    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<TaskRepository>(relaxed = true)
    private val tasksFlow = MutableStateFlow<List<Task>>(emptyList())
    private val showCompletedFlow = MutableStateFlow(false)
    private val hideBlockedFlow = MutableStateFlow(false)
    private val showWaitingTasksFlow = MutableStateFlow(true)

    private val futureWait = "2099-12-31T12:00:00Z"
    private val pastWait = "2026-05-22T12:00:00Z"

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { repository.tasks } returns tasksFlow
        every { repository.showCompleted } returns showCompletedFlow
        every { repository.hideBlockedTasksWaiting } returns hideBlockedFlow
        every { repository.showWaitingTasks } returns showWaitingTasksFlow
        every { repository.sortOrder } returns MutableStateFlow("DATE_CREATED")
        every { repository.sortDirection } returns MutableStateFlow("")
        every { repository.taskContexts } returns MutableStateFlow(emptyList())
        every { repository.activeTaskContextId } returns MutableStateFlow(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `task with future wait appears in waiting tasks`() =
        runTest {
            val viewModel = TaskViewModel(repository)

            val waitingTask = createTask(uuid = "waiting", desc = "Has future wait", wait = futureWait)

            tasksFlow.value = listOf(waitingTask)

            val job = launch { viewModel.waitingTasks.collect {} }
            advanceUntilIdle()

            val waiting = viewModel.waitingTasks.value
            assertEquals(1, waiting.size)
            assertEquals("Has future wait", waiting[0].description)
            job.cancel()
        }

    @Test
    fun `task with no wait does not appear in waiting tasks`() =
        runTest {
            val viewModel = TaskViewModel(repository)

            val normalTask = createTask(uuid = "normal", desc = "No wait")

            tasksFlow.value = listOf(normalTask)

            val job = launch { viewModel.waitingTasks.collect {} }
            advanceUntilIdle()

            assertEquals(0, viewModel.waitingTasks.value.size)
            job.cancel()
        }

    @Test
    fun `task with past wait does not appear in waiting tasks`() =
        runTest {
            val viewModel = TaskViewModel(repository)

            val expiredTask = createTask(uuid = "expired", desc = "Past wait", wait = pastWait)

            tasksFlow.value = listOf(expiredTask)

            val job = launch { viewModel.waitingTasks.collect {} }
            advanceUntilIdle()

            assertEquals(0, viewModel.waitingTasks.value.size)
            job.cancel()
        }

    @Test
    fun `completed task with future wait does not appear in waiting tasks`() =
        runTest {
            val viewModel = TaskViewModel(repository)
            showCompletedFlow.value = true

            val completedWaiting =
                createTask(
                    uuid = "completed-waiting",
                    desc = "Completed with wait",
                    status = TaskStatus.COMPLETED,
                    wait = futureWait,
                )

            tasksFlow.value = listOf(completedWaiting)

            val job = launch { viewModel.waitingTasks.collect {} }
            advanceUntilIdle()

            assertEquals(0, viewModel.waitingTasks.value.size)
            job.cancel()
        }

    @Test
    fun `waiting tasks separated from active tasks`() =
        runTest {
            val viewModel = TaskViewModel(repository)

            val activeTask = createTask(uuid = "active", desc = "Active task")
            val waitingTask = createTask(uuid = "waiting", desc = "Waiting task", wait = futureWait)

            tasksFlow.value = listOf(activeTask, waitingTask)

            val activeJob = launch { viewModel.activeTasks.collect {} }
            val waitingJob = launch { viewModel.waitingTasks.collect {} }
            advanceUntilIdle()

            val active = viewModel.activeTasks.value
            val waiting = viewModel.waitingTasks.value

            assertEquals(1, active.size)
            assertEquals("Active task", active[0].description)

            assertEquals(1, waiting.size)
            assertEquals("Waiting task", waiting[0].description)

            activeJob.cancel()
            waitingJob.cancel()
        }

    @Test
    fun `waiting tasks hidden when showWaitingTasks setting is off`() =
        runTest {
            showWaitingTasksFlow.value = false
            val viewModel = TaskViewModel(repository)

            val waitingTask = createTask(uuid = "waiting", desc = "Waiting task", wait = futureWait)

            tasksFlow.value = listOf(waitingTask)

            val job = launch { viewModel.waitingTasks.collect {} }
            advanceUntilIdle()

            assertEquals(0, viewModel.waitingTasks.value.size)
            job.cancel()
        }

    private fun createTask(
        uuid: String = "uuid",
        desc: String = "task",
        status: TaskStatus = TaskStatus.PENDING,
        wait: String? = null,
    ): Task {
        return Task(
            uuid = uuid,
            description = desc,
            status = status,
            tags = emptyList(),
            project = null,
            entry = null,
            wait = wait,
            due = null,
            scheduled = null,
            start = null,
            priority = null,
            urgency = 0f,
            isBlocked = false,
            isBlocking = false,
            dependencies = emptyList(),
            udas = emptyMap(),
        )
    }
}
