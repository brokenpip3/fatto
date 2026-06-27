package com.brokenpip3.fatto.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.brokenpip3.fatto.data.SettingsRepositoryImpl
import com.brokenpip3.fatto.data.TaskRepository
import com.brokenpip3.fatto.worker.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CompleteTaskNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != NotificationNavigation.ACTION_COMPLETE_TASK) return

        val uuid = intent.getStringExtra(NotificationNavigation.EXTRA_TASK_UUID) ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settingsRepository = SettingsRepositoryImpl(context.applicationContext)
                val taskRepository = TaskRepository(context.applicationContext, settingsRepository)
                taskRepository.init()
                taskRepository.completeTask(uuid, sync = false)
                val notificationManager = NotificationManagerCompat.from(context)
                notificationManager.cancel(NotificationNavigation.notificationTag(uuid), NotificationNavigation.TASK_NOTIFICATION_ID)
                notificationManager.cancel(NotificationNavigation.SUMMARY_NOTIFICATION_ID)
                enqueueSync(context.applicationContext)
            } catch (e: Exception) {
                Log.e("CompleteTaskNotificationReceiver", "Failed to complete task from notification", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun enqueueSync(context: Context) {
        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        val request =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
