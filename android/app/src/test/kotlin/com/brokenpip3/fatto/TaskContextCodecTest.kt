package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.TaskContextCodec
import com.brokenpip3.fatto.data.model.TaskContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskContextCodecTest {
    @Test
    fun `round trips contexts`() {
        val contexts =
            listOf(
                TaskContext(
                    id = "work-id",
                    name = "Work",
                    descriptionQuery = "call",
                    project = "Work.Mobile",
                    tags = setOf("office", "urgent"),
                ),
            )

        val decoded = TaskContextCodec.decode(TaskContextCodec.encode(contexts))

        assertEquals(contexts, decoded)
    }

    @Test
    fun `round trips project named null`() {
        val contexts =
            listOf(
                TaskContext(
                    id = "null-project-id",
                    name = "Null Project",
                    project = "null",
                ),
            )

        val decoded = TaskContextCodec.decode(TaskContextCodec.encode(contexts))

        assertEquals(contexts, decoded)
    }

    @Test
    fun `corrupted json returns empty list`() {
        assertTrue(TaskContextCodec.decode("not-json").isEmpty())
    }

    @Test
    fun `missing optional fields decode as empty filters`() {
        val decoded = TaskContextCodec.decode("""[{"id":"id","name":"Inbox"}]""")

        assertEquals(listOf(TaskContext(id = "id", name = "Inbox")), decoded)
    }
}
