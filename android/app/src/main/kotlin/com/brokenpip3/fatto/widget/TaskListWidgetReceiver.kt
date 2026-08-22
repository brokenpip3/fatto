package com.brokenpip3.fatto.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.brokenpip3.fatto.worker.TaskListWorker

class TaskListWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TaskListWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        TaskListWorker.schedulePeriodic(context)
        TaskListWorker.enqueueOneTime(context)
    }

    override fun onDeleted(
        context: Context,
        appWidgetIds: IntArray,
    ) {
        super.onDeleted(context, appWidgetIds)
        cancelPeriodicIfNoWidgetsRemain(context)
    }

    /**
     * Stops the periodic refresh once the last widget instance is removed, so it
     * does not keep waking the device every 30 minutes. Split out of [onDeleted]
     * so tests can exercise it without a real broadcast dispatch (Glance's
     * onDeleted relies on `BroadcastReceiver.goAsync()`).
     */
    internal fun cancelPeriodicIfNoWidgetsRemain(context: Context) {
        val remaining =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, TaskListWidgetReceiver::class.java))
        if (remaining.isEmpty()) {
            TaskListWorker.cancelPeriodic(context)
        }
    }
}
