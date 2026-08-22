package com.brokenpip3.fatto.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.brokenpip3.fatto.data.SettingsRepositoryImpl
import com.brokenpip3.fatto.data.Syncer
import com.brokenpip3.fatto.widget.WidgetRefreshReceiver
import uniffi.taskchampion_android.ReplicaWrapper
import java.io.File

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val settingsRepository = SettingsRepositoryImpl(applicationContext)
        if (!settingsRepository.hasCredentials()) return Result.success()

        return try {
            val path = File(applicationContext.filesDir, "taskchampion").absolutePath
            val replica = ReplicaWrapper.newOnDisk(path)

            Log.d("SyncWorker", "Starting background sync...")
            Syncer.sync(replica, settingsRepository)
            Log.d("SyncWorker", "Background sync completed successfully.")

            // Task data may have changed: refresh the home-screen widget.
            WidgetRefreshReceiver.sendRefresh(applicationContext)

            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Background sync failed", e)
            Result.retry()
        }
    }
}
