package com.brokenpip3.fatto.data

import java.net.URI
import java.util.Locale

enum class SyncS3Field {
    BUCKET,
    REGION,
    ENDPOINT_URL,
    ACCESS_KEY_ID,
    SECRET_ACCESS_KEY,
    ENCRYPTION_SECRET,
}

object SyncS3Validator {
    fun validate(
        bucket: String,
        region: String,
        endpointUrl: String,
        accessKeyId: String,
        secretAccessKey: String,
        encryptionSecret: String,
    ): Map<SyncS3Field, String> {
        val errors = mutableMapOf<SyncS3Field, String>()

        fun hasControl(value: String): Boolean =
            value.any { character ->
                character.isISOControl() ||
                    Character.getType(character) == Character.LINE_SEPARATOR.toInt() ||
                    Character.getType(character) == Character.PARAGRAPH_SEPARATOR.toInt()
            }

        fun required(
            field: SyncS3Field,
            value: String,
            label: String,
        ) {
            when {
                hasControl(value) -> errors[field] = "Remove unsupported control characters"
                value.isBlank() -> errors[field] = "Enter $label"
            }
        }

        required(SyncS3Field.BUCKET, bucket, "a bucket name")
        required(SyncS3Field.ACCESS_KEY_ID, accessKeyId, "an access key ID")
        required(SyncS3Field.SECRET_ACCESS_KEY, secretAccessKey, "a secret access key")
        required(SyncS3Field.ENCRYPTION_SECRET, encryptionSecret, "an encryption secret")

        if (hasControl(region)) {
            errors[SyncS3Field.REGION] = "Remove unsupported control characters"
        }
        if (hasControl(endpointUrl)) {
            errors[SyncS3Field.ENDPOINT_URL] = "Remove unsupported control characters"
        } else if (endpointUrl.isNotBlank()) {
            val uri = runCatching { URI(endpointUrl.trim()) }.getOrNull()
            val scheme = uri?.scheme?.lowercase(Locale.ROOT)
            if (uri == null || scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
                errors[SyncS3Field.ENDPOINT_URL] = "Enter an absolute HTTP or HTTPS URL with a host"
            }
        }

        return errors
    }
}
