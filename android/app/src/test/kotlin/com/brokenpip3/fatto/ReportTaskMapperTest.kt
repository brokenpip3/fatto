package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.reports.mapReportTasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uniffi.taskchampion_android.TaskStatus

class ReportTaskMapperTest {
    private fun task(status: TaskStatus = TaskStatus.Pending) =
        Task(
            uuid = "id", description = "Example", status = status, tags = listOf("PENDING", "ACTIVE", "BLOCKING", "android"),
            due = null, entry = "malformed", project = "", wait = null, scheduled = null, start = null, priority = null,
            urgency = 0f,
            isBlocked = false,
            isBlocking = false,
            dependencies = emptyList(),
            udas = emptyMap(),
            end = "2026-10-23T10:00:00Z",
        )

    @Test fun parsesIndependentDatesAndRemovesSystemTags() {
        val mapped = mapReportTasks(listOf(task())).single()
        assertNull(mapped.entry)
        assertEquals("2026-10-23T10:00:00Z", mapped.end.toString())
        assertEquals(setOf("android"), mapped.userTags)
        assertNull(mapped.project)
    }

    @Test fun templatesAreExcludedButOrdinaryInstancesRemain() {
        assertEquals(1, mapReportTasks(listOf(task(TaskStatus.Recurring), task(TaskStatus.Completed))).size)
    }

    @Test fun whitespaceOnlyImportedProjectIsUnassignedAndNamedProjectsAreTrimmed() {
        assertNull(mapReportTasks(listOf(task().copy(project = "   "))).single().project)
        assertEquals("Work.Android", mapReportTasks(listOf(task().copy(project = " Work.Android "))).single().project)
    }
}
