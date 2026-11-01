package com.brokenpip3.fatto
import com.brokenpip3.fatto.data.model.toModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uniffi.taskchampion_android.TaskData
import uniffi.taskchampion_android.TaskStatus
import uniffi.taskchampion_android.Annotation as UniAnnotation

class ReportTaskMappingTest {
    @Test fun nativeEndIsPreservedInModel() {
        val taskData =
            TaskData(
                uuid = "test-uuid",
                description = "Test task",
                status = TaskStatus.Pending,
                tags = emptyList(),
                due = null,
                entry = null,
                project = null,
                wait = null,
                scheduled = null,
                start = null,
                priority = null,
                urgency = 0.0f,
                isBlocked = false,
                isBlocking = false,
                dependencies = emptyList(),
                udas = emptyList(),
                recurrence = null,
                nativeDates = emptyList(),
                modified = null,
                end = "2026-10-23T10:00:00Z",
                dateModes = com.brokenpip3.fatto.data.model.calendarDateModes(),
                annotations =
                    listOf(
                        UniAnnotation(entry = "2026-06-04T10:00:00Z", description = "First note"),
                        UniAnnotation(entry = "2026-06-04T11:00:00Z", description = "Second note"),
                    ),
            )

        assertEquals("2026-10-23T10:00:00Z", taskData.toModel().end)
        assertNull(taskData.copy(end = null).toModel().end)
    }
}
