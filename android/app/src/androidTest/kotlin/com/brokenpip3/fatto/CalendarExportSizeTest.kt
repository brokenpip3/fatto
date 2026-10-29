package com.brokenpip3.fatto

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.brokenpip3.fatto.data.calendar.CalendarExportReconciler
import com.brokenpip3.fatto.data.calendar.CalendarExportStore
import com.brokenpip3.fatto.data.calendar.CalendarTaskSnapshotSource
import com.brokenpip3.fatto.data.calendar.ExportDisposition
import com.brokenpip3.fatto.data.model.Task
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.taskchampion_android.TaskStatus
import java.time.Clock
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class CalendarExportSizeTest {
    @get:Rule val permissions = GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    @Test fun oversizedTaskPausesWithIdentificationAndRemedyAndRecoversAfterEdit() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val store = CalendarExportStore(context, "calendar-size-${System.nanoTime()}")
            CalendarProviderFixture(context).use { fixture ->
                store.activate()
                store.updateConfig(true, true, fixture.target)
                var task =
                    Task(
                        "oversized-task-uuid", "x".repeat(200000), TaskStatus.Pending, emptyList(), null, null, null, null,
                        "2050-01-01T00:00:00Z", null, null, 0f, false, false, emptyList(), emptyMap(),
                    )
                val reconciler =
                    CalendarExportReconciler(
                        store, fixture.gateway, CalendarTaskSnapshotSource { listOf(task) },
                        Clock.systemUTC(), { ZoneId.of("UTC") },
                    )
                try {
                    assertEquals(ExportDisposition.PAUSED, reconciler.reconcile().disposition)
                    val message = store.readStatus().message.orEmpty()
                    assertTrue(message.contains("oversized-task-uuid"))
                    assertTrue(message.contains("Shorten"))
                    assertTrue(fixture.gateway.read(fixture.target).isEmpty())
                    task = task.copy(description = "Short title")
                    assertEquals(ExportDisposition.SUCCESS, reconciler.reconcile().disposition)
                    assertEquals("Short title", fixture.gateway.read(fixture.target).single().event.title)
                } finally {
                    store.close()
                }
            }
        }
}
