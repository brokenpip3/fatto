package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.TaskContextMatcher
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.TaskContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.taskchampion_android.TaskStatus

class TaskContextMatcherTest {
    @Test
    fun `empty context matches every task`() {
        assertTrue(TaskContextMatcher.matches(task(), TaskContext(name = "All")))
    }

    @Test
    fun `description query matches case insensitively`() {
        val context = TaskContext(name = "Calls", descriptionQuery = "CALL")

        assertTrue(TaskContextMatcher.matches(task(description = "Call bank"), context))
        assertFalse(TaskContextMatcher.matches(task(description = "Buy milk"), context))
    }

    @Test
    fun `project matches exact project and subprojects`() {
        val context = TaskContext(name = "Work", project = "Work")

        assertTrue(TaskContextMatcher.matches(task(project = "Work"), context))
        assertTrue(TaskContextMatcher.matches(task(project = "Work.Mobile"), context))
        assertFalse(TaskContextMatcher.matches(task(project = "Home"), context))
    }

    @Test
    fun `all context tags must be present`() {
        val context = TaskContext(name = "Office", tags = setOf("office", "urgent"))

        assertTrue(TaskContextMatcher.matches(task(tags = listOf("office", "urgent", "email")), context))
        assertFalse(TaskContextMatcher.matches(task(tags = listOf("office")), context))
    }

    private fun task(
        description: String = "task",
        project: String? = null,
        tags: List<String> = emptyList(),
    ): Task =
        Task(
            uuid = "uuid",
            description = description,
            status = TaskStatus.PENDING,
            tags = tags,
            project = project,
            entry = null,
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
