package com.brokenpip3.fatto.data.review

import com.brokenpip3.fatto.data.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.taskchampion_android.TaskStatus
import java.time.Instant

class ReviewPolicyTest {
    private val now = Instant.parse("2026-10-17T12:00:00Z")

    private fun task(
        id: String,
        reviewed: String? = null,
        modified: String? = null,
    ) = Task(
        uuid = id, description = id, status = TaskStatus.Pending, tags = emptyList(), due = null,
        entry = null, project = null, wait = null, scheduled = null, start = null, priority = null,
        urgency = 0f, isBlocked = false, isBlocking = false, dependencies = emptyList(),
        udas = reviewed?.let { mapOf("reviewed" to it) } ?: emptyMap(), modified = modified,
    )

    @Test fun `cutoff is strict and waiting tasks participate`() {
        val tasks =
            listOf(
                task("missing"), task("boundary", "2026-10-11T12:00:00Z"),
                task("old", "2026-10-11T11:59:59Z").copy(wait = "2026-11-01T00:00:00Z"),
                task("future", "2027-01-01T00:00:00Z"), task("done").copy(status = TaskStatus.Completed),
            )
        assertEquals(listOf("missing", "old"), ReviewPolicy.select(tasks, ReviewSettings(), now).tasks.map { it.uuid })
    }

    @Test fun `sort uses reviewed then modified then uuid and reports malformed values`() {
        val tasks =
            listOf(
                task("b", modified = "2026-10-02T00:00:00Z"), task("a", modified = "2026-10-01T00:00:00Z"),
                task("c", "2026-10-01T00:00:00Z"), task("bad", "garbage"),
            )
        val result = ReviewPolicy.select(tasks, ReviewSettings(), now)
        assertEquals(listOf("a", "b", "c"), result.tasks.map { it.uuid })
        assertEquals(setOf("bad"), result.invalidReviewedTaskIds)
        assertEquals("garbage", tasks.last().udas["reviewed"])
    }

    @Test fun `project hierarchy and exact tag intersect before limiting`() {
        val tasks =
            listOf(
                task("one").copy(project = "Work.Client", tags = listOf("urgent")),
                task("two").copy(project = "Workshop", tags = listOf("urgent")),
                task("three").copy(project = "Work.Client", tags = listOf("urgently")),
                task("four").copy(project = "Work", tags = listOf("urgent")),
            )
        assertEquals(
            listOf("four"),
            ReviewPolicy.select(
                tasks,
                ReviewSettings(project = "Work", tag = "urgent", sessionLimit = 1), now,
            ).tasks.map { it.uuid },
        )
    }

    @Test fun `imported date encodings represent the same instant`() {
        val expected = Instant.parse("2026-10-01T00:00:00Z")
        for (raw in listOf("1790812800", "20261001T000000Z", "2026-10-01T00:00:00Z")) {
            assertEquals(expected, ReviewPolicy.parseReviewed(raw).getOrThrow())
        }
        assertTrue(ReviewPolicy.parseReviewed("99999999999999999999").isFailure)
    }

    @Test fun `invalid settings cannot become active policy`() {
        for (build in listOf(
            { ReviewSettings(intervalDays = 0) }, { ReviewSettings(sessionLimit = -1) },
            { ReviewSettings(reminderDays = 0) },
        )) {
            assertTrue(runCatching(build).isFailure)
        }
    }

    @Test fun `settings backup rejects overflowing and fractional day counts`() {
        for (value in listOf<Number>(4_294_967_297L, 6.5)) {
            val json = ReviewSettings().toJson().put("intervalDays", value)
            assertTrue(runCatching { ReviewSettings.fromJson(json) }.isFailure)
        }
    }

    @Test fun `whitespace reviewed value is malformed rather than absent`() {
        val result = ReviewPolicy.select(listOf(task("blank", " ")), ReviewSettings(), now)
        assertTrue(result.tasks.isEmpty())
        assertEquals(setOf("blank"), result.invalidReviewedTaskIds)
    }
}
