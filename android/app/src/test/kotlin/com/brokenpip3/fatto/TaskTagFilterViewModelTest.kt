package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.TaskRepository
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.vm.TaskViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class TaskTagFilterViewModelTest {
    private val repository = mockk<TaskRepository>(relaxed = true)
    private val tasksFlow = MutableStateFlow<List<Task>>(emptyList())

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        every { repository.tasks } returns tasksFlow
        every { repository.showCompleted } returns MutableStateFlow(false)
        every { repository.hideBlockedTasksWaiting } returns MutableStateFlow(false)
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
    fun `selecting an excluded tag from Tags shows its tasks`() =
        runTest {
            tasksFlow.value =
                listOf(
                    task(uuid = "router", description = "Replace router", tags = listOf("devices")),
                    task(uuid = "bank", description = "Call bank"),
                )
            val viewModel = TaskViewModel(repository, FixedTaskTimeSource())
            val job = launch { viewModel.activeTasks.collect {} }
            viewModel.cycleTag("devices")
            viewModel.cycleTag("devices")
            advanceUntilIdle()
            assertEquals(listOf("bank"), viewModel.activeTasks.value.map { it.uuid })

            viewModel.toggleTag("devices")
            advanceUntilIdle()
            assertEquals(listOf("router"), viewModel.activeTasks.value.map { it.uuid })
            assertEquals(emptySet<String>(), viewModel.excludedTags.value)
            job.cancel()
        }

    @Test
    fun `applying builder tag filters preserves exclusions and replaces previous tags`() =
        runTest {
            tasksFlow.value =
                listOf(
                    task(uuid = "both", description = "Laptop at home", tags = listOf("devices", "home")),
                    task(uuid = "home", description = "Fix gate", tags = listOf("home")),
                    task(uuid = "other", description = "Call bank"),
                )
            val viewModel = TaskViewModel(repository, FixedTaskTimeSource())
            val job = launch { viewModel.activeTasks.collect {} }
            viewModel.toggleTag("old")
            viewModel.setTagFilters(setOf("home", "devices"), setOf("devices"))
            advanceUntilIdle()

            assertEquals(listOf("home"), viewModel.activeTasks.value.map { it.uuid })
            assertEquals(setOf("home"), viewModel.selectedTags.value)
            assertEquals(setOf("devices"), viewModel.excludedTags.value)
            viewModel.clearTags()
            advanceUntilIdle()
            assertEquals(listOf("both", "home", "other"), viewModel.activeTasks.value.map { it.uuid })
            job.cancel()
        }

    @Test
    fun `tag chip cycles include then exclude then off`() =
        runTest {
            tasksFlow.value =
                listOf(
                    task(uuid = "router", description = "Replace router", tags = listOf("devices")),
                    task(uuid = "both", description = "Laptop at home", tags = listOf("devices", "home")),
                    task(uuid = "home", description = "Fix gate", tags = listOf("home")),
                    task(uuid = "untagged", description = "Call bank"),
                )
            val viewModel = TaskViewModel(repository, FixedTaskTimeSource())
            val job = launch { viewModel.activeTasks.collect {} }
            advanceUntilIdle()
            val all = listOf("router", "both", "home", "untagged")
            assertEquals(all, viewModel.activeTasks.value.map { it.uuid })

            viewModel.cycleTag("devices")
            advanceUntilIdle()
            assertEquals(listOf("router", "both"), viewModel.activeTasks.value.map { it.uuid })
            assertEquals(setOf("devices"), viewModel.selectedTags.value)

            viewModel.cycleTag("devices")
            advanceUntilIdle()
            assertEquals(listOf("home", "untagged"), viewModel.activeTasks.value.map { it.uuid })
            assertEquals(emptySet<String>(), viewModel.selectedTags.value)
            assertEquals(setOf("devices"), viewModel.excludedTags.value)

            // Exclusion wins over inclusion: `+home -devices` drops the task tagged with both.
            viewModel.cycleTag("home")
            advanceUntilIdle()
            assertEquals(listOf("home"), viewModel.activeTasks.value.map { it.uuid })

            viewModel.clearFilters()
            advanceUntilIdle()
            assertEquals(all, viewModel.activeTasks.value.map { it.uuid })

            viewModel.cycleTag("devices")
            viewModel.cycleTag("devices")
            viewModel.cycleTag("devices")
            advanceUntilIdle()
            assertEquals(all, viewModel.activeTasks.value.map { it.uuid })
            assertEquals(emptySet<String>(), viewModel.excludedTags.value)
            job.cancel()
        }

    private fun task(
        uuid: String,
        description: String,
        tags: List<String> = emptyList(),
    ): Task =
        Task(
            uuid = uuid,
            description = description,
            status = TaskStatus.Pending,
            tags = tags,
            project = null,
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
