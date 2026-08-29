package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.S3Credentials
import com.brokenpip3.fatto.data.SettingsRepository
import com.brokenpip3.fatto.data.SyncCredentials
import com.brokenpip3.fatto.data.SyncType
import com.brokenpip3.fatto.data.TaskrcImportPreview
import com.brokenpip3.fatto.data.TaskrcImportResultType
import com.brokenpip3.fatto.data.model.TaskContext
import com.brokenpip3.fatto.ui.theme.ThemeMode
import com.brokenpip3.fatto.vm.SettingsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SettingsViewModelTest {
    private companion object {
        // The credentials AWS uses in its own documentation examples.
        const val ACCESS_KEY_ID = "AKIAIOSFODNN7EXAMPLE"
        const val SECRET_ACCESS_KEY = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
    }

    @Test
    fun `preview taskrc import does not mutate repository`() {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)

        viewModel.onTaskrcImportTextChange("context.work.read=+work")
        viewModel.previewTaskrcImport()

        assertNull(repository.appliedPreview)
        assertNotNull(viewModel.taskrcImportPreview.value)
        assertEquals(TaskrcImportResultType.ADDED, viewModel.taskrcImportPreview.value?.actions?.single()?.type)
    }

    @Test
    fun `changing import text clears previous preview`() {
        val viewModel = SettingsViewModel(FakeSettingsRepository())

        viewModel.onTaskrcImportTextChange("context.work.read=+work")
        viewModel.previewTaskrcImport()
        assertNotNull(viewModel.taskrcImportPreview.value)

        viewModel.onTaskrcImportTextChange("context.home.read=+home")

        assertNull(viewModel.taskrcImportPreview.value)
    }

    @Test
    fun `apply taskrc import delegates preview`() {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)

        viewModel.onTaskrcImportTextChange("context.work.read=+work")
        viewModel.previewTaskrcImport()
        val preview = viewModel.taskrcImportPreview.value
        viewModel.applyTaskrcImport()

        assertEquals(preview, repository.appliedPreview)
    }

    @Test
    fun `preview taskrc import classifies storage keys against stored credentials`() {
        val repository = FakeSettingsRepository()
        val uuid = "768d9f09-accd-406d-8685-7b977b83d5c6"
        repository.saveCredentials("http://localhost:8080", uuid, "my-secret")
        val viewModel = SettingsViewModel(repository)

        viewModel.onTaskrcImportTextChange(
            "sync.server.url=http://localhost:8080\nsync.server.client_id=$uuid\nsync.encryption_secret=my-secret",
        )
        viewModel.previewTaskrcImport()

        val preview = viewModel.taskrcImportPreview.value
        assertNotNull(preview)
        assertTrue(
            preview!!.actions
                .filter { it.key.startsWith("sync.") }
                .all { it.type == TaskrcImportResultType.UNCHANGED },
        )
    }

    @Test
    fun `apply taskrc import refreshes server form state`() {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)
        val uuid = "768d9f09-accd-406d-8685-7b977b83d5c6"

        viewModel.onTaskrcImportTextChange(
            "sync.server.url=http://localhost:8080\nsync.server.client_id=$uuid\nsync.encryption_secret=my-secret",
        )
        viewModel.previewTaskrcImport()
        viewModel.applyTaskrcImport()

        assertEquals("http://localhost:8080", viewModel.syncUrl.value)
        assertEquals(uuid, viewModel.clientId.value)
        assertEquals(SyncType.SERVER, viewModel.syncType.value)
        assertEquals("my-secret", viewModel.encryptionSecret.value)
    }

    @Test
    fun `apply taskrc import refreshes s3 form state and switches backend`() {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)

        viewModel.onTaskrcImportTextChange(
            "sync.aws.bucket=fatto-tasks\n" +
                "sync.aws.region=eu-central-1\n" +
                "sync.aws.endpoint=http://localhost:9000\n" +
                "sync.aws.access_key_id=minioadmin\n" +
                "sync.aws.secret_access_key=minioadmin\n" +
                "sync.encryption_secret=my-secret",
        )
        viewModel.previewTaskrcImport()
        viewModel.applyTaskrcImport()

        assertEquals("fatto-tasks", viewModel.s3Bucket.value)
        assertEquals("eu-central-1", viewModel.s3Region.value)
        assertEquals("http://localhost:9000", viewModel.s3EndpointUrl.value)
        assertEquals("minioadmin", viewModel.s3AccessKeyId.value)
        assertEquals("minioadmin", viewModel.s3SecretAccessKey.value)
        assertEquals(SyncType.S3, viewModel.syncType.value)
        assertEquals("my-secret", viewModel.encryptionSecret.value)
    }

    @Test
    fun `apply taskrc import with secret alone sets secret for both backends`() {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)

        viewModel.onTaskrcImportTextChange("sync.encryption_secret=new-secret")
        viewModel.previewTaskrcImport()
        viewModel.applyTaskrcImport()

        assertEquals("new-secret", viewModel.encryptionSecret.value)
        viewModel.onSyncTypeChange(SyncType.S3)
        assertEquals("new-secret", viewModel.encryptionSecret.value)
    }

    @Test
    fun `save with incomplete s3 fields does not switch backend`() {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)

        viewModel.onSyncTypeChange(SyncType.S3)
        viewModel.onS3BucketChange("my-bucket")
        // access key, secret access key and encryption secret left empty

        assertFalse(viewModel.save())
        assertEquals(SyncType.SERVER, repository.getSyncType())
        assertNull(repository.getS3Credentials())
    }

    @Test
    fun `save with complete s3 fields persists credentials and backend`() {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)

        viewModel.onSyncTypeChange(SyncType.S3)
        viewModel.onS3BucketChange("my-bucket")
        viewModel.onS3AccessKeyIdChange(ACCESS_KEY_ID)
        viewModel.onS3SecretAccessKeyChange(SECRET_ACCESS_KEY)
        viewModel.onSecretChange("encryption-secret")

        assertTrue(viewModel.save())
        assertNull(viewModel.syncSettingsError.value)
        assertEquals(SyncType.S3, repository.getSyncType())
        assertEquals(
            S3Credentials(
                bucket = "my-bucket",
                region = null,
                endpointUrl = null,
                accessKeyId = ACCESS_KEY_ID,
                secretAccessKey = SECRET_ACCESS_KEY,
                secret = "encryption-secret",
            ),
            repository.getS3Credentials(),
        )
    }

    @Test
    fun `save trims credentials picked up from a paste`() {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)

        viewModel.onSyncTypeChange(SyncType.S3)
        viewModel.onS3BucketChange(" my-bucket ")
        viewModel.onS3RegionChange(" eu-west-2\n")
        viewModel.onS3AccessKeyIdChange("$ACCESS_KEY_ID\n")
        viewModel.onS3SecretAccessKeyChange(" $SECRET_ACCESS_KEY ")
        viewModel.onSecretChange("encryption-secret")

        assertTrue(viewModel.save())
        val saved = repository.getS3Credentials()
        assertEquals("my-bucket", saved?.bucket)
        assertEquals("eu-west-2", saved?.region)
        assertEquals(ACCESS_KEY_ID, saved?.accessKeyId)
        assertEquals(SECRET_ACCESS_KEY, saved?.secretAccessKey)
    }

    @Test
    fun `save rejects malformed aws credentials with a specific reason`() {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)

        viewModel.onSyncTypeChange(SyncType.S3)
        viewModel.onS3BucketChange("my-bucket")
        viewModel.onS3AccessKeyIdChange("\"$ACCESS_KEY_ID\"")
        viewModel.onS3SecretAccessKeyChange(SECRET_ACCESS_KEY)
        viewModel.onSecretChange("encryption-secret")

        assertFalse(viewModel.save())
        assertNull(repository.getS3Credentials())
        assertTrue(viewModel.syncSettingsError.value.orEmpty().contains("Access key ID"))
    }

    @Test
    fun `save rejects a region that is not an aws region`() {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)

        viewModel.onSyncTypeChange(SyncType.S3)
        viewModel.onS3BucketChange("my-bucket")
        viewModel.onS3RegionChange("eu-west")
        viewModel.onS3AccessKeyIdChange(ACCESS_KEY_ID)
        viewModel.onS3SecretAccessKeyChange(SECRET_ACCESS_KEY)
        viewModel.onSecretChange("encryption-secret")

        assertFalse(viewModel.save())
        assertTrue(viewModel.syncSettingsError.value.orEmpty().contains("eu-west"))

        viewModel.onS3RegionChange("eu-west-2")
        assertTrue(viewModel.save())
        assertNull(viewModel.syncSettingsError.value)
    }

    @Test
    fun `save accepts the credentials of an s3 compatible service`() {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)

        viewModel.onSyncTypeChange(SyncType.S3)
        viewModel.onS3BucketChange("fatto-tasks")
        viewModel.onS3EndpointUrlChange("http://localhost:9000")
        viewModel.onS3AccessKeyIdChange("minioadmin")
        viewModel.onS3SecretAccessKeyChange("minioadmin")
        viewModel.onSecretChange("encryption-secret")

        assertTrue(viewModel.save())
        assertEquals("http://localhost:9000", repository.getS3Credentials()?.endpointUrl)
    }

    @Test
    fun `save with incomplete server fields does not persist`() {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)

        viewModel.onUrlChange("http://example.com:8080")
        // client id and encryption secret left empty

        assertFalse(viewModel.save())
        assertNull(repository.getCredentials())
    }

    @Test
    fun `switching sync backend shows that backend encryption secret`() {
        val repository = FakeSettingsRepository()
        repository.saveCredentials(
            url = "http://example.com:8080",
            clientId = "client-id",
            secret = "server-secret",
        )
        repository.saveS3Credentials(
            bucket = "my-bucket",
            region = null,
            endpointUrl = null,
            accessKeyId = ACCESS_KEY_ID,
            secretAccessKey = SECRET_ACCESS_KEY,
            secret = "s3-secret",
        )
        repository.setSyncType(SyncType.SERVER)
        val viewModel = SettingsViewModel(repository)

        assertEquals("server-secret", viewModel.encryptionSecret.value)

        viewModel.onSyncTypeChange(SyncType.S3)
        assertEquals("s3-secret", viewModel.encryptionSecret.value)

        assertTrue(viewModel.save())
        assertEquals("s3-secret", repository.getS3Credentials()?.secret)
    }

    @Test
    fun `auto waiting defaults off and can be enabled`() {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)

        assertFalse(viewModel.autoWaiting.value)

        viewModel.onAutoWaitingChange(true)

        assertTrue(viewModel.autoWaiting.value)
        assertTrue(repository.getAutoWaiting())
    }

    private class FakeSettingsRepository : SettingsRepository {
        override val showCompleted = MutableStateFlow(true)
        override val showInternalTags = MutableStateFlow(false)
        override val showEmptyProjects = MutableStateFlow(false)
        override val tagsPerLine = MutableStateFlow(4)
        override val dailyNotificationsEnabled = MutableStateFlow(false)
        override val notificationHour = MutableStateFlow(9)
        override val includeDueToday = MutableStateFlow(true)
        override val includeScheduledToday = MutableStateFlow(true)
        override val includeOverdue = MutableStateFlow(false)
        override val firstDayOfWeek = MutableStateFlow(Calendar.MONDAY)
        override val confirmActions = MutableStateFlow(true)
        override val hideBlockedTasksWaiting = MutableStateFlow(false)
        override val showWaitingTasks = MutableStateFlow(true)
        override val autoWaiting = MutableStateFlow(false)
        override val sortOrder = MutableStateFlow("DATE_CREATED")
        override val sortDirection = MutableStateFlow("")
        override val showPriorityBadge = MutableStateFlow(false)
        override val showUrgencyBar = MutableStateFlow(false)
        override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
        override val taskContexts: StateFlow<List<TaskContext>> = MutableStateFlow(emptyList())
        override val activeTaskContextId: StateFlow<String?> = MutableStateFlow(null)

        var appliedPreview: TaskrcImportPreview? = null

        override fun getFirstDayOfWeek(): Int = firstDayOfWeek.value

        override fun setFirstDayOfWeek(value: Int) {
            firstDayOfWeek.value = value
        }

        override fun getConfirmActions(): Boolean = confirmActions.value

        override fun setConfirmActions(enabled: Boolean) {
            confirmActions.value = enabled
        }

        override fun getHideBlockedTasksWaiting(): Boolean = hideBlockedTasksWaiting.value

        override fun setHideBlockedTasksWaiting(value: Boolean) {
            hideBlockedTasksWaiting.value = value
        }

        override fun getShowWaitingTasks(): Boolean = showWaitingTasks.value

        override fun setShowWaitingTasks(value: Boolean) {
            showWaitingTasks.value = value
        }

        override fun getAutoWaiting(): Boolean = autoWaiting.value

        override fun setAutoWaiting(value: Boolean) {
            autoWaiting.value = value
        }

        override fun getSortOrder(): String = sortOrder.value

        override fun setSortOrder(value: String) {
            sortOrder.value = value
        }

        override fun getSortDirection(): String = sortDirection.value

        override fun setSortDirection(value: String) {
            sortDirection.value = value
        }

        private var syncType: SyncType = SyncType.SERVER
        private var credentials: SyncCredentials? = null
        private var s3Credentials: S3Credentials? = null

        override fun getSyncType(): SyncType = syncType

        override fun setSyncType(type: SyncType) {
            syncType = type
        }

        override fun getCredentials(): SyncCredentials? = credentials

        override fun saveCredentials(
            url: String,
            clientId: String,
            secret: String,
        ) {
            credentials = SyncCredentials(url, clientId, secret)
        }

        override fun getS3Credentials(): S3Credentials? = s3Credentials

        override fun saveS3Credentials(
            bucket: String,
            region: String?,
            endpointUrl: String?,
            accessKeyId: String,
            secretAccessKey: String,
            secret: String,
        ) {
            s3Credentials = S3Credentials(bucket, region, endpointUrl, accessKeyId, secretAccessKey, secret)
        }

        override fun clearCredentials() {
            credentials = null
            s3Credentials = null
            syncType = SyncType.SERVER
        }

        override fun hasCredentials(): Boolean =
            when (syncType) {
                SyncType.S3 -> s3Credentials != null
                SyncType.SERVER -> credentials != null
            }

        override fun getShowCompleted(): Boolean = showCompleted.value

        override fun setShowCompleted(show: Boolean) {
            showCompleted.value = show
        }

        override fun getShowInternalTags(): Boolean = showInternalTags.value

        override fun setShowInternalTags(show: Boolean) {
            showInternalTags.value = show
        }

        override fun getShowEmptyProjects(): Boolean = showEmptyProjects.value

        override fun setShowEmptyProjects(show: Boolean) {
            showEmptyProjects.value = show
        }

        override fun getTagsPerLine(): Int = tagsPerLine.value

        override fun setTagsPerLine(count: Int) {
            tagsPerLine.value = count
        }

        override fun getDailyNotificationsEnabled(): Boolean = dailyNotificationsEnabled.value

        override fun setDailyNotificationsEnabled(enabled: Boolean) {
            dailyNotificationsEnabled.value = enabled
        }

        override fun getNotificationHour(): Int = notificationHour.value

        override fun setNotificationHour(hour: Int) {
            notificationHour.value = hour
        }

        override fun getIncludeDueToday(): Boolean = includeDueToday.value

        override fun setIncludeDueToday(enabled: Boolean) {
            includeDueToday.value = enabled
        }

        override fun getIncludeScheduledToday(): Boolean = includeScheduledToday.value

        override fun setIncludeScheduledToday(enabled: Boolean) {
            includeScheduledToday.value = enabled
        }

        override fun getIncludeOverdue(): Boolean = includeOverdue.value

        override fun setIncludeOverdue(enabled: Boolean) {
            includeOverdue.value = enabled
        }

        override fun getShowPriorityBadge(): Boolean = showPriorityBadge.value

        override fun setShowPriorityBadge(enabled: Boolean) {
            showPriorityBadge.value = enabled
        }

        override fun getShowUrgencyBar(): Boolean = showUrgencyBar.value

        override fun setShowUrgencyBar(enabled: Boolean) {
            showUrgencyBar.value = enabled
        }

        override fun getThemeMode(): ThemeMode = themeMode.value

        override fun setThemeMode(value: ThemeMode) {
            themeMode.value = value
        }

        override fun getTaskContexts(): List<TaskContext> = taskContexts.value

        override fun saveTaskContext(context: TaskContext) = Unit

        override fun replaceTaskContexts(contexts: List<TaskContext>) = Unit

        override fun applyTaskrcImport(preview: TaskrcImportPreview) {
            appliedPreview = preview
        }

        override fun deleteTaskContext(id: String) = Unit

        override fun getActiveTaskContextId(): String? = activeTaskContextId.value

        override fun setActiveTaskContextId(id: String?) = Unit
    }
}
