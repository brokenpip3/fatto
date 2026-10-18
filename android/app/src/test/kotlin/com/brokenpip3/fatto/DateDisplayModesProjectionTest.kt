package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.model.calendarDateModes
import com.brokenpip3.fatto.data.model.toEditPatch
import com.brokenpip3.fatto.data.model.toModel
import org.junit.Assert.assertEquals
import org.junit.Test
import uniffi.taskchampion_android.DateDisplayMode
import uniffi.taskchampion_android.DateModeEdit
import uniffi.taskchampion_android.StringEdit
import uniffi.taskchampion_android.TaskData
import uniffi.taskchampion_android.TaskStatus
import uniffi.taskchampion_android.UdaPair

class DateDisplayModesProjectionTest {
    @Test fun projectedModesKeepRawConflictBaselinesThroughAnUnrelatedEditAndExplicitOptOut() {
        val native =
            TaskData(
                uuid = "test", description = "Midnight", status = TaskStatus.Pending, tags = emptyList(),
                due = "2030-01-02T00:00:00Z", entry = null, project = null, wait = null, scheduled = null, start = null,
                priority = null, urgency = 0f, isBlocked = false, isBlocking = false, dependencies = emptyList(),
                udas = listOf(UdaPair("fatto_display_due", "v1:local-datetime"), UdaPair("fatto_display_wait", "v99:future")),
                annotations = emptyList(), recurrence = null, nativeDates = listOf(UdaPair("due", "1893542400")), modified = null,
                dateModes = calendarDateModes().copy(due = DateDisplayMode.LOCAL_DATE_TIME),
            )
        val original = native.toModel()
        assertEquals(DateDisplayMode.LOCAL_DATE_TIME, original.dateModes.due)
        assertEquals("v99:future", original.udas["fatto_display_wait"])
        val title = original.copy(description = "Edited title").toEditPatch(original)
        assertEquals(StringEdit.Unchanged, title.due)
        assertEquals(DateModeEdit.Unchanged, title.dateModes.due)
        assertEquals(DateModeEdit.Unchanged, title.dateModes.wait)
        val optedOut = original.copy(dateModes = calendarDateModes()).toEditPatch(original)
        assertEquals(StringEdit.Replace("1893542400", native.due), optedOut.due)
        assertEquals(
            DateModeEdit.Replace("v1:local-datetime", DateDisplayMode.CALENDAR_DATE),
            optedOut.dateModes.due,
        )
    }
}
