package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.model.Annotation
import com.brokenpip3.fatto.data.model.RecurrenceRule
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.calendarDateModes
import com.brokenpip3.fatto.data.model.toEditPatch
import com.brokenpip3.fatto.ui.tasklist.TaskEditorSavedState
import org.junit.Assert.assertEquals
import org.junit.Test
import uniffi.taskchampion_android.DateDisplayMode
import uniffi.taskchampion_android.RecurrenceData
import uniffi.taskchampion_android.StringEdit
import uniffi.taskchampion_android.TaskStatus
import java.time.Instant

class TaskEditorSavedStateTest {
    @Test fun completeSnapshotRestoresPrecisionUnknownPropertiesAndConflictBaselines() {
        val due = "2030-04-28T22:30:45Z"
        val original =
            Task(
                uuid = "10000000-0000-4000-8000-000000000001", description = "A long \"quoted\" draft\nwith a new line",
                status = TaskStatus.Unknown("imported-status"), tags = listOf("home", "ACTIVE"), due = due,
                entry = "2020-01-01T01:02:03Z", project = "Work.Mobile", wait = null, scheduled = "2030-04-27T00:00:00Z",
                start = "2026-10-18T12:30:45Z", priority = "H", urgency = 12.5f, isBlocked = true, isBlocking = true,
                dependencies = listOf("10000000-0000-4000-8000-000000000002"),
                udas = mapOf("fatto_display_due" to "v1:local-datetime", "fatto_display_wait" to "v99:future", "custom" to "keep"),
                annotations = listOf(Annotation("2026-01-01T00:00:45Z", "Note\nkept")),
                recurrence = RecurrenceData("3days", "periodic", "parent", "0", "-+", "malformed-until", null, "last", "template"),
                nativeDates = mapOf("due" to Instant.parse(due).epochSecond.toString(), "wait" to "malformed-wait"),
                modified = "2026-10-18T00:01:45Z",
                dateModes = calendarDateModes().copy(due = DateDisplayMode.LOCAL_DATE_TIME),
            )
        val restored = TaskEditorSavedState.decodeTask(TaskEditorSavedState.encodeTask(original))
        assertEquals(original, restored)
        val patch = restored.copy(due = "2030-04-29T22:30:45Z").toEditPatch(restored)
        assertEquals(StringEdit.Replace(original.nativeDates["due"], "2030-04-29T22:30:45Z"), patch.due)
        assertEquals("malformed-wait", restored.nativeDates["wait"])
        assertEquals("v99:future", restored.udas["fatto_display_wait"])
    }

    @Test fun recurrenceRuleRestoresItsExactDraftIncludingUnreadableUntil() {
        val original = RecurrenceRule("custom-import", "unreadable-until")
        assertEquals(original, TaskEditorSavedState.decodeRule(TaskEditorSavedState.encodeRule(original)))
    }
}
