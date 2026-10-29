package com.brokenpip3.fatto

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.brokenpip3.fatto.data.calendar.AndroidCalendarGateway
import com.brokenpip3.fatto.data.calendar.CalendarTarget
import java.io.Closeable

/** A disposable device-only calendar; never touches a user's account calendars. */
class CalendarProviderFixture(private val context: Context) : Closeable {
    private val account = "fatto-fixture-${System.nanoTime()}"
    private val calendars =
        CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build()
    private val row =
        context.contentResolver.insert(
            calendars,
            ContentValues().apply {
                put(CalendarContract.Calendars.ACCOUNT_NAME, account)
                put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                put(CalendarContract.Calendars.NAME, account)
                put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Fatto test calendar")
                put(CalendarContract.Calendars.OWNER_ACCOUNT, account)
                put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
                put(CalendarContract.Calendars.VISIBLE, 1)
                put(CalendarContract.Calendars.SYNC_EVENTS, 1)
                put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, "UTC")
            },
        ) ?: error("Could not create test calendar")
    val target = CalendarTarget(row.lastPathSegment!!.toLong(), account, CalendarContract.ACCOUNT_TYPE_LOCAL, account)
    val gateway = AndroidCalendarGateway(context)

    override fun close() {
        context.contentResolver.delete(calendars, "_id=?", arrayOf(target.id.toString()))
    }
}
