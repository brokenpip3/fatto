package com.brokenpip3.fatto

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.brokenpip3.fatto.data.calendar.AndroidCalendarGateway
import com.brokenpip3.fatto.data.calendar.CalendarChange
import com.brokenpip3.fatto.data.calendar.EventKey
import com.brokenpip3.fatto.data.calendar.ProjectedEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidCalendarGatewayTest {
    @get:Rule val permissions = GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    @Test fun roundTripsAllDayAndTimedEventsAndPreservesUnrelatedFields() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val resolver = context.contentResolver
            val account = "fatto-test-${System.nanoTime()}"
            val uri =
                CalendarContract.Calendars.CONTENT_URI.buildUpon()
                    .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                    .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account)
                    .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build()
            val row =
                resolver.insert(
                    uri,
                    ContentValues().apply {
                        put(CalendarContract.Calendars.ACCOUNT_NAME, account)
                        put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                        put(CalendarContract.Calendars.NAME, account)
                        put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, account)
                        put(CalendarContract.Calendars.OWNER_ACCOUNT, account)
                        put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
                        put(CalendarContract.Calendars.VISIBLE, 1)
                        put(CalendarContract.Calendars.SYNC_EVENTS, 1)
                        put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, "UTC")
                    },
                )!!
            val gateway = AndroidCalendarGateway(context)
            try {
                val target = gateway.listTargets().single { it.accountName == account }
                val event =
                    ProjectedEvent(
                        EventKey("test", "uuid"), "Work", "Project: test", 1_793_318_400_000L,
                        1_793_404_800_000L, true, "UTC", "fp",
                    )
                val id = gateway.apply(target, listOf(CalendarChange.Insert(event))).single().eventId
                assertEquals(event.key, gateway.read(target).single().key)
                val eventUri = android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
                resolver.update(eventUri, ContentValues().apply { put(CalendarContract.Events.EVENT_LOCATION, "keep me") }, null, null)
                val timed = event.copy(allDay = false, endMillis = event.startMillis + 1_800_000, title = "Changed")
                gateway.apply(target, listOf(CalendarChange.Update(id, timed)))
                assertEquals("Changed", gateway.read(target).single().event.title)
                resolver.query(eventUri, arrayOf(CalendarContract.Events.EVENT_LOCATION), null, null, null)!!.use {
                    assertTrue(it.moveToFirst())
                    assertEquals("keep me", it.getString(0))
                }
                gateway.apply(target, listOf(CalendarChange.Delete(id)))
                assertTrue(gateway.read(target).isEmpty())
            } finally {
                resolver.delete(
                    row.buildUpon().appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account)
                        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build(),
                    null, null,
                )
            }
        }

    @Test fun reusedCalendarIdWithDifferentAccountCannotBeWritten() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            CalendarProviderFixture(context).use { fixture ->
                val event = ProjectedEvent(EventKey("test", "uuid"), "Keep me", "", 1793318400000L, 1793404800000L, true, "UTC")
                val id = fixture.gateway.apply(fixture.target, listOf(CalendarChange.Insert(event))).single().eventId
                try {
                    fixture.gateway.apply(fixture.target.copy(accountName = "different account"), listOf(CalendarChange.Delete(id)))
                    org.junit.Assert.fail("Expected target validation to reject the reused ID")
                } catch (e: com.brokenpip3.fatto.data.calendar.CalendarAccessException) {
                    assertEquals(com.brokenpip3.fatto.data.calendar.CalendarAccessFailure.TARGET, e.failure)
                }
                assertEquals("Keep me", fixture.gateway.read(fixture.target).single().event.title)
            }
        }

    @Test fun oversizedDescriptionFailsWithoutDeletingExistingEvents() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            CalendarProviderFixture(context).use { fixture ->
                val event = ProjectedEvent(EventKey("test", "uuid"), "Keep me", "", 1793318400000L, 1793404800000L, true, "UTC")
                fixture.gateway.apply(fixture.target, listOf(CalendarChange.Insert(event)))
                try {
                    fixture.gateway.apply(fixture.target, listOf(CalendarChange.Insert(event.copy(description = "x".repeat(200000)))))
                    org.junit.Assert.fail("Expected the oversized write to be rejected")
                } catch (e: com.brokenpip3.fatto.data.calendar.CalendarItemTooLargeException) {
                    assertEquals(event.key, e.key)
                }
                assertEquals(1, fixture.gateway.read(fixture.target).size)
            }
        }

    @Test fun hiddenSelectedCalendarStillReceivesUpdates() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            CalendarProviderFixture(context).use { fixture ->
                context.contentResolver.update(
                    CalendarContract.Calendars.CONTENT_URI,
                    ContentValues().apply { put(CalendarContract.Calendars.VISIBLE, 0) }, "_id=?", arrayOf(fixture.target.id.toString()),
                )
                assertTrue(fixture.target !in fixture.gateway.listTargets())
                val event = ProjectedEvent(EventKey("test", "uuid"), "Hidden work", "", 1793318400000L, 1793404800000L, true, "UTC")
                fixture.gateway.apply(fixture.target, listOf(CalendarChange.Insert(event)))
                assertEquals("Hidden work", fixture.gateway.read(fixture.target).single().event.title)
            }
        }
}
