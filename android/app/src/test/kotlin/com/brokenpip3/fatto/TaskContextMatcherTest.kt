package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.TaskContextMatcher
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.TaskContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.taskchampion_android.TaskStatus
import java.time.Instant

class TaskContextMatcherTest {
    private val now = Instant.parse("2026-07-04T12:00:00Z")

    @Test
    fun `null context matches every task`() {
        assertTrue(TaskContextMatcher.matches(task(), null, now))
    }

    @Test
    fun `issue example matches expression context`() {
        val context = context("(-buy -ninu -relax -inbox) or +DUE")

        assertTrue(TaskContextMatcher.matches(task(description = "Read docs"), context, now))
        assertTrue(TaskContextMatcher.matches(task(due = "2026-07-05T12:00:00Z"), context, now))
        assertFalse(TaskContextMatcher.matches(task(description = "buy milk"), context, now))
    }

    @Test
    fun `negative project excludes exact project and subprojects`() {
        val context = context("-project:Work")

        assertFalse(TaskContextMatcher.matches(task(project = "Work"), context, now))
        assertFalse(TaskContextMatcher.matches(task(project = "Work.Mobile"), context, now))
        assertTrue(TaskContextMatcher.matches(task(project = "Home"), context, now))
    }

    @Test
    fun `virtual tags use task state`() {
        assertTrue(TaskContextMatcher.matches(task(status = TaskStatus.PENDING), context("+PENDING"), now))
        assertFalse(TaskContextMatcher.matches(task(status = TaskStatus.COMPLETED), context("+PENDING"), now))

        assertTrue(TaskContextMatcher.matches(task(status = TaskStatus.COMPLETED), context("+COMPLETED"), now))
        assertFalse(TaskContextMatcher.matches(task(status = TaskStatus.PENDING), context("+COMPLETED"), now))

        assertTrue(TaskContextMatcher.matches(task(due = "2026-07-05T12:00:00Z"), context("+DUE"), now))
        assertFalse(TaskContextMatcher.matches(task(), context("+DUE"), now))

        assertTrue(TaskContextMatcher.matches(task(wait = "2026-07-04T12:05:00Z"), context("+WAITING"), now))
        assertFalse(TaskContextMatcher.matches(task(wait = "2026-07-04T11:55:00Z"), context("+WAITING"), now))
        assertFalse(
            TaskContextMatcher.matches(
                task(
                    status = TaskStatus.COMPLETED,
                    wait = "2026-07-04T12:05:00Z",
                ),
                context("+WAITING"),
                now,
            ),
        )

        assertTrue(TaskContextMatcher.matches(task(start = "2026-07-04T11:00:00Z"), context("+ACTIVE"), now))
        assertTrue(TaskContextMatcher.matches(task(tags = listOf("active")), context("+ACTIVE"), now))
        assertFalse(TaskContextMatcher.matches(task(), context("+ACTIVE"), now))
        assertFalse(
            TaskContextMatcher.matches(
                task(
                    status = TaskStatus.COMPLETED,
                    start = "2026-07-04T11:00:00Z",
                    tags = listOf("active"),
                ),
                context("+ACTIVE"),
                now,
            ),
        )

        assertTrue(TaskContextMatcher.matches(task(isBlocking = true), context("+BLOCKING"), now))
        assertFalse(TaskContextMatcher.matches(task(), context("+BLOCKING"), now))

        assertTrue(TaskContextMatcher.matches(task(isBlocked = true), context("+BLOCKED"), now))
        assertFalse(TaskContextMatcher.matches(task(), context("+BLOCKED"), now))
    }

    @Test
    fun `parsed virtual tag expressions match tasks`() {
        val expression = TaskContextMatcher.parseExpression(context("+due")).getOrThrow()

        assertTrue(TaskContextMatcher.matches(task(due = "2026-07-05T12:00:00Z"), expression, now))
        assertFalse(TaskContextMatcher.matches(task(), expression, now))
    }

    @Test
    fun `waiting virtual tag accepts numeric utc offset timestamps`() {
        assertTrue(TaskContextMatcher.matches(task(wait = "2026-07-04T12:05:00+00:00"), context("+WAITING"), now))
    }

    @Test
    fun `invalid context matches no tasks`() {
        val context = context("priority:H")

        assertEquals("Unsupported attribute: priority", TaskContextMatcher.parseError(context))
        assertFalse(TaskContextMatcher.matches(task(), context, now))
    }

    @Test
    fun `project tag and keyword expressions still work`() {
        val context = context("project:Work +office call")

        assertTrue(
            TaskContextMatcher.matches(
                task(description = "Call client", project = "Work.Mobile", tags = listOf("office")),
                context,
                now,
            ),
        )
        assertFalse(
            TaskContextMatcher.matches(
                task(description = "Call client", project = "Home", tags = listOf("office")),
                context,
                now,
            ),
        )
        assertFalse(
            TaskContextMatcher.matches(
                task(description = "Email client", project = "Work.Mobile", tags = listOf("office")),
                context,
                now,
            ),
        )
        assertFalse(
            TaskContextMatcher.matches(
                task(description = "Call client", project = "Work.Mobile", tags = listOf("remote")),
                context,
                now,
            ),
        )
    }

    @Test
    fun `expression context still derives all filters`() {
        val context = context("call project:Work +office")

        assertTrue(
            TaskContextMatcher.matches(
                task(description = "Call client", project = "Work.Mobile", tags = listOf("office")),
                context,
                now,
            ),
        )
        assertFalse(
            TaskContextMatcher.matches(
                task(description = "Email client", project = "Work.Mobile", tags = listOf("office")),
                context,
                now,
            ),
        )
        assertFalse(
            TaskContextMatcher.matches(
                task(description = "Call client", project = "Home", tags = listOf("office")),
                context,
                now,
            ),
        )
        assertFalse(
            TaskContextMatcher.matches(
                task(description = "Call client", project = "Work.Mobile", tags = listOf("remote")),
                context,
                now,
            ),
        )
    }

    @Test
    fun `parse error is null for valid contexts`() {
        assertNull(TaskContextMatcher.parseError(context("project:Work +office call")))
    }

    @Test
    fun `expression only summary returns expression text`() {
        assertEquals("+DUE", TaskContext(name = "X", expressionText = "+DUE").summary())
    }

    private fun context(expressionText: String): TaskContext =
        TaskContext(
            name = "Filter",
            expressionText = expressionText,
        )

    private fun task(
        description: String = "task",
        status: TaskStatus = TaskStatus.PENDING,
        project: String? = null,
        tags: List<String> = emptyList(),
        due: String? = null,
        wait: String? = null,
        start: String? = null,
        isBlocked: Boolean = false,
        isBlocking: Boolean = false,
    ): Task =
        Task(
            uuid = "uuid",
            description = description,
            status = status,
            tags = tags,
            due = due,
            entry = null,
            project = project,
            wait = wait,
            scheduled = null,
            start = start,
            priority = null,
            urgency = 0f,
            isBlocked = isBlocked,
            isBlocking = isBlocking,
            dependencies = emptyList(),
            udas = emptyMap(),
        )
}
