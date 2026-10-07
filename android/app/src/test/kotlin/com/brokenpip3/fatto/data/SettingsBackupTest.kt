package com.brokenpip3.fatto.data

import com.brokenpip3.fatto.data.model.TaskContext
import com.brokenpip3.fatto.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SettingsBackupTest {
    @Test
    fun `older backups restore the default font size`() {
        val json = JSONObject(backupService().exportJson().getOrThrow())
        json.getJSONObject("settings").remove("fontSizePercent")
        val target = FakeSettingsRepository().apply { setFontSizePercent(150) }
        val service = backupService(target)
        service.applyImport(service.parseImport(json.toString()).getOrThrow()).getOrThrow()
        assertEquals(100, target.getFontSizePercent())
    }

    @Test
    fun `export includes the app font size`() {
        val json = JSONObject(backupService().exportJson().getOrThrow())
        assertEquals(100, json.getJSONObject("settings").getInt("fontSizePercent"))
    }

    @Test
    fun `import rejects an out of range font size`() {
        val json = JSONObject(backupService().exportJson().getOrThrow())
        json.getJSONObject("settings").put("fontSizePercent", 500)
        assertTrue(backupService().parseImport(json.toString()).isFailure)
    }

    @Test
    fun `export includes format and android version code`() {
        val service = backupService(currentVersionCode = 23, currentVersionName = "1.2.3")

        val json = service.exportJson().getOrThrow()
        val document = JSONObject(json)

        assertEquals(2, document.getInt("formatVersion"))
        assertEquals(23, document.getInt("versionCode"))
        assertEquals("1.2.3", document.getString("versionName"))
        assertEquals("2026-09-29T12:00:00Z", document.getString("exportedAt"))
    }

    @Test
    fun `round trip preserves sync credentials s3 optional fields and display settings`() {
        val source =
            FakeSettingsRepository().apply {
                setSyncType(SyncType.S3)
                saveCredentials("https://sync.example.com", "client", "server-secret")
                saveS3Credentials("bucket", null, null, "access", "secret-access", "s3-secret")
                setShowCompleted(false)
                setShowInternalTags(true)
                setTagsPerLine(2)
                setSortOrder("DUE_DATE")
                setSortDirection("DESCENDING")
                setSwipeStartToEndAction(TaskSwipeAction.COMPLETE)
                setSwipeEndToStartAction(TaskSwipeAction.DELETE)
                setThemeMode(ThemeMode.DARK)
                setFontSizePercent(140)
                replaceTaskContexts(listOf(TaskContext(id = "ctx", name = "Work", expressionText = "+work")))
                setActiveTaskContextId("ctx")
            }
        val target = FakeSettingsRepository()
        val json = backupService(source).exportJson().getOrThrow()
        val document = backupService(target).parseImport(json).getOrThrow()

        backupService(target).applyImport(document).getOrThrow()

        assertEquals(SyncType.S3, target.getSyncType())
        assertEquals("server-secret", target.getCredentials()?.secret)
        assertEquals("bucket", target.getS3Credentials()?.bucket)
        assertEquals(null, target.getS3Credentials()?.region)
        assertEquals(null, target.getS3Credentials()?.endpointUrl)
        assertFalse(target.getShowCompleted())
        assertTrue(target.getShowInternalTags())
        assertEquals(2, target.getTagsPerLine())
        assertEquals("DUE_DATE", target.getSortOrder())
        assertEquals("DESCENDING", target.getSortDirection())
        assertEquals(TaskSwipeAction.COMPLETE, target.getSwipeStartToEndAction())
        assertEquals(TaskSwipeAction.DELETE, target.getSwipeEndToStartAction())
        assertEquals(ThemeMode.DARK, target.getThemeMode())
        assertEquals(140, target.getFontSizePercent())
        assertEquals(listOf(TaskContext(id = "ctx", name = "Work", expressionText = "+work")), target.getTaskContexts())
        assertEquals("ctx", target.getActiveTaskContextId())
    }

    @Test
    fun `decode rejects backup from newer android version code`() {
        val json =
            backupService(currentVersionCode = 23).exportJson().getOrThrow()
                .replace("\"versionCode\":23", "\"versionCode\":24")

        val result = backupService(currentVersionCode = 23).parseImport(json)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SettingsBackupError.NewerVersion)
    }

    @Test
    fun `decode rejects unsupported format version`() {
        val json =
            backupService().exportJson().getOrThrow()
                .replace("\"formatVersion\":2", "\"formatVersion\":3")

        val result = backupService().parseImport(json)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SettingsBackupError.UnsupportedFormat)
    }

    @Test
    fun `decode rejects invalid json safely`() {
        val result = backupService().parseImport("not json")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SettingsBackupError.InvalidJson)
    }

    @Test
    fun `decode rejects missing settings field`() {
        val json = backupService().exportJson().getOrThrow()
        val root = JSONObject(json)
        root.getJSONObject("settings").remove("showCompleted")

        val result = backupService().parseImport(root.toString())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SettingsBackupError.InvalidJson)
    }

    @Test
    fun `decode rejects unknown enum values`() {
        val json = backupService().exportJson().getOrThrow()
        val root = JSONObject(json)
        root.getJSONObject("settings").put("syncType", "unknown")

        val result = backupService().parseImport(root.toString())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SettingsBackupError.InvalidJson)
    }

    @Test
    fun `import without credentials clears existing credentials`() {
        val repository =
            FakeSettingsRepository().apply {
                saveCredentials("https://old.example.com", "old-client", "old-secret")
                saveS3Credentials("old-bucket", null, null, "old-access", "old-key", "old-secret")
            }
        val backup = backupService(FakeSettingsRepository()).exportJson().getOrThrow()
        val document = backupService(repository).parseImport(backup).getOrThrow()

        backupService(repository).applyImport(document).getOrThrow()

        assertEquals(null, repository.getCredentials())
        assertEquals(null, repository.getS3Credentials())
    }

    @Test
    fun `import reports sync settings persistence failure`() {
        val repository = FakeSettingsRepository().apply { syncSettingsSaveSucceeds = false }
        val source = FakeSettingsRepository().apply { setShowCompleted(false) }
        val document =
            backupService(source).exportJson().getOrThrow().let {
                backupService(repository).parseImport(it).getOrThrow()
            }

        val result = backupService(repository).applyImport(document)

        assertTrue(result.isFailure)
        assertTrue(repository.getShowCompleted())
    }

    private fun backupService(
        repository: FakeSettingsRepository = FakeSettingsRepository(),
        currentVersionCode: Int = 23,
        currentVersionName: String = "1.2.3",
    ): SettingsBackupService =
        SettingsBackupService(
            repository = repository,
            codec =
                SettingsBackupCodec(
                    currentVersionCode = currentVersionCode,
                    currentVersionName = currentVersionName,
                    clock = { "2026-09-29T12:00:00Z" },
                ),
        )

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
        override val fontSizePercent = MutableStateFlow(100)
        override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
        private val contexts = MutableStateFlow<List<TaskContext>>(emptyList())
        override val taskContexts: StateFlow<List<TaskContext>> = contexts
        private val activeContext = MutableStateFlow<String?>(null)
        override val activeTaskContextId: StateFlow<String?> = activeContext
        private var syncType = SyncType.SERVER
        private var credentials: SyncCredentials? = null
        private var s3Credentials: S3Credentials? = null
        var syncSettingsSaveSucceeds = true

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

        override fun getSyncType(): SyncType = syncType

        override fun setSyncType(type: SyncType): Boolean {
            syncType = type
            return true
        }

        override fun getCredentials(): SyncCredentials? = credentials

        override fun saveCredentials(
            url: String,
            clientId: String,
            secret: String,
        ): Boolean {
            credentials = SyncCredentials(url, clientId, secret)
            return true
        }

        override fun getS3Credentials(): S3Credentials? = s3Credentials

        override fun replaceSyncSettings(
            type: SyncType,
            serverCredentials: SyncCredentials?,
            s3Credentials: S3Credentials?,
        ): Boolean {
            if (!syncSettingsSaveSucceeds) return false
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
            s3Credentials = S3Credentials(bucket, region, endpointUrl, accessKeyId, secretAccessKey, secret)
            return true
        }

        override fun clearCredentials() {
            credentials = null
            s3Credentials = null
            syncType = SyncType.SERVER
        }

        override fun hasCredentials(): Boolean = credentials != null || s3Credentials != null

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
            defaultProjectEnabled.value = enabled
        }

        override fun getDefaultProject(): String? = defaultProject.value

        override fun setDefaultProject(project: String?) {
            defaultProject.value = project
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

        override fun getFontSizePercent(): Int = fontSizePercent.value

        override fun setFontSizePercent(value: Int) {
            fontSizePercent.value = value.coerceIn(80, 150)
        }

        override fun getThemeMode(): ThemeMode = themeMode.value

        override fun setThemeMode(value: ThemeMode) {
            themeMode.value = value
        }

        override fun getTaskContexts(): List<TaskContext> = contexts.value

        override fun saveTaskContext(context: TaskContext) {
            contexts.value = contexts.value + context
        }

        override fun replaceTaskContexts(contexts: List<TaskContext>) {
            this.contexts.value = contexts
        }

        override fun applyTaskrcImport(preview: TaskrcImportPreview) = Unit

        override fun deleteTaskContext(id: String) {
            contexts.value = contexts.value.filterNot { it.id == id }
        }

        override fun getActiveTaskContextId(): String? = activeContext.value

        override fun setActiveTaskContextId(id: String?) {
            activeContext.value = id
        }
    }
}
