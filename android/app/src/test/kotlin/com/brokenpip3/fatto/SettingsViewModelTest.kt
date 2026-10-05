package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.JournalTimeDefaults
import com.brokenpip3.fatto.data.S3Credentials
import com.brokenpip3.fatto.data.SettingsRepository
import com.brokenpip3.fatto.data.SyncCredentials
import com.brokenpip3.fatto.data.SyncDiagnosticEvent
import com.brokenpip3.fatto.data.SyncDiagnosticsFormatter
import com.brokenpip3.fatto.data.SyncDiagnosticsRepository
import com.brokenpip3.fatto.data.SyncServerField
import com.brokenpip3.fatto.data.SyncType
import com.brokenpip3.fatto.data.TaskSwipeAction
import com.brokenpip3.fatto.data.TaskrcImportPreview
import com.brokenpip3.fatto.data.TaskrcImportResultType
import com.brokenpip3.fatto.data.model.TaskContext
import com.brokenpip3.fatto.ui.theme.ThemeMode
import com.brokenpip3.fatto.vm.SettingsViewModel
import com.brokenpip3.fatto.vm.SyncTestState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SettingsViewModelTest {
    @Test
    fun `settings export json contains current version code`() {
        val viewModel = SettingsViewModel(FakeSettingsRepository())

        val json = viewModel.buildSettingsExportJson().getOrThrow()

        assertEquals(BuildConfig.VERSION_CODE, JSONObject(json).getInt("versionCode"))
    }

    @Test
    fun `settings import rejects newer version without mutating repository`() {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)
        val json =
            viewModel.buildSettingsExportJson().getOrThrow()
                .replace("\"versionCode\":${BuildConfig.VERSION_CODE}", "\"versionCode\":${BuildConfig.VERSION_CODE + 1}")

        val result = viewModel.validateSettingsImportJson(json)

        assertTrue(result.isFailure)
        assertTrue(repository.getShowCompleted())
    }

    @Test
    fun `settings import applies valid document and refreshes visible state`() {
        val source =
            FakeSettingsRepository().apply {
                setSyncType(SyncType.S3)
                saveS3Credentials("bucket", null, null, "access", "secret-access", "s3-secret")
                setShowCompleted(false)
                setThemeMode(ThemeMode.DARK)
            }
        val json = SettingsViewModel(source).buildSettingsExportJson().getOrThrow()
        val target = FakeSettingsRepository()
        val viewModel = SettingsViewModel(target)
        val document = viewModel.validateSettingsImportJson(json).getOrThrow()

        viewModel.applySettingsImport(document).getOrThrow()

        assertFalse(viewModel.showCompleted.value)
        assertEquals(ThemeMode.DARK, viewModel.themeMode.value)
        assertEquals(SyncType.S3, viewModel.syncType.value)
        assertEquals("bucket", viewModel.s3Bucket.value)
    }

    @Test
    fun `hook settings use their individual defaults and clear restores them`() =
        runTest {
            val repository = FakeSettingsRepository()
            val viewModel = SettingsViewModel(repository)

            assertFalse(viewModel.autoWaiting.value)
            assertTrue(viewModel.autoStopActiveOnComplete.value)

            viewModel.onAutoWaitingChange(true)
            viewModel.onAutoStopActiveOnCompleteChange(false)
            viewModel.clear()

            assertFalse(viewModel.autoWaiting.value)
            assertTrue(viewModel.autoStopActiveOnComplete.value)
            assertFalse(repository.autoWaiting.value)
            assertTrue(repository.autoStopActiveOnComplete.value)
        }

    @Test
    fun `save and test rejects invalid server credentials before persisting or syncing`() =
        runTest {
            val repository = FakeSettingsRepository()
            var syncCalls = 0
            val viewModel =
                SettingsViewModel(
                    repository,
                    syncAction = { syncCalls++ },
                    diagnosticsRepository = FakeSyncDiagnosticsRepository(),
                )
            viewModel.onUrlChange("https://sync.example.com")
            viewModel.onClientIdChange("not-a-uuid")
            viewModel.onSecretChange("encryption-secret")

            assertFalse(viewModel.saveAndTest())
            assertNull(repository.getCredentials())
            assertEquals(0, syncCalls)
            assertTrue(viewModel.validationErrors.value.containsKey(SyncServerField.CLIENT_ID))
        }

    @Test
    fun `save and test stops when server credentials cannot be persisted`() =
        runTest {
            val repository = FakeSettingsRepository().apply { credentialSaveSucceeds = false }
            var syncCalls = 0
            val viewModel =
                SettingsViewModel(
                    repository,
                    syncAction = { syncCalls++ },
                    diagnosticsRepository = FakeSyncDiagnosticsRepository(),
                )
            viewModel.onUrlChange("https://sync.example.com")
            viewModel.onClientIdChange("768d9f09-accd-406d-8685-7b977b83d5c6")
            viewModel.onSecretChange("encryption-secret")

            assertFalse(viewModel.saveAndTest())
            assertEquals(0, syncCalls)
            assertNull(repository.getCredentials())
        }

    @Test
    fun `save and test stops when active backend cannot be persisted`() =
        runTest {
            val repository = FakeSettingsRepository().apply { syncTypeSaveSucceeds = false }
            var syncCalls = 0
            val viewModel =
                SettingsViewModel(
                    repository,
                    syncAction = { syncCalls++ },
                    diagnosticsRepository = FakeSyncDiagnosticsRepository(),
                )
            viewModel.onUrlChange("https://sync.example.com")
            viewModel.onClientIdChange("768d9f09-accd-406d-8685-7b977b83d5c6")
            viewModel.onSecretChange("encryption-secret")

            assertFalse(viewModel.saveAndTest())
            assertEquals(0, syncCalls)
            assertTrue(repository.getCredentials() != null)
            assertEquals(SyncType.SERVER, repository.getSyncType())
        }

    @Test
    fun `save and test persists server credentials before successful sync`() =
        runTest {
            val repository = FakeSettingsRepository()
            var synced = false
            val viewModel =
                SettingsViewModel(
                    repository,
                    syncAction = { synced = repository.hasCredentials() },
                    diagnosticsRepository = FakeSyncDiagnosticsRepository(),
                )
            viewModel.onUrlChange(" https://sync.example.com ")
            viewModel.onClientIdChange("768d9f09-accd-406d-8685-7b977b83d5c6")
            viewModel.onSecretChange("encryption-secret")

            assertTrue(viewModel.saveAndTest())
            assertTrue(synced)
            assertEquals("https://sync.example.com", repository.getCredentials()?.url)
            assertEquals(SyncType.SERVER, repository.getSyncType())
            assertTrue(viewModel.syncTestState.value is SyncTestState.Succeeded)
        }

    @Test
    fun `save and test retains settings and sanitizes diagnostic on sync failure`() =
        runTest {
            val repository = FakeSettingsRepository()
            val diagnostics = FakeSyncDiagnosticsRepository()
            val secret = "encryption-secret"
            val viewModel =
                SettingsViewModel(
                    repository,
                    syncAction = { throw IllegalStateException("failed https://sync.example.com/path?token=$secret secret=$secret") },
                    diagnosticsRepository = diagnostics,
                )
            viewModel.onUrlChange("https://sync.example.com")
            viewModel.onClientIdChange("768d9f09-accd-406d-8685-7b977b83d5c6")
            viewModel.onSecretChange(secret)

            assertFalse(viewModel.saveAndTest())
            assertEquals(secret, repository.getCredentials()?.secret)
            assertTrue(viewModel.syncTestState.value is SyncTestState.Failed)
            val export = SyncDiagnosticsFormatter.formatEvents(diagnostics.events.value)
            assertFalse(export.contains(secret))
            assertFalse(export.contains("/path"))
            assertFalse(export.contains("?token"))
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `save and test ignores duplicate submission while sync is running`() =
        runTest {
            val repository = FakeSettingsRepository()
            val finish = CompletableDeferred<Unit>()
            var syncCalls = 0
            val viewModel =
                SettingsViewModel(
                    repository,
                    syncAction = {
                        syncCalls++
                        finish.await()
                    },
                    diagnosticsRepository = FakeSyncDiagnosticsRepository(),
                )
            viewModel.onUrlChange("https://sync.example.com")
            viewModel.onClientIdChange("768d9f09-accd-406d-8685-7b977b83d5c6")
            viewModel.onSecretChange("encryption-secret")

            val firstAttempt = backgroundScope.launch { viewModel.saveAndTest() }
            runCurrent()
            assertEquals(1, syncCalls)
            assertFalse(viewModel.saveAndTest())
            finish.complete(Unit)
            firstAttempt.join()

            assertEquals(1, syncCalls)
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
    fun `default project import mutates only on apply and refreshes observable state`() {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)

        viewModel.onTaskrcImportTextChange("default.project=Imported")
        viewModel.previewTaskrcImport()

        assertNull(repository.getDefaultProject())
        assertFalse(repository.getDefaultProjectEnabled())
        assertNull(viewModel.defaultProject.value)
        assertFalse(viewModel.defaultProjectEnabled.value)

        viewModel.applyTaskrcImport()

        assertEquals("Imported", repository.getDefaultProject())
        assertTrue(repository.getDefaultProjectEnabled())
        assertEquals("Imported", viewModel.defaultProject.value)
        assertTrue(viewModel.defaultProjectEnabled.value)
    }

    @Test
    fun `selecting default stores value and enables while disabling preserves value`() {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)

        viewModel.onDefaultProjectSelected("  Inbox  ")
        assertEquals("Inbox", viewModel.defaultProject.value)
        assertTrue(viewModel.defaultProjectEnabled.value)
        assertTrue(repository.getDefaultProjectEnabled())

        viewModel.onDefaultProjectEnabledChange(false)
        assertFalse(viewModel.defaultProjectEnabled.value)
        assertEquals("Inbox", viewModel.defaultProject.value)
        assertEquals("Inbox", repository.getDefaultProject())
    }

    @Test
    fun `enabling default project without a value remains disabled`() {
        val viewModel = SettingsViewModel(FakeSettingsRepository())

        viewModel.onDefaultProjectEnabledChange(true)

        assertFalse(viewModel.defaultProjectEnabled.value)
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
    fun `save s3 with incomplete fields does not switch backend`() =
        runTest {
            val repository = FakeSettingsRepository()
            val viewModel = SettingsViewModel(repository)

            viewModel.onSyncTypeChange(SyncType.S3)
            viewModel.onS3BucketChange("my-bucket")
            // access key, secret access key and encryption secret left empty

            assertFalse(viewModel.saveAndTest())
            assertEquals(SyncType.SERVER, repository.getSyncType())
            assertNull(repository.getS3Credentials())
        }

    @Test
    fun `save s3 with complete fields persists credentials and backend`() =
        runTest {
            val repository = FakeSettingsRepository()
            val viewModel = SettingsViewModel(repository)

            viewModel.onSyncTypeChange(SyncType.S3)
            viewModel.onS3BucketChange("my-bucket")
            viewModel.onS3AccessKeyIdChange("access-key")
            viewModel.onS3SecretAccessKeyChange("secret-key")
            viewModel.onSecretChange("encryption-secret")

            assertTrue(viewModel.saveAndTest())
            assertEquals(SyncType.S3, repository.getSyncType())
            assertEquals(
                S3Credentials(
                    bucket = "my-bucket",
                    region = null,
                    endpointUrl = null,
                    accessKeyId = "access-key",
                    secretAccessKey = "secret-key",
                    secret = "encryption-secret",
                ),
                repository.getS3Credentials(),
            )
        }

    @Test
    fun `switching sync backend shows that backend encryption secret`() =
        runTest {
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
                accessKeyId = "access-key",
                secretAccessKey = "secret-key",
                secret = "s3-secret",
            )
            repository.setSyncType(SyncType.SERVER)
            val viewModel = SettingsViewModel(repository)

            assertEquals("server-secret", viewModel.encryptionSecret.value)

            viewModel.onSyncTypeChange(SyncType.S3)
            assertEquals("s3-secret", viewModel.encryptionSecret.value)

            assertTrue(viewModel.saveAndTest())
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

    @Test
    fun `swipe actions load and persist independently`() {
        val repository =
            FakeSettingsRepository().apply {
                setSwipeStartToEndAction(TaskSwipeAction.DELETE)
                setSwipeEndToStartAction(TaskSwipeAction.COMPLETE)
            }
        val viewModel = SettingsViewModel(repository)

        assertEquals(TaskSwipeAction.DELETE, viewModel.swipeStartToEndAction.value)
        assertEquals(TaskSwipeAction.COMPLETE, viewModel.swipeEndToStartAction.value)

        viewModel.onSwipeStartToEndActionChange(TaskSwipeAction.NONE)
        viewModel.onSwipeEndToStartActionChange(TaskSwipeAction.DELETE)

        assertEquals(TaskSwipeAction.NONE, repository.getSwipeStartToEndAction())
        assertEquals(TaskSwipeAction.DELETE, repository.getSwipeEndToStartAction())
    }

    private class FakeSyncDiagnosticsRepository : SyncDiagnosticsRepository {
        private val _events = MutableStateFlow(emptyList<SyncDiagnosticEvent>())
        override val events: StateFlow<List<SyncDiagnosticEvent>> = _events

        override fun append(event: SyncDiagnosticEvent) {
            _events.value = SyncDiagnosticsFormatter.appendBounded(_events.value, event)
        }

        override fun clear() {
            _events.value = emptyList()
        }
    }

    private class FakeSettingsRepository : SettingsRepository {
        override val showCompleted = MutableStateFlow(true)
        override val showInternalTags = MutableStateFlow(false)
        override val showEmptyProjects = MutableStateFlow(false)
        override val defaultProjectEnabled = MutableStateFlow(false)
        override val defaultProject = MutableStateFlow<String?>(null)
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
        override val autoStopActiveOnComplete = MutableStateFlow(true)
        override val sortOrder = MutableStateFlow("DATE_CREATED")
        override val sortDirection = MutableStateFlow("")
        override val showPriorityBadge = MutableStateFlow(false)
        override val showUrgencyBar = MutableStateFlow(false)
        override val swipeStartToEndAction = MutableStateFlow(TaskSwipeAction.NONE)
        override val swipeEndToStartAction = MutableStateFlow(TaskSwipeAction.NONE)
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

        override fun getAutoStopActiveOnComplete(): Boolean = autoStopActiveOnComplete.value

        override fun setAutoStopActiveOnComplete(enabled: Boolean) {
            autoStopActiveOnComplete.value = enabled
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
        var credentialSaveSucceeds = true
        var s3CredentialSaveSucceeds = true
        var syncTypeSaveSucceeds = true
        private var s3Credentials: S3Credentials? = null

        override fun getSyncType(): SyncType = syncType

        override fun setSyncType(type: SyncType): Boolean {
            if (!syncTypeSaveSucceeds) return false
            syncType = type
            return true
        }

        override fun getCredentials(): SyncCredentials? = credentials

        override fun saveCredentials(
            url: String,
            clientId: String,
            secret: String,
        ): Boolean {
            if (!credentialSaveSucceeds) return false
            credentials = SyncCredentials(url, clientId, secret)
            return true
        }

        override fun getS3Credentials(): S3Credentials? = s3Credentials

        override fun replaceSyncSettings(
            type: SyncType,
            serverCredentials: SyncCredentials?,
            s3Credentials: S3Credentials?,
        ): Boolean {
            syncType = type
            credentials = serverCredentials
            this.s3Credentials = s3Credentials
            return true
        }

        override fun saveS3Credentials(
            bucket: String,
            region: String?,
            endpointUrl: String?,
            accessKeyId: String,
            secretAccessKey: String,
            secret: String,
        ): Boolean {
            if (!s3CredentialSaveSucceeds) return false
            s3Credentials = S3Credentials(bucket, region, endpointUrl, accessKeyId, secretAccessKey, secret)
            return true
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

        override fun getDefaultProjectEnabled(): Boolean = defaultProjectEnabled.value

        override fun setDefaultProjectEnabled(enabled: Boolean) {
            defaultProjectEnabled.value = enabled && defaultProject.value != null
        }

        override fun getDefaultProject(): String? = defaultProject.value

        override fun setDefaultProject(project: String?) {
            defaultProject.value = project?.trim()?.takeIf { it.isNotEmpty() }
        }

        private var _journalTimeEnabled = false
        private var _journalStartAnnotation = JournalTimeDefaults.START_ANNOTATION
        private var _journalStopAnnotation = JournalTimeDefaults.STOP_ANNOTATION

        override fun getJournalTimeEnabled(): Boolean = _journalTimeEnabled

        override fun setJournalTimeEnabled(enabled: Boolean) {
            _journalTimeEnabled = enabled
        }

        override fun getJournalStartAnnotation(): String = _journalStartAnnotation

        override fun setJournalStartAnnotation(text: String) {
            _journalStartAnnotation = text
        }

        override fun getJournalStopAnnotation(): String = _journalStopAnnotation

        override fun setJournalStopAnnotation(text: String) {
            _journalStopAnnotation = text
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

        override fun getSwipeStartToEndAction(): TaskSwipeAction = swipeStartToEndAction.value

        override fun setSwipeStartToEndAction(value: TaskSwipeAction) {
            swipeStartToEndAction.value = value
        }

        override fun getSwipeEndToStartAction(): TaskSwipeAction = swipeEndToStartAction.value

        override fun setSwipeEndToStartAction(value: TaskSwipeAction) {
            swipeEndToStartAction.value = value
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
            setDefaultProject(preview.defaultProjectAfter)
            setDefaultProjectEnabled(preview.defaultProjectEnabledAfter)
        }

        override fun deleteTaskContext(id: String) = Unit

        override fun getActiveTaskContextId(): String? = activeTaskContextId.value

        override fun setActiveTaskContextId(id: String?) = Unit
    }
}
