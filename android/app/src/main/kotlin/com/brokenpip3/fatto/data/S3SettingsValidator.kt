package com.brokenpip3.fatto.data

/**
 * Field-level checks for the S3 sync settings.
 *
 * S3 answers a mistyped key, a newline picked up while pasting and a misspelled
 * region with the same opaque `SignatureDoesNotMatch` (or a long connection
 * timeout), so the mistakes that can be recognized without a network round trip
 * are reported while the user is still looking at the form.
 *
 * The rust wrapper repeats these checks in `sync_aws` so they also hold for
 * credentials that never went through this screen; keep the two in sync.
 */
object S3SettingsValidator {
    private const val MIN_BUCKET_LENGTH = 3
    private const val MAX_BUCKET_LENGTH = 63
    private val ACCESS_KEY_ID_LENGTH = 16..128
    private const val SECRET_ACCESS_KEY_LENGTH = 40

    private val BUCKET = Regex("^[a-z0-9][a-z0-9.-]*[a-z0-9]$")
    private val ACCESS_KEY_ID = Regex("^[A-Z0-9]+$")
    private val SECRET_ACCESS_KEY = Regex("^[A-Za-z0-9+/=]+$")

    /** Region names are `<area>-<direction>-<number>`, e.g. `eu-west-2`. */
    private val REGION = Regex("^[a-z]+(-[a-z]+)+-[0-9]+$")

    /**
     * @return a message naming the field to fix, or null when the settings can
     *   be saved. Values are expected to be trimmed by the caller.
     */
    @Suppress("ReturnCount")
    fun validate(
        bucket: String,
        region: String,
        endpointUrl: String,
        accessKeyId: String,
        secretAccessKey: String,
        encryptionSecret: String,
    ): String? {
        if (bucket.isEmpty()) return "Bucket is required"
        if (accessKeyId.isEmpty()) return "Access key ID is required"
        if (secretAccessKey.isEmpty()) return "Secret access key is required"
        if (encryptionSecret.isEmpty()) return "Encryption secret is required"

        validateBucket(bucket)?.let { return it }

        if (accessKeyId.any { it.isWhitespace() }) {
            return "Access key ID must not contain spaces or line breaks"
        }
        if (secretAccessKey.any { it.isWhitespace() }) {
            return "Secret access key must not contain spaces or line breaks"
        }

        if (endpointUrl.isNotEmpty()) {
            // An S3-compatible service: its credentials and regions are its own.
            if (!endpointUrl.startsWith("http://") && !endpointUrl.startsWith("https://")) {
                return "Endpoint URL must start with http:// or https:// (e.g. https://minio.example.com)"
            }
            return null
        }

        validateAwsAccessKeyId(accessKeyId)?.let { return it }
        validateAwsSecretAccessKey(secretAccessKey)?.let { return it }
        if (region.isNotEmpty() && !REGION.matches(region)) {
            return "'$region' is not an AWS region name. Regions look like eu-west-2 or us-east-1. " +
                "Leave the field empty to use us-east-1, or set an endpoint URL when using an " +
                "S3-compatible service"
        }
        return null
    }

    private fun validateBucket(bucket: String): String? {
        val valid =
            bucket.length in MIN_BUCKET_LENGTH..MAX_BUCKET_LENGTH &&
                BUCKET.matches(bucket) &&
                !bucket.contains("..")
        return if (valid) {
            null
        } else {
            "Invalid bucket name '$bucket'. Bucket names must be $MIN_BUCKET_LENGTH-$MAX_BUCKET_LENGTH " +
                "characters of lowercase letters, digits, dots and hyphens, and start and end with a " +
                "letter or digit"
        }
    }

    private fun validateAwsAccessKeyId(accessKeyId: String): String? =
        when {
            accessKeyId.length !in ACCESS_KEY_ID_LENGTH || !ACCESS_KEY_ID.matches(accessKeyId) ->
                "Access key ID does not look like an AWS key: it should be " +
                    "${ACCESS_KEY_ID_LENGTH.first}-${ACCESS_KEY_ID_LENGTH.last} upper-case letters and " +
                    "digits, such as AKIAIOSFODNN7EXAMPLE. Check for stray quotes, backslashes or " +
                    "characters added while typing"
            // Temporary credentials also carry a session token, which taskchampion cannot use.
            accessKeyId.startsWith("ASIA") ->
                "This is a temporary AWS access key (ASIA...), which also requires a session " +
                    "token. Use a long-lived IAM user key (AKIA...) instead"
            else -> null
        }

    private fun validateAwsSecretAccessKey(secretAccessKey: String): String? =
        if (secretAccessKey.length != SECRET_ACCESS_KEY_LENGTH ||
            !SECRET_ACCESS_KEY.matches(secretAccessKey)
        ) {
            "Secret access key does not look like an AWS secret: it should be exactly " +
                "$SECRET_ACCESS_KEY_LENGTH characters of letters, digits, '+', '/' and '='. " +
                "Backslashes and quotes are not part of the key and must not be escaped or included"
        } else {
            null
        }
}
