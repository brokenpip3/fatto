package com.brokenpip3.fatto.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.brokenpip3.fatto.data.calendar.CalendarExportRuntime
import com.brokenpip3.fatto.data.calendar.CalendarExportScheduler
import com.brokenpip3.fatto.data.calendar.ExportDisposition
import com.brokenpip3.fatto.data.calendar.ExportRunResult

class CalendarExportWorker
    @JvmOverloads
    constructor(
        context: Context,
        parameters: WorkerParameters,
        private val export: suspend () -> ExportRunResult = { CalendarExportRuntime.reconciler(context).reconcile() },
        private val completed: (ExportRunResult) -> Unit = { result ->
            CalendarExportScheduler.reschedule(context, result.nextRelease)
            if (CalendarExportRuntime.store(context).readRequestedGeneration() > result.processedRequestGeneration) {
                CalendarExportScheduler.request(context)
            }
        },
    ) : CoroutineWorker(context, parameters) {
        override suspend fun doWork(): Result {
            val result = export()
            completed(result)
            return if (result.disposition == ExportDisposition.RETRY) Result.retry() else Result.success()
        }
    }
