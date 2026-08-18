package com.brokenpip3.fatto.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.brokenpip3.fatto.worker.TaskListWorker

/**
 * Listens for task mutations and enqueues a one-time widget refresh.
 * Kept as a receiver so [com.brokenpip3.fatto.data.TaskRepository] stays
 * decoupled from WorkManager.
 */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == ACTION_WIDGET_REFRESH) {
            TaskListWorker.enqueueOneTime(context)
        }
    }

    companion object {
        const val ACTION_WIDGET_REFRESH = "com.brokenpip3.fatto.action.WIDGET_REFRESH"

        fun sendRefresh(context: Context) {
            context.sendBroadcast(
                Intent(ACTION_WIDGET_REFRESH).setPackage(context.packageName),
            )
        }
    }
}
