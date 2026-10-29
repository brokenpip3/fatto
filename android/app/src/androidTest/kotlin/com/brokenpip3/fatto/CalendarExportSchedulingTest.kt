package com.brokenpip3.fatto

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.brokenpip3.fatto.data.calendar.CalendarExportController
import com.brokenpip3.fatto.data.calendar.CalendarExportReconciler
import com.brokenpip3.fatto.data.calendar.CalendarExportRuntime
import com.brokenpip3.fatto.data.calendar.CalendarExportScheduler
import com.brokenpip3.fatto.data.calendar.CalendarTaskSnapshotSource
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.calendarDateModes
import com.brokenpip3.fatto.data.model.withField
import com.brokenpip3.fatto.worker.CalendarExportTimeReceiver
import com.brokenpip3.fatto.worker.CalendarExportWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.taskchampion_android.DateDisplayMode
import uniffi.taskchampion_android.TaskStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class CalendarExportSchedulingTest {
    @get:Rule val permissions = GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    private suspend fun awaitCondition(condition: suspend () -> Boolean) {
        repeat(150) {
            if (condition()) return
            delay(100)
        }
        error("Calendar scheduling condition was not reached")
    }

    @Test fun realWorkManagerDelayAndClockChangeTriggersReleaseAndHideWaitingTask() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val store = CalendarExportRuntime.store(context)
            val previous = store.readConfig()
            val previousStatus = store.readStatus()
            assertFalse("Test requires export to be off", previous.enabled)
            CalendarProviderFixture(context).use { fixture ->
                var now = Instant.parse("2050-01-01T10:00:00Z")
                val task =
                    Task(
                        "timer-task", "Timed release", TaskStatus.Pending, emptyList(), null, null, null,
                        "2050-01-01T11:00:00Z", "2050-01-02T14:00:00Z", null, null, 0f, false, false, emptyList(), emptyMap(),
                        dateModes =
                            calendarDateModes().withField("wait", DateDisplayMode.LOCAL_DATE_TIME)
                                .withField("scheduled", DateDisplayMode.LOCAL_DATE_TIME),
                    )
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
                                        store, fixture.gateway, CalendarTaskSnapshotSource { listOf(task) },
                                        Clock.fixed(now, ZoneOffset.UTC), { ZoneId.of("UTC") },
                                    ).reconcile()
                                })
                            } else {
                                null
                            }
                    }
                WorkManagerTestInitHelper.initializeTestWorkManager(
                    context,
                    Configuration.Builder().setExecutor(SynchronousExecutor())
                        .setWorkerFactory(factory).build(),
                )
                val manager = WorkManager.getInstance(context)
                val controller = CalendarExportController(store) { CalendarExportScheduler.request(context) }
                try {
                    controller.enable(fixture.target)
                    awaitCondition {
                        manager.getWorkInfosForUniqueWork("fatto-calendar-release").get().any { it.state == WorkInfo.State.ENQUEUED }
                    }
                    assertTrue(fixture.gateway.read(fixture.target).isEmpty())
                    val release =
                        manager.getWorkInfosForUniqueWork("fatto-calendar-release").get().first {
                            it.state == WorkInfo.State.ENQUEUED
                        }
                    now = Instant.parse("2050-01-01T11:00:00Z")
                    WorkManagerTestInitHelper.getTestDriver(context)!!.setInitialDelayMet(release.id)
                    awaitCondition { fixture.gateway.read(fixture.target).size == 1 }
                    now = Instant.parse("2050-01-01T10:00:00Z")
                    CalendarExportTimeReceiver().onReceive(context, Intent(Intent.ACTION_TIME_CHANGED))
                    awaitCondition { fixture.gateway.read(fixture.target).isEmpty() }
                    now = Instant.parse("2050-01-01T11:00:00Z")
                    CalendarExportTimeReceiver().onReceive(context, Intent(Intent.ACTION_TIME_CHANGED))
                    awaitCondition { fixture.gateway.read(fixture.target).size == 1 }
                    controller.disable(true)
                    awaitCondition { fixture.gateway.read(fixture.target).isEmpty() && store.cleanupTargets().isEmpty() }
                } finally {
                    manager.cancelAllWork().result.get()
                    store.updateConfig(previous.enabled, previous.respectWait, previous.target)
                    store.writeStatus(previousStatus)
                }
            }
        }
}
