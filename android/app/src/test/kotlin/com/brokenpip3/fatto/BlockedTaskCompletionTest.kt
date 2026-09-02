package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.ui.tasklist.completionConfirmationMessage
import com.brokenpip3.fatto.ui.tasklist.unresolvedDependencyUuids
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.taskchampion_android.TaskStatus

class BlockedTaskCompletionTest {
    @Test
    fun unblockedTaskUsesGenericConfirmation() {
        assertEquals(
            "Are you sure you want to mark this task as completed?",
            completionConfirmationMessage(task("Task"), emptyList()),
        )
    }

    @Test
    fun blockedTaskIncludesPendingDependencyDescriptions() {
        val dependency = task("Finish prerequisite")
        val blocked = task("Blocked task", isBlocked = true, dependencies = listOf(dependency.uuid))

        val message = completionConfirmationMessage(blocked, listOf(dependency))

        assertTrue(message.contains("1 tasks"))
        assertTrue(message.contains("Are you sure"))
        assertEquals(listOf(dependency.uuid), unresolvedDependencyUuids(blocked, listOf(dependency)))
    }

    @Test
    fun blockedTaskWithUnknownDependencyStillProducesWarning() {
        val message =
            completionConfirmationMessage(
                task("Blocked task", isBlocked = true, dependencies = listOf("missing")),
                emptyList(),
            )

        assertTrue(message.contains("currently blocked"))
    }

    private fun task(
        description: String,
        isBlocked: Boolean = false,
        dependencies: List<String> = emptyList(),
    ) = Task(
        uuid = "uuid-$description",
        description = description,
        status = TaskStatus.PENDING,
        tags = emptyList(), due = null, entry = null, project = null,
        wait = null, scheduled = null, start = null, priority = null,
        urgency = 0f, isBlocked = isBlocked, isBlocking = false,
        dependencies = dependencies, udas = emptyMap(),
    )
}
