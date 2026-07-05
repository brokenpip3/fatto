package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.SettingsRepository
import com.brokenpip3.fatto.data.SyncCredentials
import com.brokenpip3.fatto.data.TaskrcImportPreview
import com.brokenpip3.fatto.data.TaskrcImportResultType
import com.brokenpip3.fatto.data.model.TaskContext
import com.brokenpip3.fatto.ui.theme.ThemeMode
import com.brokenpip3.fatto.vm.SettingsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class SettingsViewModelTest {
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

        override fun getSortOrder(): String = sortOrder.value

        override fun setSortOrder(value: String) {
            sortOrder.value = value
        }

        override fun getSortDirection(): String = sortDirection.value

        override fun setSortDirection(value: String) {
            sortDirection.value = value
        }

        override fun getCredentials(): SyncCredentials? = null

        override fun saveCredentials(
            url: String,
            clientId: String,
            secret: String,
        ) = Unit

        override fun clearCredentials() = Unit

        override fun hasCredentials(): Boolean = false

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
