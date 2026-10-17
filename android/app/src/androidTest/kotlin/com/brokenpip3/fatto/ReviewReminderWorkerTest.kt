package com.brokenpip3.fatto

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.brokenpip3.fatto.data.SettingsRepositoryImpl
import com.brokenpip3.fatto.data.review.ReviewReminderState
import com.brokenpip3.fatto.data.review.ReviewSettings
import com.brokenpip3.fatto.worker.ReviewReminderWorker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewReminderWorkerTest {
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    @Test fun blockedReviewDeliveryKeepsItsDueDate() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val preferences = SettingsRepositoryImpl(context)
            val original = preferences.readReviewSettings()
            val originalRuntime = preferences.getReviewReminderState()
            val due = ReviewReminderState(1, 80)
            try {
                preferences.setReviewSettings(ReviewSettings(reminderDays = 7))
                preferences.setReviewReminderState(due)
                val factory =
                    object : WorkerFactory() {
                        override fun createWorker(
                            appContext: Context,
                            workerClassName: String,
                            workerParameters: WorkerParameters,
                        ): ListenableWorker? =
                            if (workerClassName ==
                                ReviewReminderWorker::class.java.name
                            ) {
                                ReviewReminderWorker(appContext, workerParameters) { false }
                            } else {
                                null
                            }
                    }
                val worker = TestListenableWorkerBuilder<ReviewReminderWorker>(context).setWorkerFactory(factory).build()
                assertEquals(ListenableWorker.Result.success(), worker.doWork())
                assertEquals(due, preferences.getReviewReminderState())
            } finally {
                preferences.setReviewSettings(original)
                preferences.setReviewReminderState(originalRuntime)
            }
        }

    @Test fun disabledOrFutureReminderDoesNotDeliverAndDueReminderDeliversOnce() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val preferences = SettingsRepositoryImpl(context)
            val original = preferences.readReviewSettings()
            val runtime = preferences.getReviewReminderState()
            val manager = context.getSystemService(NotificationManager::class.java)
            try {
                preferences.setReviewSettings(ReviewSettings(reminderDays = 7))
                preferences.setReviewReminderState(ReviewReminderState(System.currentTimeMillis() + 86_400_000, 1))
                val first = TestListenableWorkerBuilder<ReviewReminderWorker>(context).build()
                first.doWork()
                assertFalse(manager.activeNotifications.any { it.id == 1807 })
                preferences.setReviewReminderState(ReviewReminderState(1, 2))
                TestListenableWorkerBuilder<ReviewReminderWorker>(context).build().doWork()
                assertTrue(manager.activeNotifications.any { it.id == 1807 })
                val next = preferences.getReviewReminderState()
                assertTrue(checkNotNull(next.nextDueEpochMillis) > System.currentTimeMillis())
                TestListenableWorkerBuilder<ReviewReminderWorker>(context).build().doWork()
                assertEquals(next, preferences.getReviewReminderState())
                preferences.setReviewSettings(ReviewSettings())
                manager.cancel(1807)
                TestListenableWorkerBuilder<ReviewReminderWorker>(context).build().doWork()
                assertFalse(manager.activeNotifications.any { it.id == 1807 })
            } finally {
                manager.cancel(1807)
                preferences.setReviewSettings(original)
                preferences.setReviewReminderState(runtime)
            }
        }
}
