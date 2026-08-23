package com.brokenpip3.fatto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.brokenpip3.fatto.data.SettingsRepositoryImpl
import com.brokenpip3.fatto.widget.TaskListWidgetReceiver
import com.brokenpip3.fatto.widget.WidgetRefreshReceiver
import com.brokenpip3.fatto.worker.SyncWorker
import com.brokenpip3.fatto.worker.TaskListWorker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Verifies the widget refresh wiring:
 * - the one-time refresh must not cancel the periodic refresh (they are
 *   enqueued under the same unique name today, and WorkManager unique names
 *   are shared between one-time and periodic work),
 * - removing the last widget instance must cancel the periodic refresh,
 * - a successful background sync must trigger a widget refresh.
 *
 * The work names below are literals on purpose: they are the observable
 * WorkManager contract and must match the constants in
 * [TaskListWorker.Companion].
 */
@RunWith(AndroidJUnit4::class)
class WidgetRefreshTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @Test
    fun testOneTimeRefreshDoesNotCancelPeriodicRefresh() {
        TaskListWorker.schedulePeriodic(context)
        TaskListWorker.enqueueOneTime(context)

        val periodicInfos =
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork("task-list-widget-refresh-periodic")
                .get()
        val oneTimeInfos =
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork("task-list-widget-refresh-once")
                .get()

        assertTrue(
            "periodic refresh must survive a one-time refresh (same-name REPLACE cancels it)",
            periodicInfos.isNotEmpty(),
        )
        assertTrue("one-time refresh work must be enqueued", oneTimeInfos.isNotEmpty())
    }

    @Test
    fun testRemovingLastWidgetCancelsPeriodicRefresh() {
        TaskListWorker.schedulePeriodic(context)

        // Simulates the onDeleted path (Glance's own onDeleted cannot be invoked
        // directly: it relies on BroadcastReceiver.goAsync(), which crashes outside
        // a real broadcast dispatch).
        TaskListWidgetReceiver().cancelPeriodicIfNoWidgetsRemain(context)

        val periodicInfos =
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork("task-list-widget-refresh-periodic")
                .get()
        assertTrue(
            "periodic refresh must have been scheduled before the widget was removed",
            periodicInfos.isNotEmpty(),
        )
        assertTrue(
            "periodic refresh must be cancelled once the widget is removed",
            periodicInfos.none { it.state == WorkInfo.State.ENQUEUED },
        )
    }

    /**
     * Requires the local taskchampion sync server (`just sync-up`) reachable at
     * http://10.0.2.2:8080 (the emulator alias for the host loopback).
     */
    @Test
    fun testSyncWorkerSendsWidgetRefreshAfterSuccessfulSync() =
        runBlocking {
            val settings = SettingsRepositoryImpl(context)
            settings.saveCredentials(
                "http://10.0.2.2:8080",
                "3f9a1c2e-5b4d-4a6e-8f7a-2c1d3e4f5a6b",
                "widget-test-secret",
            )

            val latch = CountDownLatch(1)
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        c: Context?,
                        i: Intent?,
                    ) {
                        latch.countDown()
                    }
                }
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(WidgetRefreshReceiver.ACTION_WIDGET_REFRESH),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )

            try {
                val worker = TestListenableWorkerBuilder<SyncWorker>(context).build()
                val result = worker.doWork()

                assertEquals(ListenableWorker.Result.success(), result)
                assertTrue(
                    "SyncWorker must send WIDGET_REFRESH after a successful sync",
                    latch.await(10, TimeUnit.SECONDS),
                )
            } finally {
                context.unregisterReceiver(receiver)
                settings.clearCredentials()
            }
        }
}
