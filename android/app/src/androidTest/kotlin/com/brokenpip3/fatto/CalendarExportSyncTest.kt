package com.brokenpip3.fatto

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.brokenpip3.fatto.data.SettingsRepositoryImpl
import com.brokenpip3.fatto.data.SyncCredentials
import com.brokenpip3.fatto.data.SyncType
import com.brokenpip3.fatto.data.calendar.CalendarExportController
import com.brokenpip3.fatto.data.calendar.CalendarExportRuntime
import com.brokenpip3.fatto.data.calendar.CalendarExportScheduler
import com.brokenpip3.fatto.data.model.calendarDateModes
import com.brokenpip3.fatto.worker.SyncWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.taskchampion_android.ReplicaWrapper
import uniffi.taskchampion_android.TaskAddProps
import uniffi.taskchampion_android.TaskStatus
import java.io.File
import java.util.UUID

/** Uses the same local sync server as WidgetRefreshTest; no external cloud account. */
@RunWith(AndroidJUnit4::class)
class CalendarExportSyncTest {
    @get:Rule val permissions = GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    private suspend fun awaitCondition(condition: suspend () -> Boolean) {
        repeat(200) {
            if (condition()) return
            delay(100)
        }
        error("Incoming task changes were not exported")
    }

    @Test fun backgroundSyncPublishesAndRemovesEventsReceivedFromAnotherReplica() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val settings = SettingsRepositoryImpl(context)
            val oldType = settings.getSyncType()
            val oldServer = settings.getCredentials()
            val oldS3 = settings.getS3Credentials()
            val store = CalendarExportRuntime.store(context)
            val oldConfig = store.readConfig()
            val oldStatus = store.readStatus()
            assertFalse(oldConfig.enabled)
            WorkManagerTestInitHelper.initializeTestWorkManager(context)
            val credentials = SyncCredentials("http://10.0.2.2:8080", UUID.randomUUID().toString(), "calendar-sync-test")
            val directory = File(context.cacheDir, "calendar-remote-${System.nanoTime()}")
            val remote = ReplicaWrapper.newOnDisk(directory.absolutePath)
            CalendarProviderFixture(context).use { fixture ->
                val controller = CalendarExportController(store) { CalendarExportScheduler.request(context) }
                try {
                    assertTrue(settings.replaceSyncSettings(SyncType.SERVER, credentials, null))
                    val task =
                        remote.addTask(
                            TaskAddProps(
                                "Remote calendar task", null, emptyList(), null, null,
                                "2050-01-01T00:00:00Z", null, null, emptyList(), calendarDateModes(),
                            ),
                        )
                    remote.sync(credentials.url, credentials.clientId, credentials.secret)
                    controller.enable(fixture.target)
                    assertEquals(ListenableWorker.Result.success(), TestListenableWorkerBuilder<SyncWorker>(context).build().doWork())
                    awaitCondition { fixture.gateway.read(fixture.target).any { it.key?.taskUuid == task.uuid } }
                    remote.transitionOccurrence(task.uuid, TaskStatus.Completed, false)
                    remote.sync(credentials.url, credentials.clientId, credentials.secret)
                    assertEquals(ListenableWorker.Result.success(), TestListenableWorkerBuilder<SyncWorker>(context).build().doWork())
                    awaitCondition { fixture.gateway.read(fixture.target).none { it.key?.taskUuid == task.uuid } }
                    controller.disable(true)
                    awaitCondition { fixture.gateway.read(fixture.target).isEmpty() && store.cleanupTargets().isEmpty() }
                } finally {
                    WorkManager.getInstance(context).cancelAllWork().result.get()
                    store.updateConfig(oldConfig.enabled, oldConfig.respectWait, oldConfig.target)
                    store.writeStatus(oldStatus)
                    settings.replaceSyncSettings(oldType, oldServer, oldS3)
                    remote.destroy()
                    directory.deleteRecursively()
                }
            }
            Unit
        }
}
