package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.model.Task
import org.junit.Assert.assertEquals
import org.junit.Test
import uniffi.taskchampion_android.TaskStatus

class TagsFilterTest {
    @Test
    fun testUserTagsSortingAndFiltering() {
        val task =
            Task(
                uuid = "uuid",
                description = "Test",
                status = TaskStatus.PENDING,
                tags = listOf("Z", "PENDING", "A", "COMPLETED", "B"),
                project = null,
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

        val userTags = task.userTags
        assertEquals(3, userTags.size)
        assertEquals("A", userTags[0])
        assertEquals("B", userTags[1])
        assertEquals("Z", userTags[2])
    }
}
