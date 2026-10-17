package com.brokenpip3.fatto.notification

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.brokenpip3.fatto.data.SettingsRepository
import com.brokenpip3.fatto.data.review.ReviewPreferenceLock
import com.brokenpip3.fatto.data.review.ReviewReminderPolicy
import com.brokenpip3.fatto.data.review.ReviewReminderState
import com.brokenpip3.fatto.worker.ReviewReminderWorker
import java.time.Instant
import java.util.concurrent.TimeUnit

class ReviewReminderScheduler(context: Context, private val preferences: SettingsRepository) {
    private val context = context.applicationContext

    fun reconcile() =
        synchronized(ReviewPreferenceLock.lock) {
            val days = preferences.readReviewSettings().reminderDays
            val workManager = WorkManager.getInstance(context)
            if (days == null) {
                workManager.cancelUniqueWork(WORK_NAME)
                NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            } else {
                val runtime = preferences.getReviewReminderState()
                if (runtime.nextDueEpochMillis == null) {
                    preferences.setReviewReminderState(
                        ReviewReminderState(
                            ReviewReminderPolicy.nextDue(Instant.now(), days).toEpochMilli(), runtime.generation + 1,
                        ),
                    )
                }
                // A durable daily check avoids stale chains resurrecting disabled/changed schedules.
                // Delivery reads the current due time, so a productive session need not replace work.
                val request =
                    PeriodicWorkRequestBuilder<ReviewReminderWorker>(1, TimeUnit.DAYS)
                        .setInitialDelay(1, TimeUnit.DAYS).build()
                workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            }
        }

    fun onProductiveSessionEnded(at: Instant) =
        synchronized(ReviewPreferenceLock.lock) {
            preferences.readReviewSettings().reminderDays?.let { days ->
                val runtime = preferences.getReviewReminderState()
                preferences.setReviewReminderState(
                    ReviewReminderState(
                        ReviewReminderPolicy.nextDue(at, days).toEpochMilli(), runtime.generation + 1,
                    ),
                )
                NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            }
            reconcile()
        }

    companion object {
        const val WORK_NAME = "ReviewReminderWork"
        const val CHANNEL_ID = "review_reminders"
        const val NOTIFICATION_ID = 1807

        fun notificationsEnabled(context: Context): Boolean =
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
                context.getSystemService(NotificationManager::class.java)
                    .getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }
}
