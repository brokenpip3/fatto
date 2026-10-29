package com.brokenpip3.fatto

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.brokenpip3.fatto.data.SettingsRepositoryImpl
import com.brokenpip3.fatto.data.TaskRepository
import com.brokenpip3.fatto.data.calendar.CalendarChange
import com.brokenpip3.fatto.data.calendar.CalendarExportReconciler
import com.brokenpip3.fatto.data.calendar.CalendarExportStore
import com.brokenpip3.fatto.data.calendar.EventKey
import com.brokenpip3.fatto.data.calendar.NativeCalendarTaskSnapshotSource
import com.brokenpip3.fatto.data.model.calendarDateModes
import com.brokenpip3.fatto.data.model.toEditPatch
import com.brokenpip3.fatto.data.model.withField
import com.brokenpip3.fatto.worker.CalendarExportWorker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.taskchampion_android.DateDisplayMode
import uniffi.taskchampion_android.TaskAddProps
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class CalendarExportEndToEndTest {
    @get:Rule val permissions = GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    @Test fun nativeTaskThroughWorkerUpdatesSameCalendarEventAndCompletionPreservesUnrelatedEvent() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val directory = File(context.cacheDir, "calendar-e2e-${System.nanoTime()}").apply { mkdirs() }
            val store = CalendarExportStore(context, "calendar-e2e-${System.nanoTime()}")
            val repository = TaskRepository(context, SettingsRepositoryImpl(context), directory, requestCalendarExport = {})
            CalendarProviderFixture(context).use { fixture ->
                store.activate()
                store.updateConfig(true, true, fixture.target)
                var now = Instant.parse("2050-01-01T10:00:00Z")
                var release: Instant? = null
                val factory =
                    object : WorkerFactory() {
                        override fun createWorker(
                            appContext: Context,
                            workerClassName: String,
                            workerParameters: WorkerParameters,
                        ): ListenableWorker? =
                            if (workerClassName == CalendarExportWorker::class.java.name) {
                                CalendarExportWorker(appContext, workerParameters, {
                                    CalendarExportReconciler(
                                        store, fixture.gateway, NativeCalendarTaskSnapshotSource(context, directory),
                                        Clock.fixed(now, ZoneOffset.UTC), { ZoneId.of("UTC") },
                                    ).reconcile()
                                }, { release = it.nextRelease })
                            } else {
                                null
                            }
                    }

                suspend fun export() {
                    val worker = TestListenableWorkerBuilder<CalendarExportWorker>(context).setWorkerFactory(factory).build()
                    assertEquals(ListenableWorker.Result.success(), worker.doWork())
                }
                try {
                    repository.init()
                    repository.addTask(
                        TaskAddProps(
                            "Original task", null, emptyList(), null, "2050-01-01T11:00:00Z", "2050-01-02T14:00:00Z",
                            null, null, emptyList(),
                            calendarDateModes().withField("scheduled", DateDisplayMode.LOCAL_DATE_TIME)
                                .withField("wait", DateDisplayMode.LOCAL_DATE_TIME),
                        ),
                    )
                    export()
                    assertTrue(fixture.gateway.read(fixture.target).isEmpty())
                    assertEquals(Instant.parse("2050-01-01T11:00:00Z"), release)
                    now = Instant.parse("2050-01-01T11:00:00Z")
                    export()
                    val originalEvent = fixture.gateway.read(fixture.target).single()
                    assertFalse(originalEvent.event.allDay)
                    assertEquals(1_800_000L, originalEvent.event.endMillis - originalEvent.event.startMillis)
                    val task = repository.tasks.value.single()
                    repository.updateTask(
                        task.copy(
                            description = "Updated task", scheduled = "2050-01-03T00:00:00Z",
                            dateModes = task.dateModes.withField("scheduled", DateDisplayMode.CALENDAR_DATE),
                        ).toEditPatch(task),
                    )
                    export()
                    val updated = fixture.gateway.read(fixture.target).single()
                    assertEquals(originalEvent.id, updated.id)
                    assertEquals("Updated task", updated.event.title)
                    assertTrue(updated.event.allDay)
                    assertEquals(Instant.parse("2050-01-03T00:00:00Z").toEpochMilli(), updated.event.startMillis)
                    val foreign = updated.event.copy(key = EventKey("unrelated", "other"), title = "Keep me")
                    fixture.gateway.apply(fixture.target, listOf(CalendarChange.Insert(foreign)))
                    repository.completeTask(task.uuid, sync = false)
                    export()
                    assertEquals("Keep me", fixture.gateway.read(fixture.target).single().event.title)
                } finally {
                    store.close()
                    directory.deleteRecursively()
                }
            }
            Unit
        }
}
