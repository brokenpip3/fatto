package com.brokenpip3.fatto.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.brokenpip3.fatto.data.SettingsRepositoryImpl
import uniffi.taskchampion_android.ReplicaWrapper
import java.io.File

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val settingsRepository = SettingsRepositoryImpl(applicationContext)
        val creds = settingsRepository.getCredentials() ?: return Result.success()

        return try {
            val path = File(applicationContext.filesDir, "taskchampion").absolutePath
            val replica = ReplicaWrapper.newOnDisk(path)

            Log.d("SyncWorker", "Starting background sync...")
            replica.sync(creds.url, creds.clientId, creds.secret)
            Log.d("SyncWorker", "Background sync completed successfully.")

            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Background sync failed", e)
            Result.retry()
        }
    }
}
