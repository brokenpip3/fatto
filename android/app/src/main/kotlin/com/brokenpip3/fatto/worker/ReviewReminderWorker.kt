package com.brokenpip3.fatto.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.brokenpip3.fatto.MainActivity
import com.brokenpip3.fatto.R
import com.brokenpip3.fatto.data.SettingsRepositoryImpl
import com.brokenpip3.fatto.data.review.ReviewPreferenceLock
import com.brokenpip3.fatto.data.review.ReviewReminderPolicy
import com.brokenpip3.fatto.notification.NotificationNavigation
import com.brokenpip3.fatto.notification.ReviewReminderScheduler
import java.time.Instant

class ReviewReminderWorker
    @JvmOverloads
    constructor(
        context: Context,
        parameters: WorkerParameters,
        private val canNotify: (Context) -> Boolean = { ReviewReminderScheduler.notificationsEnabled(it) },
    ) : CoroutineWorker(context, parameters) {
        override suspend fun doWork(): Result =
            synchronized(ReviewPreferenceLock.lock) {
                try {
                    val preferences = SettingsRepositoryImpl(applicationContext)
                    val days = preferences.readReviewSettings().reminderDays ?: return@synchronized Result.success()
                    val runtime = preferences.getReviewReminderState()
                    val due = runtime.nextDueEpochMillis ?: return@synchronized Result.success()
                    val now = Instant.now()
                    if (due > now.toEpochMilli() || isStopped) return@synchronized Result.success()
                    val notifications = NotificationManagerCompat.from(applicationContext)
                    if (
                        Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(
                            applicationContext,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return@synchronized Result.success()
                    }
                    val manager = applicationContext.getSystemService(NotificationManager::class.java)
                    manager.createNotificationChannel(
                        NotificationChannel(
                            ReviewReminderScheduler.CHANNEL_ID,
                            "Review reminders", NotificationManager.IMPORTANCE_DEFAULT,
                        ),
                    )
                    if (!canNotify(applicationContext)) return@synchronized Result.success()
                    val intent =
                        Intent(applicationContext, MainActivity::class.java)
                            .setData(Uri.parse("fatto://review"))
                            .putExtra(NotificationNavigation.EXTRA_OPEN_REVIEW, true)
                    val pendingIntent =
                        PendingIntent.getActivity(
                            applicationContext, ReviewReminderScheduler.NOTIFICATION_ID, intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                    val notification =
                        NotificationCompat.Builder(applicationContext, ReviewReminderScheduler.CHANNEL_ID)
                            .setSmallIcon(R.mipmap.ic_launcher).setContentTitle("Time to review your tasks")
                            .setContentText("Take a fresh look at your task list.").setContentIntent(pendingIntent)
                            .setAutoCancel(true).setOnlyAlertOnce(true).build()
                    notifications.notify(ReviewReminderScheduler.NOTIFICATION_ID, notification)
                    preferences.setReviewReminderState(
                        runtime.copy(nextDueEpochMillis = ReviewReminderPolicy.nextDue(now, days).toEpochMilli()),
                    )
                    Result.success()
                } catch (_: SecurityException) {
                    Result.success()
                } catch (_: Exception) {
                    Result.retry()
                }
            }
    }
