package com.brokenpip3.fatto.vm

import com.brokenpip3.fatto.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class HookSettingsState(
    private val repository: SettingsRepository,
) {
    private val _autoWaiting = MutableStateFlow(repository.getAutoWaiting())
    val autoWaiting = _autoWaiting.asStateFlow()

    private val _autoStopActiveOnComplete = MutableStateFlow(repository.getAutoStopActiveOnComplete())
    val autoStopActiveOnComplete = _autoStopActiveOnComplete.asStateFlow()

    fun onAutoWaitingChange(enabled: Boolean) {
        _autoWaiting.value = enabled
        repository.setAutoWaiting(enabled)
    }

    fun onAutoStopActiveOnCompleteChange(enabled: Boolean) {
        _autoStopActiveOnComplete.value = enabled
        repository.setAutoStopActiveOnComplete(enabled)
    }

    fun resetToDefaults() {
        onAutoWaitingChange(false)
        onAutoStopActiveOnCompleteChange(true)
    }
}
