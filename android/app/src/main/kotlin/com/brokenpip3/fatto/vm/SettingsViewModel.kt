package com.brokenpip3.fatto.vm

import android.util.Log
import androidx.lifecycle.ViewModel
import com.brokenpip3.fatto.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    private val _syncUrl = MutableStateFlow("")
    val syncUrl = _syncUrl.asStateFlow()

    private val _clientId = MutableStateFlow("")
    val clientId = _clientId.asStateFlow()

    private val _encryptionSecret = MutableStateFlow("")
    val encryptionSecret = _encryptionSecret.asStateFlow()

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

    init {
        load()
    }

    private fun load() {
        val creds = repository.getCredentials()
        if (creds != null) {
            _syncUrl.value = creds.url
            _clientId.value = creds.clientId
            _encryptionSecret.value = creds.secret
            Log.d("SettingsViewModel", "Loaded credentials from repository")
        } else {
            Log.d("SettingsViewModel", "No credentials found in repository")
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
    }

    fun onUrlChange(value: String) {
        _syncUrl.value = value
    }

    fun onClientIdChange(value: String) {
        _clientId.value = value
    }

    fun onSecretChange(value: String) {
        _encryptionSecret.value = value
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

    fun save() {
        val url = _syncUrl.value.trim()
        val clientIdValue = _clientId.value.trim()
        val secret = _encryptionSecret.value.trim()

        Log.d("SettingsViewModel", "Saving settings to repository")
        repository.saveCredentials(url, clientIdValue, secret)
    }

    fun clear() {
        Log.d("SettingsViewModel", "Clearing settings")
        repository.clearCredentials()
        _syncUrl.value = ""
        _clientId.value = ""
        _encryptionSecret.value = ""
        _showCompleted.value = true
        repository.setShowCompleted(true)
        _confirmActions.value = true
        repository.setConfirmActions(true)
    }
}
