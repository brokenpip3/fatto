package com.brokenpip3.fatto.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.brokenpip3.fatto.MainActivity
import com.brokenpip3.fatto.data.SettingsRepositoryImpl
import com.brokenpip3.fatto.data.TaskRepository
import com.brokenpip3.fatto.data.model.Task
import uniffi.taskchampion_android.TaskStatus

class DailyNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val settingsRepository = SettingsRepositoryImpl(applicationContext)
        if (!settingsRepository.getDailyNotificationsEnabled()) {
            Log.d("DailyNotificationWorker", "Daily notifications are disabled in settings")
            return Result.success()
        }

        return try {
            val taskRepository = TaskRepository(applicationContext, settingsRepository)
            taskRepository.init()

            val includeDue = settingsRepository.getIncludeDueToday()
            val includeScheduled = settingsRepository.getIncludeScheduledToday()
            val includeOverdue = settingsRepository.getIncludeOverdue()

            val tasksToNotify =
                taskRepository.tasks.value.filter { task ->
                    if (task.status != TaskStatus.PENDING) return@filter false

                    val isDueToday = includeDue && com.brokenpip3.fatto.data.DateTimeUtils.isToday(task.due)
                    val isScheduledToday = includeScheduled && com.brokenpip3.fatto.data.DateTimeUtils.isToday(task.scheduled)
                    val isOverdue = includeOverdue && com.brokenpip3.fatto.data.DateTimeUtils.isOverdue(task.due)

                    isDueToday || isScheduledToday || isOverdue
                }

            Log.d("DailyNotificationWorker", "Found ${tasksToNotify.size} tasks to notify")
            if (tasksToNotify.isNotEmpty()) {
                showNotifications(tasksToNotify)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("DailyNotificationWorker", "Failed to generate daily notification", e)
            Result.failure()
        }
    }

    private fun showNotifications(tasks: List<Task>) {
        val channelId = "daily_summaries"
        val channelName = "Daily Summaries"
        val groupKey = "com.brokenpip3.fatto.TASKS"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, channelName, importance)
            val notificationManager: NotificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notificationManager = NotificationManagerCompat.from(applicationContext)
        val pendingIntent = getPendingIntent()

        // Individual Task Notifications
        tasks.forEach { task ->
            val builder =
                NotificationCompat.Builder(applicationContext, channelId)
                    .setSmallIcon(com.brokenpip3.fatto.R.mipmap.ic_launcher)
                    .setContentTitle("Task Reminder")
                    .setContentText(task.description)
                    .setGroup(groupKey)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)

            try {
                notificationManager.notify(task.uuid.hashCode(), builder.build())
            } catch (e: SecurityException) {
                Log.e("DailyNotificationWorker", "SecurityException: Notification permission not granted", e)
            }
        }

        // Group Summary Notification
        val summaryBuilder =
            NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(com.brokenpip3.fatto.R.mipmap.ic_launcher)
                .setContentTitle("Daily Summary")
                .setContentText("You have ${tasks.size} tasks for today")
                .setGroup(groupKey)
                .setGroupSummary(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

        try {
            notificationManager.notify(0, summaryBuilder.build())
        } catch (e: SecurityException) {
            Log.e("DailyNotificationWorker", "SecurityException: Notification permission not granted", e)
        }
    }

    private fun getPendingIntent(): PendingIntent {
        val intent =
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        return PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
