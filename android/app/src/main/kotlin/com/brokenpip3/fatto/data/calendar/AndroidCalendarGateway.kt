package com.brokenpip3.fatto.data.calendar

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Calendar account sync adapters, not Fatto, upload these local writes. */
class AndroidCalendarGateway(private val context: Context) : CalendarGateway {
    private val resolver get() = context.contentResolver

    private fun permissions() {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED
        ) {
            throw CalendarAccessException(CalendarAccessFailure.PERMISSION)
        }
    }

    override suspend fun listTargets(): List<CalendarTarget> =
        withContext(Dispatchers.IO) {
            permissions()
            try {
                val columns = arrayOf("_id", "account_name", "account_type", "ownerAccount", "calendar_displayName")
                val cursor =
                    resolver.query(
                        CalendarContract.Calendars.CONTENT_URI, columns,
                        "visible=1 AND calendar_access_level>=?",
                        arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
                        "calendar_displayName",
                    )
                        ?: throw CalendarAccessException(CalendarAccessFailure.QUERY)
                cursor.use {
                    buildList {
                        while (it.moveToNext()) {
                            add(
                                CalendarTarget(it.getLong(0), it.getString(1), it.getString(2), it.getString(3)).also { target ->
                                    target.displayName =
                                        it.getString(4) ?: target.accountName
                                },
                            )
                        }
                    }
                }
            } catch (e: SecurityException) {
                throw CalendarAccessException(CalendarAccessFailure.PERMISSION, e)
            }
        }

    @Suppress("ThrowsCount")
    private fun validate(target: CalendarTarget) {
        permissions()
        try {
            val columns = arrayOf("_id", "account_name", "account_type", "ownerAccount")
            val cursor =
                resolver.query(
                    CalendarContract.Calendars.CONTENT_URI, columns,
                    "_id=? AND calendar_access_level>=?",
                    arrayOf(target.id.toString(), CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()), null,
                )
                    ?: throw CalendarAccessException(CalendarAccessFailure.QUERY)
            cursor.use {
                if (!it.moveToFirst() || CalendarTarget(it.getLong(0), it.getString(1), it.getString(2), it.getString(3)) != target) {
                    throw CalendarAccessException(CalendarAccessFailure.TARGET)
                }
            }
        } catch (e: SecurityException) {
            throw CalendarAccessException(CalendarAccessFailure.PERMISSION, e)
        }
    }

    override suspend fun read(target: CalendarTarget): List<ProviderEvent> =
        withContext(Dispatchers.IO) {
            validate(target)
            try {
                val columns = arrayOf("_id", "title", "description", "dtstart", "dtend", "allDay", "eventTimezone", "availability")
                val cursor =
                    resolver.query(
                        CalendarContract.Events.CONTENT_URI, columns, "calendar_id=? AND deleted=0",
                        arrayOf(target.id.toString()), null,
                    ) ?: throw CalendarAccessException(CalendarAccessFailure.QUERY)
                cursor.use {
                    buildList {
                        while (it.moveToNext()) {
                            val rawDescription = it.getString(2) ?: ""
                            val key = parseMarker(rawDescription)
                            val event =
                                ProjectedEvent(
                                    key ?: EventKey("", ""), it.getString(1) ?: "", stripMarker(rawDescription),
                                    it.getLong(3), it.getLong(4), it.getInt(5) != 0, it.getString(6) ?: "UTC",
                                )
                            add(
                                ProviderEvent(
                                    it.getLong(0), key, event.copy(fingerprint = event.managedFingerprint()),
                                    it.getInt(7) == CalendarContract.Events.AVAILABILITY_FREE,
                                ),
                            )
                        }
                    }
                }
            } catch (e: SecurityException) {
                throw CalendarAccessException(CalendarAccessFailure.PERMISSION, e)
            }
        }

    override suspend fun apply(
        target: CalendarTarget,
        changes: List<CalendarChange>,
    ): List<CalendarWriteResult> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<CalendarWriteResult>()
            val chunks = mutableListOf<List<CalendarChange>>()
            var chunk = mutableListOf<CalendarChange>()
            var bytes = 0
            for (change in changes) {
                val event =
                    when (change) {
                        is CalendarChange.Insert -> change.event
                        is CalendarChange.Update -> change.event
                        is CalendarChange.Delete -> null
                    }
                val size = event?.let { (it.title + it.description + marker(it.key)).toByteArray(Charsets.UTF_8).size * 2 + 1024 } ?: 256
                if (size > 256 * 1024 && event != null) throw CalendarItemTooLargeException(event.key)
                if (chunk.size >= 50 || bytes + size > 256 * 1024) {
                    chunks.add(chunk)
                    chunk = mutableListOf()
                    bytes = 0
                }
                chunk.add(change)
                bytes += size
            }
            if (chunk.isNotEmpty()) chunks.add(chunk)
            for (batch in chunks) {
                validate(target)
                val operations =
                    ArrayList(
                        batch.map { change ->
                            when (change) {
                                is CalendarChange.Insert ->
                                    ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
                                        .withValues(values(target, change.event)).build()
                                is CalendarChange.Update ->
                                    ContentProviderOperation.newUpdate(CalendarContract.Events.CONTENT_URI)
                                        .withSelection(
                                            "_id=? AND calendar_id=? AND deleted=0",
                                            arrayOf(
                                                change.eventId.toString(),
                                                target.id.toString(),
                                            ),
                                        )
                                        .withValues(values(target, change.event)).withExpectedCount(1).build()
                                is CalendarChange.Delete ->
                                    ContentProviderOperation.newDelete(CalendarContract.Events.CONTENT_URI)
                                        .withSelection("_id=? AND calendar_id=?", arrayOf(change.eventId.toString(), target.id.toString()))
                                        .withExpectedCount(1).build()
                            }
                        },
                    )
                try {
                    val applied = resolver.applyBatch(CalendarContract.AUTHORITY, operations)
                    batch.zip(applied).forEach { (change, value) ->
                        val id =
                            when (change) {
                                is CalendarChange.Insert ->
                                    value.uri?.lastPathSegment?.toLongOrNull()
                                        ?: throw CalendarAccessException(CalendarAccessFailure.WRITE)
                                is CalendarChange.Update -> change.eventId
                                is CalendarChange.Delete -> change.eventId
                            }
                        result.add(CalendarWriteResult(change, id))
                    }
                } catch (e: SecurityException) {
                    throw CalendarAccessException(CalendarAccessFailure.PERMISSION, e)
                } catch (e: CalendarAccessException) {
                    throw e
                } catch (e: Exception) {
                    throw CalendarAccessException(CalendarAccessFailure.WRITE, e)
                }
            }
            result
        }

    private fun values(
        target: CalendarTarget,
        event: ProjectedEvent,
    ) = ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, target.id)
        put(CalendarContract.Events.TITLE, event.title)
        put(CalendarContract.Events.DESCRIPTION, event.description + "\n\n" + marker(event.key))
        put(CalendarContract.Events.DTSTART, event.startMillis)
        put(CalendarContract.Events.DTEND, event.endMillis)
        put(CalendarContract.Events.ALL_DAY, if (event.allDay) 1 else 0)
        put(CalendarContract.Events.EVENT_TIMEZONE, event.timeZone)
        put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE)
    }

    companion object {
        private const val PREFIX = "[Fatto calendar v1:"

        private fun marker(key: EventKey) =
            PREFIX + Base64.encodeToString(key.encode().toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP) + "]"

        private fun parseMarker(description: String): EventKey? =
            runCatching {
                val line = description.substringAfterLast('\n')
                require(line.startsWith(PREFIX) && line.endsWith("]"))
                EventKey.decode(String(Base64.decode(line.substring(PREFIX.length, line.length - 1), Base64.URL_SAFE), Charsets.UTF_8))
                    .also { require(it.namespace.isNotBlank() && it.taskUuid.isNotBlank() && it.role == "scheduled") }
            }.getOrNull()

        private fun stripMarker(description: String): String =
            if (parseMarker(description) ==
                null
            ) {
                description
            } else {
                description.substringBeforeLast("\n\n", "")
            }
    }
}
