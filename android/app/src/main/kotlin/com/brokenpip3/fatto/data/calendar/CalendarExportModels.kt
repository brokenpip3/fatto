package com.brokenpip3.fatto.data.calendar

import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant

data class CalendarTarget(val id: Long, val accountName: String, val accountType: String, val ownerAccount: String?) {
    var displayName: String = accountName

    fun encode(): String =
        JSONObject().put("id", id).put("account", accountName).put("type", accountType)
            .put("owner", ownerAccount ?: JSONObject.NULL).toString()

    companion object {
        fun decode(raw: String): CalendarTarget =
            JSONObject(raw).let {
                CalendarTarget(
                    it.getLong("id"),
                    it.getString("account"),
                    it.getString("type"),
                    if (it.isNull("owner")) null else it.getString("owner"),
                )
            }
    }
}

data class EventKey(val namespace: String, val taskUuid: String, val role: String = "scheduled") {
    fun encode(): String = JSONObject().put("namespace", namespace).put("task", taskUuid).put("role", role).toString()

    companion object {
        fun decode(raw: String): EventKey =
            JSONObject(raw).let { EventKey(it.getString("namespace"), it.getString("task"), it.getString("role")) }
    }
}

data class ExportConfig(
    val namespace: String,
    val enabled: Boolean,
    val respectWait: Boolean,
    val target: CalendarTarget?,
    val generation: Long,
)

data class ProjectedEvent(
    val key: EventKey,
    val title: String,
    val description: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val timeZone: String,
    val fingerprint: String = "",
) {
    fun managedFingerprint(): String {
        val canonical = org.json.JSONArray(listOf(title, description, startMillis, endMillis, allDay, timeZone)).toString()
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

data class Projection(val events: List<ProjectedEvent>, val nextRelease: Instant?, val warnings: List<String> = emptyList())

enum class ExportOperation { INSERT, UPDATE, DELETE, SETTLED }

data class ExportRecord(
    val key: EventKey,
    val target: CalendarTarget,
    val eventId: Long?,
    val fingerprint: String,
    val operation: ExportOperation,
)

enum class ExportPhase { DISABLED, QUEUED, RUNNING, SUCCESS, PAUSED_PERMISSION, PAUSED_TARGET, PAUSED_DATA, RETRY, CLEANUP_PENDING }

data class ExportStatus(val phase: ExportPhase = ExportPhase.DISABLED, val lastSuccessMillis: Long? = null, val message: String? = null)

data class ProviderEvent(val id: Long, val key: EventKey?, val event: ProjectedEvent, val available: Boolean = true)

sealed interface CalendarChange {
    data class Insert(val event: ProjectedEvent) : CalendarChange

    data class Update(val eventId: Long, val event: ProjectedEvent) : CalendarChange

    data class Delete(val eventId: Long) : CalendarChange
}

data class CalendarWriteResult(val change: CalendarChange, val eventId: Long)

enum class CalendarAccessFailure { PERMISSION, TARGET, QUERY, WRITE }

class CalendarAccessException(val failure: CalendarAccessFailure, cause: Throwable? = null) : Exception(failure.name, cause)

interface CalendarGateway {
    suspend fun listTargets(): List<CalendarTarget>

    suspend fun read(target: CalendarTarget): List<ProviderEvent>

    suspend fun apply(
        target: CalendarTarget,
        changes: List<CalendarChange>,
    ): List<CalendarWriteResult>
}

enum class ExportDisposition { SUCCESS, PAUSED, RETRY }

data class ExportRunResult(val disposition: ExportDisposition, val nextRelease: Instant? = null, val processedRequestGeneration: Long = 0)

class CalendarItemTooLargeException(val key: EventKey) : Exception("Calendar event exceeds the write budget")
