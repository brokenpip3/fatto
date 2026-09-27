package com.brokenpip3.fatto.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncDiagnosticsTest {
    @Test
    fun boundedHistoryKeepsNewestEventsInChronologicalOrder() {
        val retained =
            (0L until 101L).fold(emptyList<SyncDiagnosticEvent>()) { events, timestamp ->
                SyncDiagnosticsFormatter.appendBounded(events, event(timestamp))
            }

        assertEquals(100, retained.size)
        assertEquals(1L, retained.first().timestampEpochMillis)
        assertEquals(100L, retained.last().timestampEpochMillis)
    }

    @Test
    fun serverOriginRemovesUserInfoPathQueryAndFragment() {
        val origin =
            SyncDiagnosticsFormatter.serverOrigin(
                "https://user:password@sync.example.com:8443/private/path?token=query-secret#fragment",
            )

        assertEquals("https://sync.example.com:8443", origin)
    }

    @Test
    fun malformedServerUrlHasNoDiagnosticOrigin() {
        assertEquals(null, SyncDiagnosticsFormatter.serverOrigin("https://bad host/path"))
    }

    @Test
    fun errorFormattingRemovesCredentialsClientIdAndFullUrl() {
        val secret = "encryption-secret-123"
        val clientId = "768d9f09-accd-406d-8685-7b977b83d5c6"
        val taskUuid = "e4dd60be-7c1e-4c1f-8c36-58b5b185c4c8"
        val error =
            IllegalStateException(
                "Request failed for https://user:password@sync.example.com/private/path?token=query-secret; " +
                    "secret=$secret client=$clientId task=$taskUuid",
            )

        val formatted = SyncDiagnosticsFormatter.safeError(error, setOf(secret, clientId))

        listOf(secret, clientId, taskUuid, "user:password", "/private/path", "query-secret", "https://").forEach { unsafe ->
            assertFalse("Diagnostic exposed $unsafe", formatted.contains(unsafe))
        }
        assertTrue(formatted.contains("IllegalStateException"))
    }

    @Test
    fun eventSanitizationRemovesFullUrlsAndLineBreaksBeforeStorage() {
        val taskUuid = "e4dd60be-7c1e-4c1f-8c36-58b5b185c4c8"
        val unsafeEvent =
            event(1L).copy(
                summary = "request failed for https://user:password@sync.example.com/path?token=query-secret\nretry task=$taskUuid",
                serverOrigin = "https://user:password@sync.example.com/path?token=query-secret",
            )

        val sanitized = SyncDiagnosticsFormatter.sanitizeEvent(unsafeEvent)

        assertEquals("request failed for [server URL] retry task=[task ID]", sanitized.summary)
        assertEquals("https://sync.example.com", sanitized.serverOrigin)
    }

    @Test
    fun diagnosticExportKeepsSanitizedErrorWithoutCredentialsOrFullUrl() {
        val secret = "export-encryption-secret"
        val clientId = "768d9f09-accd-406d-8685-7b977b83d5c6"
        val url = "https://user:password@sync.example.com/private/path?token=query-secret"
        val safeSummary =
            SyncDiagnosticsFormatter.safeError(
                IllegalStateException("request to $url failed; secret=$secret client=$clientId"),
                setOf(secret, clientId),
            )
        val event =
            event(1L).copy(
                summary = safeSummary,
                serverOrigin = SyncDiagnosticsFormatter.serverOrigin(url),
            )

        val exported = SyncDiagnosticsFormatter.formatEvents(listOf(event))

        listOf(secret, clientId, "user:password", "/private/path", "query-secret").forEach { unsafe ->
            assertFalse("Export exposed $unsafe", exported.contains(unsafe))
        }
        assertTrue(exported.contains("IllegalStateException"))
    }

    @Test
    fun eventExportUsesOnlyEventFields() {
        val formatted = SyncDiagnosticsFormatter.formatEvents(listOf(event(1L)))

        assertTrue(formatted.contains("sync started"))
        assertTrue(formatted.contains("https://sync.example.com:8443"))
    }

    private fun event(timestamp: Long) =
        SyncDiagnosticEvent(
            timestampEpochMillis = timestamp,
            stage = "sync",
            outcome = "started",
            summary = "sync started",
            serverOrigin = "https://sync.example.com:8443",
            elapsedMillis = null,
            appVersion = "0.12.0",
        )
}
