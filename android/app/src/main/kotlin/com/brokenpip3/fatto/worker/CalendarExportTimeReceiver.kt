package com.brokenpip3.fatto.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.brokenpip3.fatto.data.calendar.CalendarExportScheduler

class CalendarExportTimeReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            CalendarExportScheduler.request(context)
        }
    }
}
