package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.TaskRepository
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.vm.SortOrder
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
class TaskSortTest {
    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<TaskRepository>(relaxed = true)
    private val tasksFlow = MutableStateFlow<List<Task>>(emptyList())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { repository.tasks } returns tasksFlow
        every { repository.showCompleted } returns MutableStateFlow(false)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testPrioritySorting() =
        runTest {
            val viewModel = TaskViewModel(repository)

            val taskH = createTask("High", "H", 10f)
            val taskM = createTask("Medium", "M", 5f)
            val taskL = createTask("Low", "L", 2f)
            val taskNone = createTask("None", null, 1f)

            tasksFlow.value = listOf(taskNone, taskM, taskH, taskL)

            viewModel.setSortOrder(SortOrder.PRIORITY)

            // Start collection to activate stateIn
            val job = launch { viewModel.activeTasks.collect {} }
            advanceUntilIdle()

            val sorted = viewModel.activeTasks.value
            assertEquals("High", sorted[0].description)
            assertEquals("Medium", sorted[1].description)
            assertEquals("Low", sorted[2].description)
            assertEquals("None", sorted[3].description)
            job.cancel()
        }

    @Test
    fun testUrgencySorting() =
        runTest {
            val viewModel = TaskViewModel(repository)

            val task1 = createTask("Urgent", null, 20f)
            val task2 = createTask("Normal", null, 5f)
            val task3 = createTask("Meh", null, 15f)

            tasksFlow.value = listOf(task2, task1, task3)

            viewModel.setSortOrder(SortOrder.URGENCY)

            // Start collection to activate stateIn
            val job = launch { viewModel.activeTasks.collect {} }
            advanceUntilIdle()

            val sorted = viewModel.activeTasks.value
            assertEquals("Urgent", sorted[0].description)
            assertEquals("Meh", sorted[1].description)
            assertEquals("Normal", sorted[2].description)
            job.cancel()
        }

    private fun createTask(
        desc: String,
        priority: String?,
        urgency: Float,
    ): Task {
        return Task(
            uuid = "uuid-$desc",
            description = desc,
            status = TaskStatus.PENDING,
            tags = emptyList(),
            project = null,
            entry = "2026-01-01T00:00:00Z",
            wait = null,
            due = null,
            scheduled = null,
            start = null,
            priority = priority,
            urgency = urgency,
            isBlocked = false,
            isBlocking = false,
            dependencies = emptyList(),
            udas = emptyMap(),
        )
    }
}
