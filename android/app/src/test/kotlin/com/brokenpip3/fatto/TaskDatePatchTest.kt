package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.calendarDateModes
import com.brokenpip3.fatto.data.model.toEditPatch
import com.brokenpip3.fatto.data.model.visibleUdas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import uniffi.taskchampion_android.DateDisplayMode
import uniffi.taskchampion_android.DateModeEdit
import uniffi.taskchampion_android.StringEdit
import uniffi.taskchampion_android.TaskStatus

class TaskDatePatchTest {
    private fun task() =
        Task(
            uuid = "10000000-0000-4000-8000-000000000002", description = "Task",
            status = TaskStatus.Pending, tags = emptyList(), due = "2030-01-01T00:00:00Z",
            entry = null, project = null, wait = null, scheduled = null, start = null, priority = null,
            urgency = 0f, isBlocked = false, isBlocking = false, dependencies = emptyList(), udas = emptyMap(),
        )

    @Test fun timestampEditChecksUnknownMarkerWithoutReplacingIt() {
        val original = task().copy(udas = mapOf("fatto_display_due" to "v99:future"))
        val patch = original.copy(due = "2030-01-02T00:00:00Z").toEditPatch(original)
        assertEquals(DateModeEdit.Check("v99:future"), patch.dateModes.due)
    }

    @Test fun equalInstantModeChangeChecksTimestampToo() {
        val original = task()
        val patch = original.copy(dateModes = calendarDateModes().copy(due = DateDisplayMode.LOCAL_DATE_TIME)).toEditPatch(original)
        assertEquals(StringEdit.Replace(original.due, original.due), patch.due)
        assertEquals(DateModeEdit.Replace(null, DateDisplayMode.LOCAL_DATE_TIME), patch.dateModes.due)
        assertEquals(StringEdit.Unchanged, patch.wait)
    }

    @Test fun malformedDateClearChecksRawValueAndRemovesEvenUnknownMarker() {
        for (field in listOf("due", "scheduled", "wait")) {
            val original =
                task().copy(
                    due = null,
                    nativeDates = mapOf(field to "broken"),
                    udas = mapOf("fatto_display_$field" to "v99:future"),
                )
            val patch = original.toEditPatch(original, explicitDateClears = setOf(field))
            val edit =
                when (field) {
                    "due" -> patch.due
                    "wait" -> patch.wait
                    else -> patch.scheduled
                }
            val mode =
                when (field) {
                    "due" -> patch.dateModes.due
                    "wait" -> patch.dateModes.wait
                    else -> patch.dateModes.scheduled
                }
            assertEquals(StringEdit.Replace("broken", null), edit)
            assertEquals(DateModeEdit.Replace("v99:future", DateDisplayMode.CALENDAR_DATE), mode)
        }
    }

    @Test fun derivedWaitInheritsEarliestSourceModeAndChecksBothMarkers() {
        val original = task().copy(scheduled = "2030-01-02T00:00:00Z")
        val patch =
            original.copy(
                due = "2030-01-02T00:00:00Z",
                dateModes = calendarDateModes().copy(due = DateDisplayMode.LOCAL_DATE_TIME),
            ).toEditPatch(
                original,
                autoWaiting = true,
            )
        assertEquals(StringEdit.Replace(null, "2029-12-26T00:00:00Z"), patch.wait)
        assertEquals(DateModeEdit.Replace(null, DateDisplayMode.LOCAL_DATE_TIME), patch.dateModes.wait)
        assertEquals(DateModeEdit.Check(null), patch.dateModes.scheduled)
        val cleared =
            original.copy(due = "2030-01-03T00:00:00Z").toEditPatch(
                original,
                autoWaiting = true,
                explicitDateClears = setOf("wait"),
            )
        assertEquals(StringEdit.Replace(null, null), cleared.wait)
    }

    @Test fun autoWaitSourceChecksDoNotClearUntouchedRawDatesOrUnknownMarkers() {
        for (raw in listOf(null, "malformed-scheduled", "2030-01-03T00:00:45Z")) {
            val original =
                task().copy(
                    nativeDates = raw?.let { mapOf("scheduled" to it) }.orEmpty(),
                    udas = mapOf("fatto_display_scheduled" to "v99:future"),
                )
            val patch = original.copy(due = "2030-02-01T00:00:00Z").toEditPatch(original, autoWaiting = true)
            assertFalse("An untouched source must be checked without replacement: $raw", patch.scheduled is StringEdit.Replace)
            assertEquals(DateModeEdit.Check("v99:future"), patch.dateModes.scheduled)
            assertEquals(StringEdit.Replace(null, "2030-01-25T00:00:00Z"), patch.wait)
        }
    }

    @Test fun autoWaitDoesNotOverwriteAnUntouchedUnreadableNativeWait() {
        val original =
            task().copy(
                nativeDates = mapOf("wait" to "unreadable-wait"),
                udas = mapOf("fatto_display_wait" to "v99:preserve"),
            )
        val patch = original.copy(due = "2030-02-01T00:00:00Z").toEditPatch(original, autoWaiting = true)
        assertEquals(StringEdit.Unchanged, patch.wait)
        assertEquals(DateModeEdit.Unchanged, patch.dateModes.wait)
    }

    @Test fun timestampEditOnlyChecksTheCanonicalMarker() {
        val original =
            task().copy(
                udas = mapOf("fatto_display_due" to "v1:local-datetime"),
                dateModes = calendarDateModes().copy(due = DateDisplayMode.LOCAL_DATE_TIME),
            )
        val edited = original.copy(due = "2030-01-02T00:00:45Z").toEditPatch(original)
        assertEquals(DateModeEdit.Check("v1:local-datetime"), edited.dateModes.due)
        assertEquals(DateModeEdit.Unchanged, original.copy(description = "Unrelated").toEditPatch(original).dateModes.due)
    }

    @Test fun obsoleteNamesRemainVisibleAsOrdinaryUdas() {
        val original =
            task().copy(
                udas =
                    mapOf(
                        "fatto_display_due" to "v1:local-datetime",
                        "fatto_due_display" to "v1:local-datetime", "fatto_display_wait" to "v99:future", "custom" to "value",
                    ),
            )
        assertEquals(original.udas - "fatto_display_due", original.visibleUdas)
        val edited = original.copy(due = "2030-01-02T00:00:00Z").toEditPatch(original)
        assertEquals(DateModeEdit.Check("v1:local-datetime"), edited.dateModes.due)
    }
}
