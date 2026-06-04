package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.model.Annotation
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.toModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.taskchampion_android.TaskData
import uniffi.taskchampion_android.TaskStatus
import uniffi.taskchampion_android.Annotation as UniAnnotation

class AnnotationModelTest {
    @Test
    fun annotationDataClass_holdsValues() {
        val ann = Annotation(entry = "2026-06-04T10:00:00Z", description = "Test note")
        assertEquals("2026-06-04T10:00:00Z", ann.entry)
        assertEquals("Test note", ann.description)
    }

    @Test
    fun toModel_mapsAnnotationsCorrectly() {
        val taskData =
            TaskData(
                uuid = "test-uuid",
                description = "Test task",
                status = TaskStatus.PENDING,
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
                annotations =
                    listOf(
                        UniAnnotation(entry = "2026-06-04T10:00:00Z", description = "First note"),
                        UniAnnotation(entry = "2026-06-04T11:00:00Z", description = "Second note"),
                    ),
            )

        val task = taskData.toModel()
        assertEquals(2, task.annotations.size)
        assertEquals("First note", task.annotations[0].description)
        assertEquals("Second note", task.annotations[1].description)
    }

    @Test
    fun toModel_emptyAnnotations() {
        val taskData =
            TaskData(
                uuid = "test-uuid",
                description = "Test task",
                status = TaskStatus.PENDING,
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
                annotations = emptyList(),
            )

        val task = taskData.toModel()
        assertTrue(task.annotations.isEmpty())
    }

    @Test
    fun taskCopy_preservesAnnotations() {
        val original =
            Task(
                uuid = "test-uuid",
                description = "Test",
                status = TaskStatus.PENDING,
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
                udas = emptyMap(),
                annotations = listOf(Annotation(entry = "2026-06-04T10:00:00Z", description = "Note")),
            )

        val updated = original.copy(description = "Updated")
        assertEquals(1, updated.annotations.size)
        assertEquals("Note", updated.annotations[0].description)
        assertEquals("Updated", updated.description)
    }
}
