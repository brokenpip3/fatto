package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.toEditPatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.taskchampion_android.RecurrenceData
import uniffi.taskchampion_android.StringEdit
import uniffi.taskchampion_android.StringSetEdit
import uniffi.taskchampion_android.TaskStatus

class RecurrencePatchTest {
    private fun task() =
        Task(
            uuid = "10000000-0000-4000-8000-000000000002", description = "Occurrence",
            status = TaskStatus.Pending, tags = listOf("home", "PENDING"), due = "2030-01-01T00:00:00Z",
            entry = null, project = null, wait = null, scheduled = null, start = null, priority = null,
            urgency = 0f, isBlocked = false, isBlocking = false, dependencies = emptyList(), udas = emptyMap(),
        )

    @Test
    fun descriptionEditAndNoOpNeverDeriveWaitFromUnchangedDates() {
        val original = task()
        val patch = original.copy(description = "New title").toEditPatch(original, autoWaiting = true)
        assertEquals(StringEdit.Replace("Occurrence", "New title"), patch.description)
        assertEquals(StringEdit.Unchanged, patch.due)
        assertEquals(StringEdit.Unchanged, patch.wait)
        assertEquals(StringSetEdit.Unchanged, patch.tags)
        assertEquals(StringEdit.Unchanged, original.toEditPatch(original, autoWaiting = true).description)
        assertEquals(StringEdit.Unchanged, original.toEditPatch(original, autoWaiting = true).wait)
    }

    @Test
    fun equivalentDateInstantsNeverDeriveWaitOrRewriteNativeDates() {
        val original = task().copy(scheduled = "2030-01-10T00:00:00+00:00")
        val patch =
            original.copy(due = "2030-01-01T01:00:00+01:00", scheduled = "2030-01-10T00:00:00Z")
                .toEditPatch(original, autoWaiting = true)
        assertEquals(StringEdit.Unchanged, patch.wait)
        assertEquals(StringEdit.Unchanged, patch.due)
        assertEquals(StringEdit.Unchanged, patch.scheduled)
    }

    @Test
    fun derivedWaitChecksBothDatesIncludingAnUnchangedSourceDate() {
        val original = task().copy(scheduled = "2030-01-10T00:00:00Z")
        val patch = original.copy(due = "2030-01-20T00:00:00Z").toEditPatch(original, autoWaiting = true)
        assertEquals(StringEdit.Replace(null, "2030-01-03T00:00:00Z"), patch.wait)
        assertEquals(StringEdit.Replace("2030-01-01T00:00:00Z", "2030-01-20T00:00:00Z"), patch.due)
        assertEquals(StringEdit.Check("2030-01-10T00:00:00Z"), patch.scheduled)
    }

    @Test
    fun explicitWaitAndSetClearingPreserveTheirOriginalExpectations() {
        val original = task().copy(wait = "2030-01-04T00:00:00Z")
        val patch = original.copy(due = "2030-01-20T00:00:00Z", tags = emptyList()).toEditPatch(original, autoWaiting = true)
        assertEquals(StringEdit.Unchanged, patch.wait)
        assertEquals(StringSetEdit.Replace(listOf("home"), emptyList()), patch.tags)
        assertEquals(TaskStatus.Pending, patch.expectedStatus)
    }

    @Test
    fun repairingAnUnreadableDateChecksItsExactNativeSnapshot() {
        val original = task().copy(due = null, nativeDates = mapOf("due" to "broken"))
        val patch = original.copy(due = "2030-01-01T00:00:00Z").toEditPatch(original)
        assertEquals(StringEdit.Replace("broken", "2030-01-01T00:00:00Z"), patch.due)
    }

    @Test
    fun nativeAndRetiredTemplatesAndUnknownStatusesAreNotActionable() {
        val metadata = RecurrenceData("daily", "periodic", null, null, "--", null, null, null, null)
        for (status in listOf(TaskStatus.Recurring, TaskStatus.Completed, TaskStatus.Deleted)) {
            val template = task().copy(status = status, recurrence = metadata)
            assertTrue(template.isRecurrenceTemplate)
            assertFalse(template.isActionableStatus)
        }
        assertFalse(task().copy(status = TaskStatus.Unknown("imported")).isActionableStatus)
        val occurrence = task().copy(recurrence = metadata.copy(parent = "10000000-0000-4000-8000-000000000001", imask = "0", mask = null))
        assertTrue(occurrence.isRecurringOccurrence)
        assertTrue(occurrence.isActionableStatus)
    }
}
