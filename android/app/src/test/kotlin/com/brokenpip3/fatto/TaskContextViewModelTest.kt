package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.TaskRepository
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.TaskContext
import com.brokenpip3.fatto.vm.TaskViewModel
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

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { repository.tasks } returns tasksFlow
        every { repository.showCompleted } returns MutableStateFlow(false)
        every { repository.hideBlockedTasksWaiting } returns MutableStateFlow(false)
        every { repository.showWaitingTasks } returns MutableStateFlow(true)
        every { repository.sortOrder } returns MutableStateFlow("DATE_CREATED")
        every { repository.sortDirection } returns MutableStateFlow("")
        every { repository.taskContexts } returns contextsFlow
        every { repository.activeTaskContextId } returns activeContextIdFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `active context filters active tasks`() =
        runTest {
            val context = TaskContext(id = "work", name = "Work", project = "Work", tags = setOf("office"))
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
            val context = TaskContext(id = "calls", name = "Calls", project = "Work")
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

    private fun task(
        uuid: String,
        description: String,
        project: String? = null,
        tags: List<String> = emptyList(),
    ): Task =
        Task(
            uuid = uuid,
            description = description,
            status = TaskStatus.PENDING,
            tags = tags,
            project = project,
            entry = "2026-01-01T00:00:00Z",
            wait = null,
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
