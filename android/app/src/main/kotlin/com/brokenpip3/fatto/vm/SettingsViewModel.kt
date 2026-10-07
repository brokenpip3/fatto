package com.brokenpip3.fatto.vm

import android.util.Log
import androidx.lifecycle.ViewModel
import com.brokenpip3.fatto.BuildConfig
import com.brokenpip3.fatto.data.SettingsBackupCodec
import com.brokenpip3.fatto.data.SettingsBackupDocument
import com.brokenpip3.fatto.data.SettingsBackupService
import com.brokenpip3.fatto.data.SettingsRepository
import com.brokenpip3.fatto.data.SyncCredentials
import com.brokenpip3.fatto.data.SyncDiagnosticEvent
import com.brokenpip3.fatto.data.SyncDiagnosticsFormatter
import com.brokenpip3.fatto.data.SyncDiagnosticsRepository
import com.brokenpip3.fatto.data.SyncS3Field
import com.brokenpip3.fatto.data.SyncS3Validator
import com.brokenpip3.fatto.data.SyncServerField
import com.brokenpip3.fatto.data.SyncServerValidator
import com.brokenpip3.fatto.data.SyncType
import com.brokenpip3.fatto.data.TaskSwipeAction
import com.brokenpip3.fatto.data.TaskrcImportPreview
import com.brokenpip3.fatto.data.TaskrcImporter
import com.brokenpip3.fatto.data.model.TaskContext
import com.brokenpip3.fatto.ui.theme.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

sealed interface SyncTestState {
    data object NotTested : SyncTestState

    data object Testing : SyncTestState

    data class Succeeded(val timestampEpochMillis: Long, val elapsedMillis: Long) : SyncTestState

    data class Failed(val timestampEpochMillis: Long, val safeSummary: String) : SyncTestState

    data class SaveFailed(val timestampEpochMillis: Long, val safeSummary: String) : SyncTestState

    data object NeedsRetest : SyncTestState
}

@Suppress("LargeClass", "TooManyFunctions")
class SettingsViewModel(
    private val repository: SettingsRepository,
    private val syncAction: suspend () -> Unit = {},
    private val diagnosticsRepository: SyncDiagnosticsRepository? = null,
    settingsBackupService: SettingsBackupService? = null,
) : ViewModel() {
    private val settingsBackupService =
        settingsBackupService
            ?: SettingsBackupService(
                repository = repository,
                codec =
                    SettingsBackupCodec(
                        currentVersionCode = BuildConfig.VERSION_CODE,
                        currentVersionName = BuildConfig.VERSION_NAME,
                        clock = { Instant.now().toString() },
                    ),
            )
    private val _validationErrors = MutableStateFlow<Map<SyncServerField, String>>(emptyMap())
    val validationErrors = _validationErrors.asStateFlow()
    private val _s3ValidationErrors = MutableStateFlow<Map<SyncS3Field, String>>(emptyMap())
    val s3ValidationErrors = _s3ValidationErrors.asStateFlow()

    private val _syncTestState = MutableStateFlow<SyncTestState>(SyncTestState.NotTested)
    val syncTestState = _syncTestState.asStateFlow()
    val diagnosticEvents = diagnosticsRepository?.events ?: MutableStateFlow(emptyList())
    private var syncTestInProgress = false
    private val _syncType = MutableStateFlow(SyncType.SERVER)
    val syncType = _syncType.asStateFlow()

    private val _syncUrl = MutableStateFlow("")
    val syncUrl = _syncUrl.asStateFlow()

    private val _clientId = MutableStateFlow("")
    val clientId = _clientId.asStateFlow()

    private val _encryptionSecret = MutableStateFlow("")
    val encryptionSecret = _encryptionSecret.asStateFlow()
    private var serverEncryptionSecret = ""
    private var s3EncryptionSecret = ""

    private val _s3Bucket = MutableStateFlow("")
    val s3Bucket = _s3Bucket.asStateFlow()

    private val _s3Region = MutableStateFlow("")
    val s3Region = _s3Region.asStateFlow()

    private val _s3EndpointUrl = MutableStateFlow("")
    val s3EndpointUrl = _s3EndpointUrl.asStateFlow()

    private val _s3AccessKeyId = MutableStateFlow("")
    val s3AccessKeyId = _s3AccessKeyId.asStateFlow()

    private val _s3SecretAccessKey = MutableStateFlow("")
    val s3SecretAccessKey = _s3SecretAccessKey.asStateFlow()

    private val _showCompleted = MutableStateFlow(true)
    val showCompleted = _showCompleted.asStateFlow()

    private val _showInternalTags = MutableStateFlow(true)
    val showInternalTags = _showInternalTags.asStateFlow()

    private val _showEmptyProjects = MutableStateFlow(false)
    val showEmptyProjects = _showEmptyProjects.asStateFlow()

    private val _defaultProjectEnabled = MutableStateFlow(false)
    val defaultProjectEnabled = _defaultProjectEnabled.asStateFlow()

    private val _defaultProject = MutableStateFlow<String?>(null)
    val defaultProject = _defaultProject.asStateFlow()

    private val _tagsPerLine = MutableStateFlow(4)
    val tagsPerLine = _tagsPerLine.asStateFlow()

    private val _dailyNotificationsEnabled = MutableStateFlow(false)
    val dailyNotificationsEnabled = _dailyNotificationsEnabled.asStateFlow()

    private val _notificationHour = MutableStateFlow(9)
    val notificationHour = _notificationHour.asStateFlow()

    private val _includeDueToday = MutableStateFlow(true)
    val includeDueToday = _includeDueToday.asStateFlow()

    private val _includeScheduledToday = MutableStateFlow(true)
    val includeScheduledToday = _includeScheduledToday.asStateFlow()

    private val _includeOverdue = MutableStateFlow(false)
    val includeOverdue = _includeOverdue.asStateFlow()

    private val _firstDayOfWeek = MutableStateFlow(java.util.Calendar.MONDAY)
    val firstDayOfWeek = _firstDayOfWeek.asStateFlow()

    private val _confirmActions = MutableStateFlow(true)
    val confirmActions = _confirmActions.asStateFlow()

    private val _hideBlockedTasksWaiting = MutableStateFlow(false)
    val hideBlockedTasksWaiting = _hideBlockedTasksWaiting.asStateFlow()

    private val _showWaitingTasks = MutableStateFlow(true)
    val showWaitingTasks = _showWaitingTasks.asStateFlow()

    private val hookSettings = HookSettingsState(repository)
    val autoWaiting = hookSettings.autoWaiting
    val autoStopActiveOnComplete = hookSettings.autoStopActiveOnComplete

    private val _showPriorityBadge = MutableStateFlow(false)
    val showPriorityBadge = _showPriorityBadge.asStateFlow()

    private val _showUrgencyBar = MutableStateFlow(false)
    val showUrgencyBar = _showUrgencyBar.asStateFlow()

    private val _swipeStartToEndAction = MutableStateFlow(TaskSwipeAction.NONE)
    val swipeStartToEndAction = _swipeStartToEndAction.asStateFlow()

    private val _swipeEndToStartAction = MutableStateFlow(TaskSwipeAction.NONE)
    val swipeEndToStartAction = _swipeEndToStartAction.asStateFlow()

    val fontSizePercent = repository.fontSizePercent

    fun onFontSizePercentChange(value: Int) {
        repository.setFontSizePercent(value)
    }

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode = _themeMode.asStateFlow()

    val taskContexts = repository.taskContexts
    val activeTaskContextId = repository.activeTaskContextId

    private val _taskrcImportText = MutableStateFlow("")
    val taskrcImportText = _taskrcImportText.asStateFlow()

    private val _taskrcImportPreview = MutableStateFlow<TaskrcImportPreview?>(null)
    val taskrcImportPreview = _taskrcImportPreview.asStateFlow()

    init {
        load()
    }

    fun buildSettingsExportJson(): Result<String> = settingsBackupService.exportJson()

    fun validateSettingsImportJson(json: String): Result<SettingsBackupDocument> = settingsBackupService.parseImport(json)

    fun applySettingsImport(document: SettingsBackupDocument): Result<Unit> =
        settingsBackupService.applyImport(document).onSuccess { load() }

    private fun load() {
        _syncType.value = repository.getSyncType()

        val creds = repository.getCredentials()
        if (creds != null) {
            _syncUrl.value = creds.url
            _clientId.value = creds.clientId
            serverEncryptionSecret = creds.secret
            if (_syncType.value == SyncType.SERVER) {
                _encryptionSecret.value = serverEncryptionSecret
            }
            Log.d("SettingsViewModel", "Loaded credentials from repository")
        } else {
            Log.d("SettingsViewModel", "No credentials found in repository")
        }

        val s3Creds = repository.getS3Credentials()
        if (s3Creds != null) {
            _s3Bucket.value = s3Creds.bucket
            _s3Region.value = s3Creds.region ?: ""
            _s3EndpointUrl.value = s3Creds.endpointUrl ?: ""
            _s3AccessKeyId.value = s3Creds.accessKeyId
            _s3SecretAccessKey.value = s3Creds.secretAccessKey
            s3EncryptionSecret = s3Creds.secret
            if (_syncType.value == SyncType.S3) {
                _encryptionSecret.value = s3EncryptionSecret
            }
            Log.d("SettingsViewModel", "Loaded S3 credentials from repository")
        }
        _showCompleted.value = repository.getShowCompleted()
        _showInternalTags.value = repository.getShowInternalTags()
        _showEmptyProjects.value = repository.getShowEmptyProjects()
        _defaultProjectEnabled.value = repository.getDefaultProjectEnabled()
        _defaultProject.value = repository.getDefaultProject()
        _tagsPerLine.value = repository.getTagsPerLine()
        _dailyNotificationsEnabled.value = repository.getDailyNotificationsEnabled()
        _notificationHour.value = repository.getNotificationHour()
        _includeDueToday.value = repository.getIncludeDueToday()
        _includeScheduledToday.value = repository.getIncludeScheduledToday()
        _includeOverdue.value = repository.getIncludeOverdue()
        _firstDayOfWeek.value = repository.getFirstDayOfWeek()
        _confirmActions.value = repository.getConfirmActions()
        _hideBlockedTasksWaiting.value = repository.getHideBlockedTasksWaiting()
        _showWaitingTasks.value = repository.getShowWaitingTasks()
        _showPriorityBadge.value = repository.getShowPriorityBadge()
        _showUrgencyBar.value = repository.getShowUrgencyBar()
        _swipeStartToEndAction.value = repository.getSwipeStartToEndAction()
        _swipeEndToStartAction.value = repository.getSwipeEndToStartAction()
        _themeMode.value = repository.getThemeMode()
        _syncTestState.value = restoredSyncTestState()
    }

    fun onSyncTypeChange(value: SyncType) {
        cacheVisibleSecretForCurrentBackend()
        _syncType.value = value
        _encryptionSecret.value =
            when (value) {
                SyncType.SERVER -> serverEncryptionSecret
                SyncType.S3 -> s3EncryptionSecret
            }
        _syncTestState.value = restoredSyncTestState()
    }

    fun onUrlChange(value: String) {
        _syncUrl.value = value
        clearValidationError(SyncServerField.URL)
        markSyncSettingsEdited()
    }

    fun onS3BucketChange(value: String) {
        _s3Bucket.value = value
        clearS3ValidationError(SyncS3Field.BUCKET)
        markSyncSettingsEdited()
    }

    fun onS3RegionChange(value: String) {
        _s3Region.value = value
        clearS3ValidationError(SyncS3Field.REGION)
        markSyncSettingsEdited()
    }

    fun onS3EndpointUrlChange(value: String) {
        _s3EndpointUrl.value = value
        clearS3ValidationError(SyncS3Field.ENDPOINT_URL)
        markSyncSettingsEdited()
    }

    fun onS3AccessKeyIdChange(value: String) {
        _s3AccessKeyId.value = value
        clearS3ValidationError(SyncS3Field.ACCESS_KEY_ID)
        markSyncSettingsEdited()
    }

    fun onS3SecretAccessKeyChange(value: String) {
        _s3SecretAccessKey.value = value
        clearS3ValidationError(SyncS3Field.SECRET_ACCESS_KEY)
        markSyncSettingsEdited()
    }

    fun onClientIdChange(value: String) {
        _clientId.value = value
        clearValidationError(SyncServerField.CLIENT_ID)
        markSyncSettingsEdited()
    }

    fun onSecretChange(value: String) {
        _encryptionSecret.value = value
        clearValidationError(SyncServerField.ENCRYPTION_SECRET)
        cacheVisibleSecretForCurrentBackend()
        markSyncSettingsEdited()
    }

    private fun cacheVisibleSecretForCurrentBackend() {
        when (_syncType.value) {
            SyncType.SERVER -> serverEncryptionSecret = _encryptionSecret.value
            SyncType.S3 -> s3EncryptionSecret = _encryptionSecret.value
        }
    }

    fun onDailyNotificationsChange(value: Boolean) {
        _dailyNotificationsEnabled.value = value
        repository.setDailyNotificationsEnabled(value)
    }

    fun onNotificationHourChange(value: Int) {
        _notificationHour.value = value
        repository.setNotificationHour(value)
    }

    fun onIncludeDueTodayChange(value: Boolean) {
        _includeDueToday.value = value
        repository.setIncludeDueToday(value)
    }

    fun onIncludeScheduledTodayChange(value: Boolean) {
        _includeScheduledToday.value = value
        repository.setIncludeScheduledToday(value)
    }

    fun onIncludeOverdueChange(value: Boolean) {
        _includeOverdue.value = value
        repository.setIncludeOverdue(value)
    }

    fun onShowCompletedChange(value: Boolean) {
        _showCompleted.value = value
        repository.setShowCompleted(value)
    }

    fun onShowInternalTagsChange(value: Boolean) {
        _showInternalTags.value = value
        repository.setShowInternalTags(value)
    }

    fun onShowEmptyProjectsChange(value: Boolean) {
        _showEmptyProjects.value = value
        repository.setShowEmptyProjects(value)
    }

    fun onDefaultProjectEnabledChange(enabled: Boolean) {
        repository.setDefaultProjectEnabled(enabled)
        _defaultProjectEnabled.value = repository.getDefaultProjectEnabled()
    }

    fun onDefaultProjectSelected(project: String) {
        val normalized = project.trim()
        if (normalized.isEmpty()) return

        repository.setDefaultProject(normalized)
        repository.setDefaultProjectEnabled(true)
        _defaultProject.value = normalized
        _defaultProjectEnabled.value = true
    }

    fun onTagsPerLineChange(value: Int) {
        _tagsPerLine.value = value
        repository.setTagsPerLine(value)
    }

    fun onFirstDayOfWeekChange(value: Int) {
        _firstDayOfWeek.value = value
        repository.setFirstDayOfWeek(value)
    }

    fun onConfirmActionsChange(value: Boolean) {
        _confirmActions.value = value
        repository.setConfirmActions(value)
    }

    fun onHideBlockedTasksWaitingChange(value: Boolean) {
        _hideBlockedTasksWaiting.value = value
        repository.setHideBlockedTasksWaiting(value)
    }

    fun onShowWaitingTasksChange(value: Boolean) {
        _showWaitingTasks.value = value
        repository.setShowWaitingTasks(value)
    }

    fun onAutoWaitingChange(value: Boolean) {
        hookSettings.onAutoWaitingChange(value)
    }

    fun onAutoStopActiveOnCompleteChange(enabled: Boolean) {
        hookSettings.onAutoStopActiveOnCompleteChange(enabled)
    }

    fun onShowPriorityBadgeChange(value: Boolean) {
        _showPriorityBadge.value = value
        repository.setShowPriorityBadge(value)
    }

    fun onShowUrgencyBarChange(value: Boolean) {
        _showUrgencyBar.value = value
        repository.setShowUrgencyBar(value)
    }

    fun onSwipeStartToEndActionChange(value: TaskSwipeAction) {
        _swipeStartToEndAction.value = value
        repository.setSwipeStartToEndAction(value)
    }

    fun onSwipeEndToStartActionChange(value: TaskSwipeAction) {
        _swipeEndToStartAction.value = value
        repository.setSwipeEndToStartAction(value)
    }

    fun onThemeModeChange(value: ThemeMode) {
        _themeMode.value = value
        repository.setThemeMode(value)
    }

    fun saveTaskContext(context: TaskContext) {
        repository.saveTaskContext(context)
    }

    fun deleteTaskContext(id: String) {
        repository.deleteTaskContext(id)
    }

    fun setActiveTaskContext(id: String?) {
        repository.setActiveTaskContextId(id)
    }

    fun onTaskrcImportTextChange(value: String) {
        _taskrcImportText.value = value
        _taskrcImportPreview.value = null
    }

    fun previewTaskrcImport() {
        _taskrcImportPreview.value =
            TaskrcImporter.preview(
                text = _taskrcImportText.value,
                existingContexts = repository.getTaskContexts(),
                currentActiveContextId = repository.getActiveTaskContextId(),
                currentFirstDayOfWeek = repository.getFirstDayOfWeek(),
                currentDefaultProjectEnabled = repository.getDefaultProjectEnabled(),
                currentDefaultProject = repository.getDefaultProject(),
                currentSyncCredentials = repository.getCredentials(),
                currentS3Credentials = repository.getS3Credentials(),
                currentSyncType = repository.getSyncType(),
            )
    }

    fun applyTaskrcImport() {
        val preview = _taskrcImportPreview.value ?: return
        repository.applyTaskrcImport(preview)
        _defaultProject.value = repository.getDefaultProject()
        _defaultProjectEnabled.value = repository.getDefaultProjectEnabled()
        preview.serverCredentialsAfter?.let { creds ->
            _syncUrl.value = creds.url
            _clientId.value = creds.clientId
            serverEncryptionSecret = creds.secret
        }
        preview.s3CredentialsAfter?.let { creds ->
            _s3Bucket.value = creds.bucket
            _s3Region.value = creds.region ?: ""
            _s3EndpointUrl.value = creds.endpointUrl ?: ""
            _s3AccessKeyId.value = creds.accessKeyId
            _s3SecretAccessKey.value = creds.secretAccessKey
            s3EncryptionSecret = creds.secret
        }
        preview.encryptionSecretAfter?.let { secret ->
            serverEncryptionSecret = secret
            s3EncryptionSecret = secret
        }
        _syncType.value = preview.syncTypeAfter
        _encryptionSecret.value =
            when (_syncType.value) {
                SyncType.SERVER -> serverEncryptionSecret
                SyncType.S3 -> s3EncryptionSecret
            }
        _taskrcImportPreview.value = preview
    }

    /** Save the current Sync Server settings, then run a real sync with the active replica. */
    @Suppress("ReturnCount")
    suspend fun saveAndTest(): Boolean {
        if (syncTestInProgress) return false
        syncTestInProgress = true
        try {
            val backend = _syncType.value
            val prepared =
                when (backend) {
                    SyncType.SERVER -> saveServerCredentials()
                    SyncType.S3 -> prepareS3Settings()
                }
            if (!prepared) return false

            _syncTestState.value = SyncTestState.Testing
            val startedAt = System.nanoTime()
            recordDiagnostic("sync_test", "started", "Sync test started")
            return try {
                syncAction()
                val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L
                val timestamp = System.currentTimeMillis()
                recordDiagnostic("sync_test", "success", "Sync completed successfully", elapsedMillis)
                _syncTestState.value = SyncTestState.Succeeded(timestamp, elapsedMillis)
                true
            } catch (e: CancellationException) {
                val timestamp = System.currentTimeMillis()
                recordDiagnostic("sync_test", "cancelled", "Sync test was cancelled")
                _syncTestState.value = SyncTestState.Failed(timestamp, "Sync test was cancelled")
                throw e
            } catch (e: Exception) {
                val safeSummary =
                    SyncDiagnosticsFormatter.safeError(
                        error = e,
                        sensitiveValues = sensitiveValuesForCurrentBackend(),
                    )
                val timestamp = System.currentTimeMillis()
                recordDiagnostic("sync_test", "failed", safeSummary)
                _syncTestState.value = SyncTestState.Failed(timestamp, safeSummary)
                false
            }
        } finally {
            syncTestInProgress = false
        }
    }

    @Suppress("ReturnCount")
    private fun prepareS3Settings(): Boolean {
        val errors =
            SyncS3Validator.validate(
                bucket = _s3Bucket.value,
                region = _s3Region.value,
                endpointUrl = _s3EndpointUrl.value,
                accessKeyId = _s3AccessKeyId.value,
                secretAccessKey = _s3SecretAccessKey.value,
                encryptionSecret = _encryptionSecret.value,
            )
        _s3ValidationErrors.value = errors
        if (errors.isNotEmpty()) {
            recordDiagnostic("validation", "failed", "S3 settings validation failed")
            return false
        }
        recordDiagnostic("validation", "success", "S3 settings validated")

        val bucket = _s3Bucket.value.trim()
        val region = _s3Region.value.trim().ifEmpty { null }
        val endpointUrl = _s3EndpointUrl.value.trim().ifEmpty { null }
        val accessKeyId = _s3AccessKeyId.value.trim()
        val secretAccessKey = _s3SecretAccessKey.value.trim()
        val secret = _encryptionSecret.value.trim()
        if (!repository.saveS3Credentials(bucket, region, endpointUrl, accessKeyId, secretAccessKey, secret)) {
            recordDiagnostic("s3_config", "save_failed", "Unable to save S3 settings")
            _syncTestState.value = SyncTestState.SaveFailed(System.currentTimeMillis(), "Unable to save S3 settings")
            return false
        }
        if (!repository.setSyncType(SyncType.S3)) {
            recordDiagnostic("s3_config", "save_failed", "Unable to save active sync backend")
            _syncTestState.value = SyncTestState.SaveFailed(System.currentTimeMillis(), "Unable to save S3 settings")
            return false
        }
        _s3Bucket.value = bucket
        _s3Region.value = region.orEmpty()
        _s3EndpointUrl.value = endpointUrl.orEmpty()
        _s3AccessKeyId.value = accessKeyId
        _s3SecretAccessKey.value = secretAccessKey
        _encryptionSecret.value = secret
        s3EncryptionSecret = secret
        _syncType.value = SyncType.S3
        recordDiagnostic("s3_config", "saved", "S3 settings saved")
        return true
    }

    @Suppress("ReturnCount")
    private fun saveServerCredentials(): Boolean {
        val errors = SyncServerValidator.validate(_syncUrl.value, _clientId.value, _encryptionSecret.value)
        _validationErrors.value = errors
        if (errors.isNotEmpty()) {
            recordDiagnostic("validation", "failed", "Sync Server settings validation failed")
            return false
        }
        recordDiagnostic("validation", "success", "Sync Server settings validated")

        val url = _syncUrl.value.trim()
        val clientId = _clientId.value.trim()
        val secret = _encryptionSecret.value.trim()
        val previousCredentials = repository.getCredentials()
        val configurationUnchanged =
            repository.getSyncType() == SyncType.SERVER &&
                previousCredentials == SyncCredentials(url, clientId, secret)
        if (!repository.saveCredentials(url, clientId, secret)) {
            recordDiagnostic("server_config", "save_failed", "Unable to save Sync Server credentials")
            _syncTestState.value = SyncTestState.SaveFailed(System.currentTimeMillis(), "Unable to save Sync Server settings")
            return false
        }
        if (!repository.setSyncType(SyncType.SERVER)) {
            recordDiagnostic("server_config", "save_failed", "Unable to save active sync backend")
            _syncTestState.value = SyncTestState.SaveFailed(System.currentTimeMillis(), "Unable to save Sync Server settings")
            return false
        }

        _syncUrl.value = url
        _clientId.value = clientId
        _encryptionSecret.value = secret
        serverEncryptionSecret = secret
        _syncType.value = SyncType.SERVER
        if (_syncTestState.value is SyncTestState.SaveFailed) {
            _syncTestState.value = if (hasPriorSyncAttempt()) SyncTestState.NeedsRetest else SyncTestState.NotTested
        }
        val saveOutcome = if (configurationUnchanged) "saved_unchanged" else "saved"
        recordDiagnostic("server_config", saveOutcome, "Sync Server settings saved")
        return true
    }

    private fun markSyncSettingsEdited() {
        if (!syncTestInProgress && _syncTestState.value !is SyncTestState.NotTested) {
            _syncTestState.value = SyncTestState.NeedsRetest
        }
    }

    private fun clearValidationError(field: SyncServerField) {
        if (field in _validationErrors.value) {
            _validationErrors.value = _validationErrors.value - field
        }
    }

    private fun clearS3ValidationError(field: SyncS3Field) {
        if (field in _s3ValidationErrors.value) {
            _s3ValidationErrors.value = _s3ValidationErrors.value - field
        }
    }

    fun clearDiagnostics() {
        diagnosticsRepository?.clear()
    }

    private fun sensitiveValuesForCurrentBackend(): Set<String> =
        when (_syncType.value) {
            SyncType.SERVER -> setOf(_syncUrl.value, _clientId.value, _encryptionSecret.value)
            SyncType.S3 ->
                setOf(
                    _s3Bucket.value,
                    _s3EndpointUrl.value,
                    _s3AccessKeyId.value,
                    _s3SecretAccessKey.value,
                    _encryptionSecret.value,
                )
        }

    private fun recordDiagnostic(
        stage: String,
        outcome: String,
        summary: String,
        elapsedMillis: Long? = null,
    ) {
        val event =
            SyncDiagnosticEvent(
                timestampEpochMillis = System.currentTimeMillis(),
                stage = stage,
                outcome = outcome,
                summary = summary,
                serverOrigin =
                    when (_syncType.value) {
                        SyncType.SERVER -> SyncDiagnosticsFormatter.endpointOrigin(_syncUrl.value)
                        SyncType.S3 -> _s3EndpointUrl.value.takeIf { it.isNotBlank() }?.let(SyncDiagnosticsFormatter::endpointOrigin)
                    },
                elapsedMillis = elapsedMillis,
                appVersion = BuildConfig.VERSION_NAME,
                backend = _syncType.value,
            )
        diagnosticsRepository?.append(SyncDiagnosticsFormatter.sanitizeEvent(event))
    }

    private fun restoredSyncTestState(): SyncTestState = restoreSyncTestState(diagnosticsRepository?.events?.value.orEmpty())

    @Suppress("ReturnCount")
    private fun restoreSyncTestState(events: List<SyncDiagnosticEvent>): SyncTestState {
        val latestIndex =
            events.indexOfLast { event ->
                event.backend == _syncType.value &&
                    (
                        (event.stage == "sync_test" && event.outcome in SYNC_ATTEMPT_OUTCOMES) ||
                            (event.stage in CONFIG_STAGES && event.outcome in SERVER_CONFIG_OUTCOMES)
                    )
            }
        if (latestIndex < 0) return SyncTestState.NotTested
        val latest = events[latestIndex]
        if (latest.stage in CONFIG_STAGES) {
            return when (latest.outcome) {
                "cleared" -> SyncTestState.NotTested
                "save_failed" -> SyncTestState.SaveFailed(latest.timestampEpochMillis, latest.summary)
                "saved" ->
                    if (
                        events.take(latestIndex).any {
                            it.backend == _syncType.value &&
                                it.stage == "sync_test" &&
                                it.outcome in SYNC_ATTEMPT_OUTCOMES
                        }
                    ) {
                        SyncTestState.NeedsRetest
                    } else {
                        SyncTestState.NotTested
                    }
                "saved_unchanged" -> restoreSyncTestState(events.take(latestIndex))
                else -> SyncTestState.NotTested
            }
        }
        return stateFromSyncEvent(latest)
    }

    private fun stateFromSyncEvent(event: SyncDiagnosticEvent): SyncTestState =
        when (event.outcome) {
            "success" -> SyncTestState.Succeeded(event.timestampEpochMillis, event.elapsedMillis ?: 0L)
            "failed", "cancelled" -> SyncTestState.Failed(event.timestampEpochMillis, event.summary)
            else -> SyncTestState.Failed(event.timestampEpochMillis, "Previous sync test did not complete")
        }

    private fun hasPriorSyncAttempt(): Boolean =
        diagnosticsRepository?.events?.value?.any {
            it.backend == _syncType.value &&
                it.stage == "sync_test" &&
                it.outcome in SYNC_ATTEMPT_OUTCOMES
        } == true

    fun clear() {
        if (syncTestInProgress) return
        Log.d("SettingsViewModel", "Clearing settings")
        repository.clearCredentials()
        _syncType.value = SyncType.SERVER
        _syncUrl.value = ""
        _clientId.value = ""
        _encryptionSecret.value = ""
        serverEncryptionSecret = ""
        s3EncryptionSecret = ""
        _s3Bucket.value = ""
        _s3Region.value = ""
        _s3EndpointUrl.value = ""
        _s3AccessKeyId.value = ""
        _s3SecretAccessKey.value = ""
        _validationErrors.value = emptyMap()
        _s3ValidationErrors.value = emptyMap()
        _syncTestState.value = SyncTestState.NotTested
        recordDiagnostic("server_config", "cleared", "Sync Server settings cleared")
        _showCompleted.value = true
        repository.setShowCompleted(true)
        _confirmActions.value = true
        repository.setConfirmActions(true)
        hookSettings.resetToDefaults()
        _showPriorityBadge.value = false
        repository.setShowPriorityBadge(false)
        _showUrgencyBar.value = false
        repository.setShowUrgencyBar(false)
    }

    private companion object {
        val SYNC_ATTEMPT_OUTCOMES = setOf("started", "success", "failed", "cancelled")
        val SERVER_CONFIG_OUTCOMES = setOf("saved", "saved_unchanged", "save_failed", "cleared")
        val CONFIG_STAGES = setOf("server_config", "s3_config")
    }
}
