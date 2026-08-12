package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.S3SettingsValidator
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class S3SettingsValidatorTest {
    private fun validate(
        bucket: String = "fatto-tasks",
        region: String = "eu-west-2",
        endpointUrl: String = "",
        accessKeyId: String = ACCESS_KEY_ID,
        secretAccessKey: String = SECRET_ACCESS_KEY,
        encryptionSecret: String = "encryption-secret",
    ) = S3SettingsValidator.validate(
        bucket = bucket,
        region = region,
        endpointUrl = endpointUrl,
        accessKeyId = accessKeyId,
        secretAccessKey = secretAccessKey,
        encryptionSecret = encryptionSecret,
    )

    @Test
    fun `accepts a complete aws configuration`() {
        assertNull(validate())
        assertNull(validate(region = ""))
    }

    @Test
    fun `names the missing field`() {
        assertTrue(validate(bucket = "").orEmpty().contains("Bucket"))
        assertTrue(validate(accessKeyId = "").orEmpty().contains("Access key ID"))
        assertTrue(validate(secretAccessKey = "").orEmpty().contains("Secret access key"))
        assertTrue(validate(encryptionSecret = "").orEmpty().contains("Encryption secret"))
    }

    @Test
    fun `rejects credentials containing whitespace`() {
        assertNotNull(validate(accessKeyId = "AKIAIOSF ODNN7EXAMPL"))
        assertNotNull(validate(secretAccessKey = SECRET_ACCESS_KEY.replaceRange(5, 6, " ")))
    }

    @Test
    fun `rejects quoted or escaped aws credentials`() {
        assertNotNull(validate(accessKeyId = "\"$ACCESS_KEY_ID\""))
        assertNotNull(validate(secretAccessKey = SECRET_ACCESS_KEY.replace("/", "\\/")))
    }

    @Test
    fun `rejects temporary aws credentials`() {
        assertTrue(validate(accessKeyId = "ASIAIOSFODNN7EXAMPLE").orEmpty().contains("session token"))
    }

    @Test
    fun `rejects region names that are not aws regions`() {
        for (region in listOf("eu-west", "EU-WEST-2", "eu_west_2", "eu-west-", "europe")) {
            assertNotNull("$region should be rejected", validate(region = region))
        }
        for (region in listOf("us-east-1", "eu-west-2", "us-gov-east-1", "cn-northwest-1")) {
            assertNull("$region should be accepted", validate(region = region))
        }
    }

    @Test
    fun `rejects invalid bucket names`() {
        assertNotNull(validate(bucket = "My.Tasks"))
        assertNotNull(validate(bucket = "ab"))
        assertNotNull(validate(bucket = "-tasks"))
        assertNotNull(validate(bucket = "tasks..backup"))
        assertNull(validate(bucket = "tasks.backup-1"))
    }

    @Test
    fun `checks key and region shape only for aws`() {
        // S3-compatible services choose their own credentials and region names.
        assertNull(
            validate(
                endpointUrl = "http://localhost:9000",
                region = "garage",
                accessKeyId = "minioadmin",
                secretAccessKey = "minioadmin",
            ),
        )
    }

    @Test
    fun `requires a scheme on the endpoint url`() {
        assertNotNull(validate(endpointUrl = "minio.example.com"))
        assertNull(validate(endpointUrl = "https://minio.example.com"))
    }

    private companion object {
        const val ACCESS_KEY_ID = "AKIAIOSFODNN7EXAMPLE"
        const val SECRET_ACCESS_KEY = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
    }
}
