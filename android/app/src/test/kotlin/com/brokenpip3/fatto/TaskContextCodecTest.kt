package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.TaskContextCodec
import com.brokenpip3.fatto.data.model.TaskContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskContextCodecTest {
    @Test
    fun `round trips v2 contexts`() {
        val contexts =
            listOf(
                TaskContext(
                    id = "work-id",
                    name = "Work",
                    expressionText = "call project:Work.Mobile +office +urgent",
                ),
            )

        val decoded = TaskContextCodec.decode(TaskContextCodec.encode(contexts))

        assertEquals(contexts, decoded)
    }

    @Test
    fun `migrates v1 contexts to expression text`() {
        val contexts =
            listOf(
                TaskContext(id = "work-id", name = "Work", expressionText = "call project:Work.Mobile +office +urgent"),
            )

        val decoded =
            TaskContextCodec.decode(
                """
                [
                  {
                    "id": "work-id",
                    "name": "Work",
                    "descriptionQuery": "call",
                    "project": "Work.Mobile",
                    "tags": ["urgent", "office"]
                  }
                ]
                """.trimIndent(),
            )

        assertEquals(contexts, decoded)
    }

    @Test
    fun `migrates v1 free text queries as quoted keyword phrases`() {
        val decoded =
            TaskContextCodec.decode(
                """
                [
                  {
                    "id": "review-id",
                    "name": "Review",
                    "descriptionQuery": "call or email (urgent)"
                  }
                ]
                """.trimIndent(),
            )

        assertEquals(
            listOf(
                TaskContext(
                    id = "review-id",
                    name = "Review",
                    expressionText = "\"call or email (urgent)\"",
                ),
            ),
            decoded,
        )
    }

    @Test
    fun `migrates literal null project value`() {
        val decoded =
            TaskContextCodec.decode(
                """
                [
                  {
                    "id": "null-project-id",
                    "name": "Null Project",
                    "project": "null"
                  }
                ]
                """.trimIndent(),
            )

        assertEquals(
            listOf(
                TaskContext(
                    id = "null-project-id",
                    name = "Null Project",
                    expressionText = "project:null",
                ),
            ),
            decoded,
        )
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

    @Test
    fun `mixed arrays keep valid contexts`() {
        val decoded =
            TaskContextCodec.decode(
                """
                [
                  42,
                  {
                    "id": "id",
                    "name": "Inbox",
                    "expressionText": "call"
                  }
                ]
                """.trimIndent(),
            )

        assertEquals(
            listOf(
                TaskContext(
                    id = "id",
                    name = "Inbox",
                    expressionText = "call",
                ),
            ),
            decoded,
        )
    }
}
