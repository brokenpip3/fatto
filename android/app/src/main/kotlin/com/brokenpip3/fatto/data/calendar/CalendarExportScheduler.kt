package com.brokenpip3.fatto.data.calendar

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.brokenpip3.fatto.worker.CalendarExportWorker
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object CalendarExportRuntime {
    @Volatile private var instance: CalendarExportStore? = null

    fun store(context: Context): CalendarExportStore =
        instance ?: synchronized(this) {
            instance ?: CalendarExportStore(context.applicationContext).also { instance = it }
        }

    fun reconciler(context: Context) =
        CalendarExportReconciler(
            store(context), AndroidCalendarGateway(context),
            NativeCalendarTaskSnapshotSource(context), Clock.systemUTC(), ZoneId::systemDefault,
        )
}

object CalendarExportScheduler {
    private const val IMMEDIATE = "fatto-calendar-export"
    private const val RELEASE = "fatto-calendar-release"
    private const val REPAIR = "fatto-calendar-repair"
    private var observer: ContentObserver? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pendingObservation: Runnable? = null

    /** This boundary must never turn a saved task into an apparent save/sync failure. */
    fun request(context: Context) {
        try {
            val store = CalendarExportRuntime.store(context)
            if (!store.readConfig().enabled && store.cleanupTargets().isEmpty()) {
                WorkManager.getInstance(context).cancelUniqueWork(RELEASE)
                return
            }
            store.request()
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE, ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<CalendarExportWorker>().build(),
            )
        } catch (e: Exception) {
            Log.e("CalendarExport", "Could not schedule local calendar export", e)
        }
    }

    fun bootstrap(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            REPAIR, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CalendarExportWorker>(6, TimeUnit.HOURS).build(),
        )
        request(context)
    }

    fun reschedule(
        context: Context,
        nextRelease: Instant?,
    ) {
        val manager = WorkManager.getInstance(context)
        if (nextRelease == null || !CalendarExportRuntime.store(context).readConfig().enabled) {
            manager.cancelUniqueWork(RELEASE)
        } else {
            manager.enqueueUniqueWork(
                RELEASE, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<CalendarExportWorker>()
                    .setInitialDelay(
                        Duration.between(
                            Instant.now(),
                            nextRelease,
                        ).toMillis().coerceAtLeast(0),
                        TimeUnit.MILLISECONDS,
                    ).build(),
            )
        }
    }

    fun foreground(context: Context) {
        request(context)
        if (observer != null) return
        val app = context.applicationContext
        val callback =
            object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    pendingObservation?.let(handler::removeCallbacks)
                    pendingObservation = Runnable { request(app) }.also { handler.postDelayed(it, 1000) }
                }
            }
        try {
            app.contentResolver.registerContentObserver(CalendarContract.Events.CONTENT_URI, true, callback)
            observer = callback
        } catch (e: SecurityException) {
            // Permission will be requested only through settings.
        }
    }

    fun background(context: Context) {
        observer?.let { context.contentResolver.unregisterContentObserver(it) }
        observer = null
        pendingObservation?.let(handler::removeCallbacks)
        pendingObservation = null
    }
}
