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
class BlockedTasksFilterTest {
    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<TaskRepository>(relaxed = true)
    private val tasksFlow = MutableStateFlow<List<Task>>(emptyList())
    private val showCompletedFlow = MutableStateFlow(false)
    private val hideBlockedFlow = MutableStateFlow(false)

    private val futureWait = "2099-12-31T12:00:00Z"
    private val pastWait = "2026-05-22T12:00:00Z"

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { repository.tasks } returns tasksFlow
        every { repository.showCompleted } returns showCompletedFlow
        every { repository.hideBlockedTasksWaiting } returns hideBlockedFlow
        every { repository.showWaitingTasks } returns MutableStateFlow(true)
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
    fun `disabled setting shows blocked tasks regardless of dependency wait status`() =
        runTest {
            val viewModel = TaskViewModel(repository)
            hideBlockedFlow.value = false

            val depWaiting = createTask(uuid = "dep-wait", desc = "Waiting dep", wait = futureWait)
            val depPending = createTask(uuid = "dep-pending", desc = "Pending dep")
            val taskBlockedByWaiting =
                createTask(
                    uuid = "blocked-by-waiting",
                    desc = "Blocked by waiting",
                    isBlocked = true,
                    dependencies = listOf("dep-wait"),
                )
            val taskBlockedByBoth =
                createTask(
                    uuid = "blocked-by-both",
                    desc = "Blocked by both",
                    isBlocked = true,
                    dependencies = listOf("dep-wait", "dep-pending"),
                )

            tasksFlow.value = listOf(depWaiting, depPending, taskBlockedByWaiting, taskBlockedByBoth)

            val job = launch { viewModel.activeTasks.collect {} }
            advanceUntilIdle()

            val active = viewModel.activeTasks.value
            // depWaiting filtered out (wait in future), others all shown
            assertEquals(3, active.size)
            job.cancel()
        }

    @Test
    fun `enabled setting hides task blocked only by waiting dependencies`() =
        runTest {
            val viewModel = TaskViewModel(repository)
            hideBlockedFlow.value = true

            val depWaiting = createTask(uuid = "dep-wait", desc = "Waiting dep", wait = futureWait)
            val taskBlockedByWaiting =
                createTask(
                    uuid = "blocked-by-waiting",
                    desc = "Should be hidden",
                    isBlocked = true,
                    dependencies = listOf("dep-wait"),
                )

            tasksFlow.value = listOf(depWaiting, taskBlockedByWaiting)

            val job = launch { viewModel.activeTasks.collect {} }
            advanceUntilIdle()

            val active = viewModel.activeTasks.value
            // depWaiting filtered out (wait in future), blocked task filtered (all deps waiting)
            assertEquals(0, active.size)
            job.cancel()
        }

    @Test
    fun `enabled setting shows task blocked by mix of waiting and pending dependencies`() =
        runTest {
            val viewModel = TaskViewModel(repository)
            hideBlockedFlow.value = true

            val depWaiting = createTask(uuid = "dep-wait", desc = "Waiting dep", wait = futureWait)
            val depPending = createTask(uuid = "dep-pending", desc = "Pending dep")
            val taskBlockedByBoth =
                createTask(
                    uuid = "blocked-by-both",
                    desc = "Should be visible",
                    isBlocked = true,
                    dependencies = listOf("dep-wait", "dep-pending"),
                )

            tasksFlow.value = listOf(depWaiting, depPending, taskBlockedByBoth)

            val job = launch { viewModel.activeTasks.collect {} }
            advanceUntilIdle()

            val active = viewModel.activeTasks.value
            // depWaiting filtered out (wait in future), depPending shown, blocked task shown (has actionable dep)
            assertEquals(2, active.size)
            job.cancel()
        }

    @Test
    fun `enabled setting shows task blocked by pending-only dependencies`() =
        runTest {
            val viewModel = TaskViewModel(repository)
            hideBlockedFlow.value = true

            val depPending = createTask(uuid = "dep-pending", desc = "Pending dep")
            val taskBlockedByPending =
                createTask(
                    uuid = "blocked-by-pending",
                    desc = "Should be visible",
                    isBlocked = true,
                    dependencies = listOf("dep-pending"),
                )

            tasksFlow.value = listOf(depPending, taskBlockedByPending)

            val job = launch { viewModel.activeTasks.collect {} }
            advanceUntilIdle()

            val active = viewModel.activeTasks.value
            // Both tasks shown: depPending is active, blocked task has actionable dep
            assertEquals(2, active.size)
            job.cancel()
        }

    @Test
    fun `enabled setting does not affect non-blocked tasks`() =
        runTest {
            val viewModel = TaskViewModel(repository)
            hideBlockedFlow.value = true

            val normalTask =
                createTask(uuid = "normal", desc = "Normal pending task", isBlocked = false)

            tasksFlow.value = listOf(normalTask)

            val job = launch { viewModel.activeTasks.collect {} }
            advanceUntilIdle()

            val active = viewModel.activeTasks.value
            assertEquals(1, active.size)
            assertEquals("Normal pending task", active[0].description)
            job.cancel()
        }

    @Test
    fun `enabled setting shows task whose waiting dep has wait already expired`() =
        runTest {
            val viewModel = TaskViewModel(repository)
            hideBlockedFlow.value = true

            val depPastWait =
                createTask(uuid = "dep-past-wait", desc = "Past wait dep", wait = pastWait)
            val taskBlockedByPastWait =
                createTask(
                    uuid = "blocked-by-past-wait",
                    desc = "Should be visible",
                    isBlocked = true,
                    dependencies = listOf("dep-past-wait"),
                )

            tasksFlow.value = listOf(depPastWait, taskBlockedByPastWait)

            val job = launch { viewModel.activeTasks.collect {} }
            advanceUntilIdle()

            val active = viewModel.activeTasks.value
            // depPastWait shown (wait already expired), blocked task has no waiting dep
            assertEquals(2, active.size)
            job.cancel()
        }

    @Test
    fun `enabled setting shows non-blocked task with completed dependency that had wait`() =
        runTest {
            val viewModel = TaskViewModel(repository)
            hideBlockedFlow.value = true

            val depCompleted =
                createTask(
                    uuid = "dep-completed",
                    desc = "Completed dep",
                    status = TaskStatus.COMPLETED,
                    wait = futureWait,
                )
            val normalTask =
                createTask(
                    uuid = "normal",
                    desc = "Normal task",
                    isBlocked = false,
                    dependencies = listOf("dep-completed"),
                )

            tasksFlow.value = listOf(depCompleted, normalTask)

            val job = launch { viewModel.activeTasks.collect {} }
            advanceUntilIdle()

            val active = viewModel.activeTasks.value
            // depCompleted filtered out (COMPLETED), normalTask shown (not blocked)
            assertEquals(1, active.size)
            assertEquals("Normal task", active[0].description)
            job.cancel()
        }

    private fun createTask(
        uuid: String = "uuid",
        desc: String = "task",
        status: TaskStatus = TaskStatus.PENDING,
        wait: String? = null,
        isBlocked: Boolean = false,
        dependencies: List<String> = emptyList(),
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
            isBlocked = isBlocked,
            isBlocking = false,
            dependencies = dependencies,
            udas = emptyMap(),
        )
    }
}
