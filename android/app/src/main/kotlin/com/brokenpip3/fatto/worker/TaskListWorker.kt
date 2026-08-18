package com.brokenpip3.fatto.worker

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.brokenpip3.fatto.widget.TaskListWidget
import java.util.concurrent.TimeUnit

/**
 * Refreshes the "Next Tasks" home-screen widget. Used both for the 30-minute
 * periodic fallback and for one-time refreshes triggered by task mutations.
 * No-ops when no widget instance is placed on any launcher.
 */
class TaskListWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return try {
            val manager = GlanceAppWidgetManager(applicationContext)
            if (manager.getGlanceIds(TaskListWidget::class.java).isEmpty()) {
                Log.d("TaskListWorker", "No widget placed, skipping refresh")
                return Result.success()
            }
            TaskListWidget().updateAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Log.e("TaskListWorker", "Failed to refresh widget", e)
            Result.failure()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "task-list-widget-refresh"

        fun enqueueOneTime(context: Context) {
            val request = OneTimeWorkRequestBuilder<TaskListWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun schedulePeriodic(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<TaskListWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
