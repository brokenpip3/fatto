package com.brokenpip3.fatto.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

interface SyncDiagnosticsRepository {
    val events: StateFlow<List<SyncDiagnosticEvent>>

    fun append(event: SyncDiagnosticEvent)

    fun clear()
}

class SyncDiagnosticsRepositoryImpl(context: Context) : SyncDiagnosticsRepository {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private val _events = MutableStateFlow(readEvents())
    override val events: StateFlow<List<SyncDiagnosticEvent>> = _events

    override fun append(event: SyncDiagnosticEvent) {
        synchronized(lock) {
            val updated = SyncDiagnosticsFormatter.appendBounded(_events.value, event)
            val stored = preferences.edit().putString(EVENTS_KEY, encode(updated)).commit()
            if (stored) {
                _events.value = updated
                Log.d(TAG, SyncDiagnosticsFormatter.formatEvents(listOf(updated.last())))
            } else {
                Log.w(TAG, "Unable to persist sync diagnostics")
            }
        }
    }

    override fun clear() {
        synchronized(lock) {
            if (preferences.edit().remove(EVENTS_KEY).commit()) {
                _events.value = emptyList()
            } else {
                Log.w(TAG, "Unable to clear sync diagnostics")
            }
        }
    }

    private fun readEvents(): List<SyncDiagnosticEvent> {
        val stored = runCatching { preferences.getString(EVENTS_KEY, null) }.getOrNull() ?: return emptyList()
        return runCatching {
            val array = JSONArray(stored)
            List(array.length()) { index -> decode(array.getJSONObject(index)) }
                .map(SyncDiagnosticsFormatter::sanitizeEvent)
                .takeLast(MAX_SYNC_DIAGNOSTIC_EVENTS)
        }.getOrElse {
            Log.w(TAG, "Unable to read stored sync diagnostics")
            emptyList()
        }
    }

    private fun encode(events: List<SyncDiagnosticEvent>): String =
        JSONArray().apply {
            events.forEach { event ->
                put(
                    JSONObject()
                        .put("timestamp", event.timestampEpochMillis)
                        .put("stage", event.stage)
                        .put("outcome", event.outcome)
                        .put("summary", event.summary)
                        .put("endpointOrigin", event.serverOrigin ?: JSONObject.NULL)
                        .put("backend", event.backend.value)
                        .put("elapsed", event.elapsedMillis ?: JSONObject.NULL)
                        .put("appVersion", event.appVersion),
                )
            }
        }.toString()

    private fun decode(json: JSONObject): SyncDiagnosticEvent =
        SyncDiagnosticEvent(
            timestampEpochMillis = json.getLong("timestamp"),
            stage = json.getString("stage"),
            outcome = json.getString("outcome"),
            summary = json.getString("summary"),
            serverOrigin =
                when {
                    !json.isNull("endpointOrigin") -> json.getString("endpointOrigin")
                    !json.isNull("serverOrigin") -> json.getString("serverOrigin")
                    else -> null
                },
            elapsedMillis = if (json.isNull("elapsed")) null else json.getLong("elapsed"),
            appVersion = json.getString("appVersion"),
            backend = if (json.isNull("backend")) SyncType.SERVER else SyncType.fromValue(json.getString("backend")),
        )

    private companion object {
        const val TAG = "SyncDiagnostics"
        const val PREFERENCES_NAME = "sync_diagnostics"
        const val EVENTS_KEY = "events"
    }
}
