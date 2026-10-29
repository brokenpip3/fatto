package com.brokenpip3.fatto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.brokenpip3.fatto.data.calendar.ExportDisposition
import com.brokenpip3.fatto.data.calendar.ExportRunResult
import com.brokenpip3.fatto.worker.CalendarExportWorker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class CalendarExportWorkerTest {
    @Test fun waitReleaseIsScheduledAfterSuccessfulPassAndTransientFailureRetries() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val boundary = Instant.parse("2026-11-01T10:00:00Z")
            var outcome = ExportRunResult(ExportDisposition.SUCCESS, boundary)
            var scheduled: Instant? = null
            val factory =
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker? =
                        if (workerClassName == CalendarExportWorker::class.java.name) {
                            CalendarExportWorker(appContext, workerParameters, { outcome }, { scheduled = it.nextRelease })
                        } else {
                            null
                        }
                }

            fun worker() = TestListenableWorkerBuilder<CalendarExportWorker>(context).setWorkerFactory(factory).build()
            assertEquals(ListenableWorker.Result.success(), worker().doWork())
            assertEquals(boundary, scheduled)
            outcome = ExportRunResult(ExportDisposition.RETRY)
            assertEquals(ListenableWorker.Result.retry(), worker().doWork())
            outcome = ExportRunResult(ExportDisposition.PAUSED)
            assertEquals(ListenableWorker.Result.success(), worker().doWork())
        }
}
