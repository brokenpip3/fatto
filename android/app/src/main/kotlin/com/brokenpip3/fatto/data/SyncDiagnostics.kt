package com.brokenpip3.fatto.data

import java.net.URI
import java.util.Locale

internal const val MAX_SYNC_DIAGNOSTIC_EVENTS = 100
private const val MAX_SYNC_DIAGNOSTIC_SUMMARY_LENGTH = 500
private val URL_IN_ERROR_PATTERN = Regex("(?i)\\b(?:https?|ftp)://[^\\s\\]<>\\\"']+")
private val UUID_IN_ERROR_PATTERN = Regex("(?i)\\b[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\\b")

data class SyncDiagnosticEvent(
    val timestampEpochMillis: Long,
    val stage: String,
    val outcome: String,
    val summary: String,
    val serverOrigin: String?,
    val elapsedMillis: Long?,
    val appVersion: String,
    val backend: SyncType = SyncType.SERVER,
) {
    val endpointOrigin: String? get() = serverOrigin
}

object SyncDiagnosticsFormatter {
    fun appendBounded(
        events: List<SyncDiagnosticEvent>,
        event: SyncDiagnosticEvent,
    ): List<SyncDiagnosticEvent> = (events + sanitizeEvent(event)).takeLast(MAX_SYNC_DIAGNOSTIC_EVENTS)

    @Suppress("ReturnCount")
    fun endpointOrigin(rawUrl: String): String? {
        if (rawUrl.any { it.isISOControl() }) return null
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        if (scheme != "http" && scheme != "https") return null
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        return "$scheme://$host$port"
    }

    fun sanitizeEvent(event: SyncDiagnosticEvent): SyncDiagnosticEvent =
        event.copy(
            stage = safeSingleLine(event.stage),
            outcome = safeSingleLine(event.outcome),
            summary =
                safeSingleLine(
                    UUID_IN_ERROR_PATTERN.replace(
                        URL_IN_ERROR_PATTERN.replace(event.summary, "[server URL]"),
                        "[task ID]",
                    ),
                ).take(MAX_SYNC_DIAGNOSTIC_SUMMARY_LENGTH),
            serverOrigin = event.serverOrigin?.let(::endpointOrigin),
            appVersion = safeSingleLine(event.appVersion),
        )

    fun serverOrigin(rawUrl: String): String? = endpointOrigin(rawUrl)

    fun safeError(
        error: Throwable,
        sensitiveValues: Set<String>,
    ): String {
        val message = error.message?.takeIf { it.isNotBlank() } ?: "Sync failed without additional details"
        var safeMessage = URL_IN_ERROR_PATTERN.replace(message, "[sync URL]")
        sensitiveValues
            .filter { it.isNotEmpty() }
            .sortedByDescending { it.length }
            .forEach { sensitive -> safeMessage = safeMessage.replace(sensitive, "[redacted]") }
        safeMessage = UUID_IN_ERROR_PATTERN.replace(safeMessage, "[task ID]")
        safeMessage = safeSingleLine(safeMessage).take(MAX_SYNC_DIAGNOSTIC_SUMMARY_LENGTH)
        return "${error.javaClass.simpleName}: $safeMessage"
    }

    fun formatEvents(events: List<SyncDiagnosticEvent>): String =
        events.joinToString(separator = "\n") { rawEvent ->
            val event = sanitizeEvent(rawEvent)
            val origin = event.serverOrigin?.let { " [$it]" }.orEmpty()
            val duration = event.elapsedMillis?.let { " (${it}ms)" }.orEmpty()
            buildString {
                append(event.timestampEpochMillis)
                append(' ')
                append(event.backend.value)
                append(' ')
                append(event.stage)
                append(' ')
                append(event.outcome)
                append(origin)
                append(duration)
                append(": ")
                append(event.summary)
                append(" [")
                append(event.appVersion)
                append(']')
            }
        }

    private fun safeSingleLine(value: String): String =
        value
            .map { character ->
                if (
                    character.isISOControl() ||
                    Character.getType(character) == Character.LINE_SEPARATOR.toInt() ||
                    Character.getType(character) == Character.PARAGRAPH_SEPARATOR.toInt()
                ) {
                    ' '
                } else {
                    character
                }
            }
            .joinToString(separator = "")
            .replace(Regex("\\s+"), " ")
            .trim()
}
