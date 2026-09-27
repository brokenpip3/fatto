package com.brokenpip3.fatto.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncServerValidatorTest {
    private val clientId = "768d9f09-accd-406d-8685-7b977b83d5c6"
    private val secret = "encryption-secret"

    @Test
    fun acceptsHttpAndHttpsServerUrls() {
        listOf("http://localhost:8080", "https://sync.example.com:8443/taskchampion").forEach { url ->
            assertTrue(
                "Expected $url to be accepted",
                SyncServerValidator.validate(url, clientId, secret).isEmpty(),
            )
        }
    }

    @Test
    fun acceptsUppercaseUuidHexWithoutRestrictingVersion() {
        val uppercaseUuid = "768D9F09-ACCD-406D-8685-7B977B83D5C6"

        assertTrue(SyncServerValidator.validate("https://sync.example.com", uppercaseUuid, secret).isEmpty())
    }

    @Test
    fun rejectsNonHttpOrMalformedServerUrls() {
        listOf(
            "",
            "/sync",
            "ftp://sync.example.com",
            "https:///missing-host",
            "https://bad host.example",
            "https://sync.example.com\n/path",
        ).forEach { url ->
            assertTrue(
                "Expected URL validation error for $url",
                SyncServerValidator.validate(url, clientId, secret).containsKey(SyncServerField.URL),
            )
        }
    }

    @Test
    fun rejectsNonUuidClientIds() {
        listOf("", "not-a-uuid", "768d9f09-accd-406d-8685-7b977b83d5c", "768d9f09accd406d86857b977b83d5c6").forEach { value ->
            assertTrue(
                "Expected client ID validation error for $value",
                SyncServerValidator.validate("https://sync.example.com", value, secret)
                    .containsKey(SyncServerField.CLIENT_ID),
            )
        }
    }

    @Test
    fun rejectsBlankEncryptionSecret() {
        val errors = SyncServerValidator.validate("https://sync.example.com", clientId, " \t ")

        assertTrue(errors.containsKey(SyncServerField.ENCRYPTION_SECRET))
    }

    @Test
    fun rejectsControlCharactersInEveryServerCredentialField() {
        val invalidValues = listOf("line\nbreak", "line\rbreak", "null\u0000byte")

        invalidValues.forEach { invalidValue ->
            val urlErrors = SyncServerValidator.validate("https://sync.example.com/$invalidValue", clientId, secret)
            val clientIdErrors = SyncServerValidator.validate("https://sync.example.com", invalidValue, secret)
            val secretErrors = SyncServerValidator.validate("https://sync.example.com", clientId, invalidValue)

            assertTrue(urlErrors.containsKey(SyncServerField.URL))
            assertTrue(clientIdErrors.containsKey(SyncServerField.CLIENT_ID))
            assertTrue(secretErrors.containsKey(SyncServerField.ENCRYPTION_SECRET))
        }
    }

    @Test
    fun validationErrorsDoNotEchoRejectedInput() {
        val rejectedSecret = "secret\nvalue"
        val errors = SyncServerValidator.validate("https://sync.example.com", "bad-client-id", rejectedSecret)

        assertEquals(setOf(SyncServerField.CLIENT_ID, SyncServerField.ENCRYPTION_SECRET), errors.keys)
        assertTrue(errors.values.none { it.contains("bad-client-id") || it.contains(rejectedSecret) })
    }
}
