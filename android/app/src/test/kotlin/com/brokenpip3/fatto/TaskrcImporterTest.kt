package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.S3Credentials
import com.brokenpip3.fatto.data.SyncCredentials
import com.brokenpip3.fatto.data.SyncType
import com.brokenpip3.fatto.data.TaskrcImportResultType
import com.brokenpip3.fatto.data.TaskrcImporter
import com.brokenpip3.fatto.data.model.TaskContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TaskrcImporterTest {
    @Test
    fun `preview imports contexts active context and weekstart`() {
        val preview =
            TaskrcImporter.preview(
                text =
                    """
                    # contexts
                    context.work.read=project:Work +office
                    context=work
                    weekstart=Sunday
                    """.trimIndent(),
                existingContexts = emptyList(),
                currentActiveContextId = null,
                currentFirstDayOfWeek = Calendar.MONDAY,
            )

        assertEquals(
            listOf(TaskrcImportResultType.ADDED, TaskrcImportResultType.UPDATED, TaskrcImportResultType.ACTIVATED),
            preview.types(),
        )
        assertEquals(Calendar.SUNDAY, preview.firstDayOfWeekAfter)
        assertEquals("project:Work +office", preview.contextsAfter.single().expressionText)
        assertEquals(preview.contextsAfter.single().id, preview.activeContextIdAfter)
    }

    @Test
    fun `preview updates contexts by name and preserves id`() {
        val existing = listOf(TaskContext(id = "work-id", name = "work", expressionText = "+old"))

        val preview = TaskrcImporter.preview("context.work.read=+new", existing, null, Calendar.MONDAY)

        assertEquals("work-id", preview.contextsAfter.single().id)
        assertEquals("+new", preview.contextsAfter.single().expressionText)
        assertEquals(TaskrcImportResultType.UPDATED, preview.actions.single().type)
    }

    @Test
    fun `unchanged context is logged without changing expression`() {
        val existing = listOf(TaskContext(id = "work-id", name = "work", expressionText = "+work"))

        val preview = TaskrcImporter.preview("context.work.read=+work", existing, null, Calendar.MONDAY)

        assertEquals("+work", preview.contextsAfter.single().expressionText)
        assertEquals(TaskrcImportResultType.UNCHANGED, preview.actions.single().type)
    }

    @Test
    fun `invalid context read is not applied`() {
        val preview = TaskrcImporter.preview("context.work.read=priority:H", emptyList(), null, Calendar.MONDAY)

        assertTrue(preview.contextsAfter.isEmpty())
        assertEquals(TaskrcImportResultType.ERROR, preview.actions.single().type)
    }

    @Test
    fun `unsupported lines are skipped with line numbers`() {
        val preview = TaskrcImporter.preview("include holidays.en-US.rc", emptyList(), null, Calendar.MONDAY)

        assertEquals(TaskrcImportResultType.SKIPPED, preview.actions.single().type)
        assertEquals(1, preview.actions.single().lineNumber)
    }

    @Test
    fun `active context reports error when context is missing`() {
        val preview = TaskrcImporter.preview("context=missing", emptyList(), null, Calendar.MONDAY)

        assertEquals(TaskrcImportResultType.ERROR, preview.actions.single().type)
        assertEquals(null, preview.activeContextIdAfter)
    }

    @Test
    fun `unsupported weekstart reports error`() {
        val preview = TaskrcImporter.preview("weekstart=Tuesday", emptyList(), null, Calendar.MONDAY)

        assertEquals(TaskrcImportResultType.ERROR, preview.actions.single().type)
        assertEquals(Calendar.MONDAY, preview.firstDayOfWeekAfter)
    }

    @Test
    fun `preview imports complete server credentials and switches sync type`() {
        val uuid = "768d9f09-accd-406d-8685-7b977b83d5c6"
        val preview =
            TaskrcImporter.preview(
                text =
                    """
                    sync.server.url=http://localhost:8080
                    sync.server.client_id=$uuid
                    sync.encryption_secret=my-secret
                    """.trimIndent(),
                existingContexts = emptyList(),
                currentActiveContextId = null,
                currentFirstDayOfWeek = Calendar.MONDAY,
                currentSyncCredentials = null,
                currentS3Credentials = null,
                currentSyncType = SyncType.S3,
            )

        assertFalse(preview.hasErrors)
        assertEquals(SyncType.SERVER, preview.syncTypeAfter)
        assertEquals(SyncCredentials("http://localhost:8080", uuid, "my-secret"), preview.serverCredentialsAfter)
        assertNull(preview.s3CredentialsAfter)
        assertEquals(
            listOf(
                TaskrcImportResultType.ADDED,
                TaskrcImportResultType.ADDED,
                TaskrcImportResultType.ADDED,
                TaskrcImportResultType.UPDATED,
            ),
            preview.types(),
        )
        assertEquals("Sync type switched to Server", preview.actions.last().message)
    }

    @Test
    fun `preview imports complete s3 credentials with optional endpoint and skips empty region`() {
        val preview =
            TaskrcImporter.preview(
                text =
                    """
                    sync.aws.bucket=fatto-tasks
                    sync.aws.region=
                    sync.aws.endpoint=http://localhost:9000
                    sync.aws.access_key_id=minioadmin
                    sync.aws.secret_access_key=minioadmin
                    sync.encryption_secret=my-secret
                    """.trimIndent(),
                existingContexts = emptyList(),
                currentActiveContextId = null,
                currentFirstDayOfWeek = Calendar.MONDAY,
                currentSyncCredentials = null,
                currentS3Credentials = null,
                currentSyncType = SyncType.SERVER,
            )

        assertFalse(preview.hasErrors)
        assertEquals(SyncType.S3, preview.syncTypeAfter)
        assertEquals(
            S3Credentials(
                bucket = "fatto-tasks",
                region = null,
                endpointUrl = "http://localhost:9000",
                accessKeyId = "minioadmin",
                secretAccessKey = "minioadmin",
                secret = "my-secret",
            ),
            preview.s3CredentialsAfter,
        )
        assertNull(preview.serverCredentialsAfter)
        assertTrue(preview.actions.any { it.type == TaskrcImportResultType.SKIPPED && it.message == "Empty value ignored" })
    }

    @Test
    fun `both complete credential groups report error and block apply`() {
        val uuid = "768d9f09-accd-406d-8685-7b977b83d5c6"
        val preview =
            TaskrcImporter.preview(
                text =
                    """
                    sync.server.url=http://localhost:8080
                    sync.server.client_id=$uuid
                    sync.aws.bucket=fatto-tasks
                    sync.aws.access_key_id=minioadmin
                    sync.aws.secret_access_key=minioadmin
                    sync.encryption_secret=my-secret
                    """.trimIndent(),
                existingContexts = emptyList(),
                currentActiveContextId = null,
                currentFirstDayOfWeek = Calendar.MONDAY,
                currentSyncCredentials = null,
                currentS3Credentials = null,
                currentSyncType = SyncType.SERVER,
            )

        assertTrue(preview.hasErrors)
        assertEquals(TaskrcImportResultType.ERROR, preview.actions.last().type)
        assertNull(preview.serverCredentialsAfter)
        assertNull(preview.s3CredentialsAfter)
        assertEquals(SyncType.SERVER, preview.syncTypeAfter)
    }

    @Test
    fun `incomplete server group reports error listing missing keys`() {
        val preview =
            TaskrcImporter.preview(
                text = "sync.server.url=http://localhost:8080",
                existingContexts = emptyList(),
                currentActiveContextId = null,
                currentFirstDayOfWeek = Calendar.MONDAY,
                currentSyncCredentials = null,
                currentS3Credentials = null,
                currentSyncType = SyncType.SERVER,
            )

        assertTrue(preview.hasErrors)
        val error = preview.actions.single { it.type == TaskrcImportResultType.ERROR }
        assertTrue(error.message.contains("sync.server.client_id"))
        assertTrue(error.message.contains("sync.encryption_secret"))
        assertNull(preview.serverCredentialsAfter)
    }

    @Test
    fun `invalid client id reports error and does not import server group`() {
        val preview =
            TaskrcImporter.preview(
                text =
                    """
                    sync.server.url=http://localhost:8080
                    sync.server.client_id=not-a-uuid
                    sync.encryption_secret=my-secret
                    """.trimIndent(),
                existingContexts = emptyList(),
                currentActiveContextId = null,
                currentFirstDayOfWeek = Calendar.MONDAY,
                currentSyncCredentials = null,
                currentS3Credentials = null,
                currentSyncType = SyncType.SERVER,
            )

        assertTrue(preview.hasErrors)
        assertEquals(
            TaskrcImportResultType.ERROR,
            preview.actions.single { it.key == "sync.server.client_id" }.type,
        )
        assertNull(preview.serverCredentialsAfter)
        assertEquals(SyncType.SERVER, preview.syncTypeAfter)
    }

    @Test
    fun `empty storage values are skipped and never clobber stored settings`() {
        val preview =
            TaskrcImporter.preview(
                text = "sync.aws.region=\nsync.server.url=",
                existingContexts = emptyList(),
                currentActiveContextId = null,
                currentFirstDayOfWeek = Calendar.MONDAY,
                currentSyncCredentials = SyncCredentials("http://old:8080", "768d9f09-accd-406d-8685-7b977b83d5c6", "old-secret"),
                currentS3Credentials = null,
                currentSyncType = SyncType.SERVER,
            )

        assertFalse(preview.hasErrors)
        assertEquals(2, preview.actions.size)
        assertTrue(preview.actions.all { it.type == TaskrcImportResultType.SKIPPED && it.message == "Empty value ignored" })
        assertNull(preview.serverCredentialsAfter)
        assertNull(preview.s3CredentialsAfter)
        assertNull(preview.encryptionSecretAfter)
    }

    @Test
    fun `taskd keys are skipped with dedicated message`() {
        val preview =
            TaskrcImporter.preview(
                text = "taskd.certificate=/path/to/cert.pem",
                existingContexts = emptyList(),
                currentActiveContextId = null,
                currentFirstDayOfWeek = Calendar.MONDAY,
            )

        val action = preview.actions.single()
        assertEquals(TaskrcImportResultType.SKIPPED, action.type)
        assertEquals("Taskwarrior 2.x taskd settings are not supported and were not imported", action.message)
    }

    @Test
    fun `secret alone is imported for both backends without switching sync type`() {
        val preview =
            TaskrcImporter.preview(
                text = "sync.encryption_secret=new-secret",
                existingContexts = emptyList(),
                currentActiveContextId = null,
                currentFirstDayOfWeek = Calendar.MONDAY,
                currentSyncCredentials = SyncCredentials("http://localhost:8080", "768d9f09-accd-406d-8685-7b977b83d5c6", "old-secret"),
                currentS3Credentials = null,
                currentSyncType = SyncType.SERVER,
            )

        assertEquals("new-secret", preview.encryptionSecretAfter)
        assertNull(preview.serverCredentialsAfter)
        assertNull(preview.s3CredentialsAfter)
        assertEquals(SyncType.SERVER, preview.syncTypeAfter)
        assertEquals(TaskrcImportResultType.UPDATED, preview.actions.single().type)
    }

    @Test
    fun `secret matching both stored secrets is unchanged`() {
        val preview =
            TaskrcImporter.preview(
                text = "sync.encryption_secret=same-secret",
                existingContexts = emptyList(),
                currentActiveContextId = null,
                currentFirstDayOfWeek = Calendar.MONDAY,
                currentSyncCredentials = SyncCredentials("http://localhost:8080", "768d9f09-accd-406d-8685-7b977b83d5c6", "same-secret"),
                currentS3Credentials = S3Credentials("bucket", null, null, "key", "secret", "same-secret"),
                currentSyncType = SyncType.SERVER,
            )

        assertEquals(TaskrcImportResultType.UNCHANGED, preview.actions.single().type)
        assertEquals("same-secret", preview.encryptionSecretAfter)
    }

    @Test
    fun `storage keys matching stored credentials are unchanged`() {
        val uuid = "768d9f09-accd-406d-8685-7b977b83d5c6"
        val preview =
            TaskrcImporter.preview(
                text =
                    """
                    sync.server.url=http://localhost:8080
                    sync.server.client_id=$uuid
                    sync.encryption_secret=my-secret
                    """.trimIndent(),
                existingContexts = emptyList(),
                currentActiveContextId = null,
                currentFirstDayOfWeek = Calendar.MONDAY,
                currentSyncCredentials = SyncCredentials("http://localhost:8080", uuid, "my-secret"),
                currentS3Credentials = null,
                currentSyncType = SyncType.SERVER,
            )

        assertEquals(
            listOf(TaskrcImportResultType.UNCHANGED, TaskrcImportResultType.UNCHANGED, TaskrcImportResultType.UNCHANGED),
            preview.types(),
        )
        assertEquals(SyncCredentials("http://localhost:8080", uuid, "my-secret"), preview.serverCredentialsAfter)
    }

    @Test
    fun `storage keys differing from stored credentials are updated`() {
        val uuid = "768d9f09-accd-406d-8685-7b977b83d5c6"
        val preview =
            TaskrcImporter.preview(
                text =
                    """
                    sync.server.url=http://new:8080
                    sync.server.client_id=$uuid
                    sync.encryption_secret=new-secret
                    """.trimIndent(),
                existingContexts = emptyList(),
                currentActiveContextId = null,
                currentFirstDayOfWeek = Calendar.MONDAY,
                currentSyncCredentials = SyncCredentials("http://old:8080", uuid, "old-secret"),
                currentS3Credentials = null,
                currentSyncType = SyncType.SERVER,
            )

        assertEquals(
            listOf(TaskrcImportResultType.UPDATED, TaskrcImportResultType.UNCHANGED, TaskrcImportResultType.UPDATED),
            preview.types(),
        )
        assertEquals(SyncCredentials("http://new:8080", uuid, "new-secret"), preview.serverCredentialsAfter)
    }

    @Test
    fun `incomplete group blocks import of a complete group`() {
        val preview =
            TaskrcImporter.preview(
                text =
                    """
                    sync.server.url=http://localhost:8080
                    sync.aws.bucket=fatto-tasks
                    sync.aws.access_key_id=minioadmin
                    sync.aws.secret_access_key=minioadmin
                    sync.encryption_secret=my-secret
                    """.trimIndent(),
                existingContexts = emptyList(),
                currentActiveContextId = null,
                currentFirstDayOfWeek = Calendar.MONDAY,
                currentSyncCredentials = null,
                currentS3Credentials = null,
                currentSyncType = SyncType.SERVER,
            )

        assertTrue(preview.hasErrors)
        assertNull(preview.s3CredentialsAfter)
        assertEquals(SyncType.SERVER, preview.syncTypeAfter)
    }

    private fun com.brokenpip3.fatto.data.TaskrcImportPreview.types(): List<TaskrcImportResultType> {
        return actions.map { it.type }
    }
}
