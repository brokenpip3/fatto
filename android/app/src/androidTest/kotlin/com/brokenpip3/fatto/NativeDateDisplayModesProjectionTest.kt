package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.model.toEditPatch
import com.brokenpip3.fatto.data.model.toModel
import org.junit.Assert.assertEquals
import org.junit.Test
import uniffi.taskchampion_android.DateDisplayMode
import uniffi.taskchampion_android.DateDisplayModes
import uniffi.taskchampion_android.ReplicaWrapper
import uniffi.taskchampion_android.TaskAddProps

@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
class NativeDateDisplayModesProjectionTest {
    @Test
    fun nativeTimedMidnightSurvivesModelMappingAndAnUnrelatedEdit() {
        ReplicaWrapper.newInMemory().use { replica ->
            val native =
                replica.addTask(
                    TaskAddProps(
                        description = "Explicit midnight",
                        project = null,
                        tags = emptyList(),
                        due = "2030-01-02T00:00:00Z",
                        wait = null,
                        scheduled = null,
                        start = null,
                        priority = null,
                        dependencies = emptyList(),
                        dateModes =
                            DateDisplayModes(
                                DateDisplayMode.LOCAL_DATE_TIME,
                                DateDisplayMode.CALENDAR_DATE,
                                DateDisplayMode.CALENDAR_DATE,
                            ),
                    ),
                )
            val original = native.toModel()
            assertEquals(DateDisplayMode.LOCAL_DATE_TIME, original.dateModes.due)
            replica.updateTask(original.copy(description = "Edited title").toEditPatch(original))
            val updated = replica.getTask(native.uuid)!!.toModel()
            assertEquals("Edited title", updated.description)
            assertEquals(native.due, updated.due)
            assertEquals(DateDisplayMode.LOCAL_DATE_TIME, updated.dateModes.due)
            assertEquals("v1:local-datetime", updated.udas["fatto_display_due"])
        }
    }

    @Test
    fun autoWaitChecksAnAbsentSourceAcrossTheActualNativeBoundary() {
        ReplicaWrapper.newInMemory().use { replica ->
            val original =
                replica.addTask(
                    TaskAddProps(
                        "Auto-wait native guard", null, emptyList(), "2099-01-02T00:00:00Z", null, null, null,
                        null, emptyList(), com.brokenpip3.fatto.data.model.calendarDateModes(),
                    ),
                ).toModel()
            val patch = original.copy(due = "2099-02-01T00:00:00Z").toEditPatch(original, autoWaiting = true)
            assertEquals(uniffi.taskchampion_android.StringEdit.Check(null), patch.scheduled)
            replica.updateTask(patch)
            val saved = replica.getTask(original.uuid)!!.toModel()
            assertEquals(java.time.Instant.parse("2099-01-25T00:00:00Z"), java.time.Instant.parse(saved.wait))
            assertEquals(null, saved.scheduled)
            assertEquals(original.dateModes.scheduled, saved.dateModes.scheduled)
            assertEquals(original.udas, saved.udas)
        }
    }
}
