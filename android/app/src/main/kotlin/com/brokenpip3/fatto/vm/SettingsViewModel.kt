package com.brokenpip3.fatto.vm

import android.util.Log
import androidx.lifecycle.ViewModel
import com.brokenpip3.fatto.data.SettingsRepository
import com.brokenpip3.fatto.data.SyncType
import com.brokenpip3.fatto.data.TaskrcImportPreview
import com.brokenpip3.fatto.data.TaskrcImporter
import com.brokenpip3.fatto.data.model.TaskContext
import com.brokenpip3.fatto.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
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

    private val _autoWaiting = MutableStateFlow(false)
    val autoWaiting = _autoWaiting.asStateFlow()

    private val _showPriorityBadge = MutableStateFlow(false)
    val showPriorityBadge = _showPriorityBadge.asStateFlow()

    private val _showUrgencyBar = MutableStateFlow(false)
    val showUrgencyBar = _showUrgencyBar.asStateFlow()

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
        _autoWaiting.value = repository.getAutoWaiting()
        _showPriorityBadge.value = repository.getShowPriorityBadge()
        _showUrgencyBar.value = repository.getShowUrgencyBar()
        _themeMode.value = repository.getThemeMode()
    }

    fun onSyncTypeChange(value: SyncType) {
        cacheVisibleSecretForCurrentBackend()
        _syncType.value = value
        _encryptionSecret.value =
            when (value) {
                SyncType.SERVER -> serverEncryptionSecret
                SyncType.S3 -> s3EncryptionSecret
            }
    }

    fun onUrlChange(value: String) {
        _syncUrl.value = value
    }

    fun onS3BucketChange(value: String) {
        _s3Bucket.value = value
    }

    fun onS3RegionChange(value: String) {
        _s3Region.value = value
    }

    fun onS3EndpointUrlChange(value: String) {
        _s3EndpointUrl.value = value
    }

    fun onS3AccessKeyIdChange(value: String) {
        _s3AccessKeyId.value = value
    }

    fun onS3SecretAccessKeyChange(value: String) {
        _s3SecretAccessKey.value = value
    }

    fun onClientIdChange(value: String) {
        _clientId.value = value
    }

    fun onSecretChange(value: String) {
        _encryptionSecret.value = value
        cacheVisibleSecretForCurrentBackend()
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
        _autoWaiting.value = value
        repository.setAutoWaiting(value)
    }

    fun onShowPriorityBadgeChange(value: Boolean) {
        _showPriorityBadge.value = value
        repository.setShowPriorityBadge(value)
    }

    fun onShowUrgencyBarChange(value: Boolean) {
        _showUrgencyBar.value = value
        repository.setShowUrgencyBar(value)
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
                currentSyncCredentials = repository.getCredentials(),
                currentS3Credentials = repository.getS3Credentials(),
                currentSyncType = repository.getSyncType(),
            )
    }

    fun applyTaskrcImport() {
        val preview = _taskrcImportPreview.value ?: return
        repository.applyTaskrcImport(preview)
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

    /**
     * Persist the sync configuration. The active backend (sync type) is only
     * persisted together with a complete set of credentials, so an incomplete
     * form can never switch the app to a backend that has no usable credentials.
     *
     * @return true if the settings were saved, false if required fields are missing.
     */
    fun save(): Boolean {
        val type = _syncType.value
        val secret = _encryptionSecret.value.trim()

        val saved =
            when (type) {
                SyncType.S3 -> saveS3Credentials(secret)
                SyncType.SERVER -> saveServerCredentials(secret)
            }
        if (saved) {
            repository.setSyncType(type)
        }
        return saved
    }

    private fun saveS3Credentials(secret: String): Boolean {
        val bucket = _s3Bucket.value.trim()
        val accessKeyId = _s3AccessKeyId.value.trim()
        val secretAccessKey = _s3SecretAccessKey.value.trim()
        if (bucket.isEmpty() || accessKeyId.isEmpty() || secretAccessKey.isEmpty() || secret.isEmpty()) {
            Log.d("SettingsViewModel", "Not saving: incomplete S3 credentials")
            return false
        }
        Log.d("SettingsViewModel", "Saving S3 settings to repository")
        repository.saveS3Credentials(
            bucket = bucket,
            region = _s3Region.value.trim().ifEmpty { null },
            endpointUrl = _s3EndpointUrl.value.trim().ifEmpty { null },
            accessKeyId = accessKeyId,
            secretAccessKey = secretAccessKey,
            secret = secret,
        )
        return true
    }

    private fun saveServerCredentials(secret: String): Boolean {
        val url = _syncUrl.value.trim()
        val clientId = _clientId.value.trim()
        if (url.isEmpty() || clientId.isEmpty() || secret.isEmpty()) {
            Log.d("SettingsViewModel", "Not saving: incomplete server credentials")
            return false
        }
        Log.d("SettingsViewModel", "Saving server settings to repository")
        repository.saveCredentials(
            url = url,
            clientId = clientId,
            secret = secret,
        )
        return true
    }

    fun clear() {
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
        _showCompleted.value = true
        repository.setShowCompleted(true)
        _confirmActions.value = true
        repository.setConfirmActions(true)
        _autoWaiting.value = false
        repository.setAutoWaiting(false)
        _showPriorityBadge.value = false
        repository.setShowPriorityBadge(false)
        _showUrgencyBar.value = false
        repository.setShowUrgencyBar(false)
    }
}
