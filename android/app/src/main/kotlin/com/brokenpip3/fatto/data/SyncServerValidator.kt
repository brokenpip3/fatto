package com.brokenpip3.fatto.data

import java.net.URI
import java.util.Locale
import java.util.UUID

// UUID syntax is checked before UUID.fromString because the JDK parser accepts shortened groups.
private val STANDARD_UUID_PATTERN = Regex("^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$")

enum class SyncServerField {
    URL,
    CLIENT_ID,
    ENCRYPTION_SECRET,
}

object SyncServerValidator {
    fun validate(
        url: String,
        clientId: String,
        encryptionSecret: String,
    ): Map<SyncServerField, String> {
        val errors = mutableMapOf<SyncServerField, String>()

        if (url.containsUnsafeCharacter()) {
            errors[SyncServerField.URL] = "URL must not contain line breaks or control characters"
        } else {
            val normalizedUrl = url.trim()
            val uri = runCatching { URI(normalizedUrl) }.getOrNull()
            val scheme = uri?.scheme?.lowercase(Locale.ROOT)
            if (
                normalizedUrl.isEmpty() ||
                uri == null ||
                scheme !in setOf("http", "https") ||
                uri.host.isNullOrBlank()
            ) {
                errors[SyncServerField.URL] = "Enter an absolute HTTP or HTTPS URL with a host"
            }
        }

        if (clientId.containsUnsafeCharacter()) {
            errors[SyncServerField.CLIENT_ID] = "Client ID must not contain line breaks or control characters"
        } else {
            val normalizedClientId = clientId.trim()
            val validUuid =
                STANDARD_UUID_PATTERN.matches(normalizedClientId) &&
                    runCatching { UUID.fromString(normalizedClientId) }.isSuccess
            if (!validUuid) {
                errors[SyncServerField.CLIENT_ID] = "Enter a valid UUID"
            }
        }

        if (encryptionSecret.containsUnsafeCharacter() || encryptionSecret.isBlank()) {
            errors[SyncServerField.ENCRYPTION_SECRET] =
                "Encryption secret must be non-empty and contain no line breaks or control characters"
        }

        return errors
    }

    private fun String.containsUnsafeCharacter(): Boolean =
        any { character ->
            character.isISOControl() ||
                Character.getType(character) == Character.LINE_SEPARATOR.toInt() ||
                Character.getType(character) == Character.PARAGRAPH_SEPARATOR.toInt()
        }
}
