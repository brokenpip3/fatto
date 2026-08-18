package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.NextTasksSelector
import com.brokenpip3.fatto.data.model.Task
import org.junit.Assert.assertEquals
import org.junit.Test
import uniffi.taskchampion_android.TaskStatus
import java.time.LocalDate

class NextTasksSelectorTest {
    private fun createTask(
        desc: String,
        due: String?,
        status: TaskStatus = TaskStatus.PENDING,
    ): Task =
        Task(
            uuid = "uuid-$desc",
            description = desc,
            status = status,
            tags = emptyList(),
            project = "Project",
            entry = null,
            due = due,
            wait = null,
            scheduled = null,
            start = null,
            priority = null,
            urgency = 0f,
            isBlocked = false,
            isBlocking = false,
            dependencies = emptyList(),
            udas = emptyMap(),
        )

    /** Due date `offset` days from today, as an ISO instant string. */
    private fun day(offset: Long): String = LocalDate.now().plusDays(offset).toString() + "T00:00:00Z"

    @Test
    fun testSortsByDueDateAscendingWithOverdueFirst() {
        val soon = createTask("Soon", day(2))
        val overdue = createTask("Overdue", day(-1))
        val later = createTask("Later", day(10))

        val result = NextTasksSelector.nextTasks(listOf(later, soon, overdue), 8)

        assertEquals(listOf("Overdue", "Soon", "Later"), result.map { it.description })
    }

    @Test
    fun testExcludesTasksWithoutDueDate() {
        val dated = createTask("Dated", day(1))
        val undated = createTask("Undated", null)
        val invalid = createTask("Invalid", "not-a-date")

        val result = NextTasksSelector.nextTasks(listOf(undated, dated, invalid), 8)

        assertEquals(listOf("Dated"), result.map { it.description })
    }

    @Test
    fun testExcludesNonPendingTasks() {
        val pending = createTask("Pending", day(1))
        val completed = createTask("Completed", day(1), TaskStatus.COMPLETED)
        val deleted = createTask("Deleted", day(1), TaskStatus.DELETED)

        val result = NextTasksSelector.nextTasks(listOf(completed, pending, deleted), 8)

        assertEquals(listOf("Pending"), result.map { it.description })
    }

    @Test
    fun testLimitsResultSize() {
        val tasks = (1..10).map { createTask("Task $it", day(it.toLong())) }

        val result = NextTasksSelector.nextTasks(tasks, 3)

        assertEquals(3, result.size)
        assertEquals(listOf("Task 1", "Task 2", "Task 3"), result.map { it.description })
    }

    @Test
    fun testEmptyListReturnsEmpty() {
        assertEquals(emptyList<Task>(), NextTasksSelector.nextTasks(emptyList(), 8))
    }

    @Test
    fun testEqualDueDatesKeepInputOrder() {
        val first = createTask("First", day(1))
        val second = createTask("Second", day(1))

        val result = NextTasksSelector.nextTasks(listOf(first, second), 8)

        assertEquals(listOf("First", "Second"), result.map { it.description })
    }
}
