package com.brokenpip3.fatto.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.brokenpip3.fatto.data.model.TaskContext
import com.brokenpip3.fatto.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import java.util.Locale

data class SyncCredentials(
    val url: String,
    val clientId: String,
    val secret: String,
)

data class S3Credentials(
    val bucket: String,
    val region: String?,
    val endpointUrl: String?,
    val accessKeyId: String,
    val secretAccessKey: String,
    val secret: String,
)

/** Selected sync backend. Stored as a string under the `sync_type` preference key. */
enum class SyncType(val value: String) {
    SERVER("server"),
    S3("s3"),
    ;

    companion object {
        fun fromValue(value: String?): SyncType = entries.firstOrNull { it.value == value } ?: SERVER
    }
}

@Suppress("TooManyFunctions")
interface SettingsRepository {
    val showCompleted: StateFlow<Boolean>
    val showInternalTags: StateFlow<Boolean>
    val showEmptyProjects: StateFlow<Boolean>
    val tagsPerLine: StateFlow<Int>
    val dailyNotificationsEnabled: StateFlow<Boolean>
    val notificationHour: StateFlow<Int>
    val includeDueToday: StateFlow<Boolean>
    val includeScheduledToday: StateFlow<Boolean>
    val includeOverdue: StateFlow<Boolean>
    val firstDayOfWeek: StateFlow<Int>
    val confirmActions: StateFlow<Boolean>
    val hideBlockedTasksWaiting: StateFlow<Boolean>
    val showWaitingTasks: StateFlow<Boolean>
    val autoWaiting: StateFlow<Boolean>
    val sortOrder: StateFlow<String>
    val sortDirection: StateFlow<String>
    val showPriorityBadge: StateFlow<Boolean>
    val showUrgencyBar: StateFlow<Boolean>
    val themeMode: StateFlow<ThemeMode>
    val taskContexts: StateFlow<List<TaskContext>>
    val activeTaskContextId: StateFlow<String?>

    fun getFirstDayOfWeek(): Int

    fun setFirstDayOfWeek(value: Int)

    fun getConfirmActions(): Boolean

    fun setConfirmActions(enabled: Boolean)

    fun getHideBlockedTasksWaiting(): Boolean

    fun setHideBlockedTasksWaiting(value: Boolean)

    fun getShowWaitingTasks(): Boolean

    fun setShowWaitingTasks(value: Boolean)

    fun getAutoWaiting(): Boolean

    fun setAutoWaiting(value: Boolean)

    fun getSortOrder(): String

    fun setSortOrder(value: String)

    fun getSortDirection(): String

    fun setSortDirection(value: String)

    fun getSyncType(): SyncType

    fun setSyncType(type: SyncType)

    fun getCredentials(): SyncCredentials?

    fun saveCredentials(
        url: String,
        clientId: String,
        secret: String,
    )

    fun getS3Credentials(): S3Credentials?

    fun saveS3Credentials(
        bucket: String,
        region: String?,
        endpointUrl: String?,
        accessKeyId: String,
        secretAccessKey: String,
        secret: String,
    )

    fun clearCredentials()

    fun hasCredentials(): Boolean

    fun getShowCompleted(): Boolean

    fun setShowCompleted(show: Boolean)

    fun getShowInternalTags(): Boolean

    fun setShowInternalTags(show: Boolean)

    fun getShowEmptyProjects(): Boolean

    fun setShowEmptyProjects(show: Boolean)

    fun getTagsPerLine(): Int

    fun setTagsPerLine(count: Int)

    fun getDailyNotificationsEnabled(): Boolean

    fun setDailyNotificationsEnabled(enabled: Boolean)

    fun getNotificationHour(): Int

    fun setNotificationHour(hour: Int)

    fun getIncludeDueToday(): Boolean

    fun setIncludeDueToday(enabled: Boolean)

    fun getIncludeScheduledToday(): Boolean

    fun setIncludeScheduledToday(enabled: Boolean)

    fun getIncludeOverdue(): Boolean

    fun setIncludeOverdue(enabled: Boolean)

    fun getShowPriorityBadge(): Boolean

    fun setShowPriorityBadge(enabled: Boolean)

    fun getShowUrgencyBar(): Boolean

    fun setShowUrgencyBar(enabled: Boolean)

    fun getThemeMode(): ThemeMode

    fun setThemeMode(value: ThemeMode)

    fun getTaskContexts(): List<TaskContext>

    fun saveTaskContext(context: TaskContext)

    fun replaceTaskContexts(contexts: List<TaskContext>)

    fun applyTaskrcImport(preview: TaskrcImportPreview)

    fun deleteTaskContext(id: String)

    fun getActiveTaskContextId(): String?

    fun setActiveTaskContextId(id: String?)
}

@Suppress("TooManyFunctions")
class SettingsRepositoryImpl(context: Context) : SettingsRepository {
    @Suppress("DEPRECATION")
    private val sharedPreferences =
        try {
            val masterKey =
                MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

            EncryptedSharedPreferences.create(
                context,
                "sync_settings",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            Log.e("SettingsRepository", "Failed to initialize EncryptedSharedPreferences", e)
            null
        }

    private val themeModeSettings = ThemeModeSettings(sharedPreferences)

    private val _showCompleted = MutableStateFlow(getShowCompleted())
    override val showCompleted: StateFlow<Boolean> = _showCompleted.asStateFlow()

    private val _showInternalTags = MutableStateFlow(getShowInternalTags())
    override val showInternalTags: StateFlow<Boolean> = _showInternalTags.asStateFlow()

    private val _showEmptyProjects = MutableStateFlow(getShowEmptyProjects())
    override val showEmptyProjects: StateFlow<Boolean> = _showEmptyProjects.asStateFlow()

    private val _tagsPerLine = MutableStateFlow(getTagsPerLine())
    override val tagsPerLine: StateFlow<Int> = _tagsPerLine.asStateFlow()

    private val _dailyNotificationsEnabled = MutableStateFlow(getDailyNotificationsEnabled())
    override val dailyNotificationsEnabled: StateFlow<Boolean> = _dailyNotificationsEnabled.asStateFlow()

    private val _notificationHour = MutableStateFlow(getNotificationHour())
    override val notificationHour: StateFlow<Int> = _notificationHour.asStateFlow()

    private val _includeDueToday = MutableStateFlow(getIncludeDueToday())
    override val includeDueToday: StateFlow<Boolean> = _includeDueToday.asStateFlow()

    private val _includeScheduledToday = MutableStateFlow(getIncludeScheduledToday())
    override val includeScheduledToday: StateFlow<Boolean> = _includeScheduledToday.asStateFlow()

    private val _includeOverdue = MutableStateFlow(getIncludeOverdue())
    override val includeOverdue: StateFlow<Boolean> = _includeOverdue.asStateFlow()

    private val _firstDayOfWeek = MutableStateFlow(getFirstDayOfWeek())
    override val firstDayOfWeek: StateFlow<Int> = _firstDayOfWeek.asStateFlow()

    private val _confirmActions = MutableStateFlow(getConfirmActions())
    override val confirmActions: StateFlow<Boolean> = _confirmActions.asStateFlow()

    private val _hideBlockedTasksWaiting = MutableStateFlow(getHideBlockedTasksWaiting())
    override val hideBlockedTasksWaiting: StateFlow<Boolean> = _hideBlockedTasksWaiting.asStateFlow()

    private val _showWaitingTasks = MutableStateFlow(getShowWaitingTasks())
    override val showWaitingTasks: StateFlow<Boolean> = _showWaitingTasks.asStateFlow()

    private val _autoWaiting = MutableStateFlow(getAutoWaiting())
    override val autoWaiting: StateFlow<Boolean> = _autoWaiting.asStateFlow()

    private val _sortOrder = MutableStateFlow(getSortOrder())
    override val sortOrder: StateFlow<String> = _sortOrder.asStateFlow()

    private val _sortDirection = MutableStateFlow(getSortDirection())
    override val sortDirection: StateFlow<String> = _sortDirection.asStateFlow()

    private val _showPriorityBadge = MutableStateFlow(getShowPriorityBadge())
    override val showPriorityBadge: StateFlow<Boolean> = _showPriorityBadge.asStateFlow()

    private val _showUrgencyBar = MutableStateFlow(getShowUrgencyBar())
    override val showUrgencyBar: StateFlow<Boolean> = _showUrgencyBar.asStateFlow()

    private val _themeMode = MutableStateFlow(getThemeMode())
    override val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _taskContexts = MutableStateFlow(getTaskContexts())
    override val taskContexts: StateFlow<List<TaskContext>> = _taskContexts.asStateFlow()

    private val _activeTaskContextId = MutableStateFlow(getActiveTaskContextId())
    override val activeTaskContextId: StateFlow<String?> = _activeTaskContextId.asStateFlow()

    override fun getFirstDayOfWeek(): Int {
        return sharedPreferences?.getInt("first_day_of_week", Calendar.MONDAY) ?: Calendar.MONDAY
    }

    override fun setFirstDayOfWeek(value: Int) {
        sharedPreferences?.edit()?.putInt("first_day_of_week", value)?.apply()
        _firstDayOfWeek.value = value
    }

    override fun getConfirmActions(): Boolean {
        return sharedPreferences?.getBoolean("confirm_actions", true) ?: true
    }

    override fun setConfirmActions(enabled: Boolean) {
        sharedPreferences?.edit()?.putBoolean("confirm_actions", enabled)?.apply()
        _confirmActions.value = enabled
    }

    override fun getHideBlockedTasksWaiting(): Boolean {
        return sharedPreferences?.getBoolean("hide_blocked_tasks_waiting", false) ?: false
    }

    override fun setHideBlockedTasksWaiting(value: Boolean) {
        sharedPreferences?.edit()?.putBoolean("hide_blocked_tasks_waiting", value)?.apply()
        _hideBlockedTasksWaiting.value = value
    }

    override fun getShowWaitingTasks(): Boolean {
        return sharedPreferences?.getBoolean("show_waiting_tasks", true) ?: true
    }

    override fun setShowWaitingTasks(value: Boolean) {
        sharedPreferences?.edit()?.putBoolean("show_waiting_tasks", value)?.apply()
        _showWaitingTasks.value = value
    }

    override fun getAutoWaiting(): Boolean {
        return sharedPreferences?.getBoolean("auto_waiting", false) ?: false
    }

    override fun setAutoWaiting(value: Boolean) {
        sharedPreferences?.edit()?.putBoolean("auto_waiting", value)?.apply()
        _autoWaiting.value = value
    }

    override fun getSortOrder(): String {
        return sharedPreferences?.getString("sort_order", "DATE_CREATED") ?: "DATE_CREATED"
    }

    override fun setSortOrder(value: String) {
        sharedPreferences?.edit()?.putString("sort_order", value)?.apply()
        _sortOrder.value = value
    }

    override fun getSortDirection(): String {
        return sharedPreferences?.getString("sort_direction", "") ?: ""
    }

    override fun setSortDirection(value: String) {
        sharedPreferences?.edit()?.putString("sort_direction", value)?.apply()
        _sortDirection.value = value
    }

    override fun getSyncType(): SyncType {
        return SyncType.fromValue(sharedPreferences?.getString("sync_type", null))
    }

    override fun setSyncType(type: SyncType) {
        sharedPreferences?.edit()?.putString("sync_type", type.value)?.apply()
    }

    override fun getCredentials(): SyncCredentials? {
        val prefs = sharedPreferences ?: return null
        val url = prefs.getString("sync_url", null)
        val clientId = prefs.getString("client_id", null)
        val secret = prefs.getString("encryption_secret", null)

        Log.d("SettingsRepository", "Getting credentials")

        return if (url != null && clientId != null && secret != null) {
            SyncCredentials(url, clientId, secret)
        } else {
            null
        }
    }

    override fun getS3Credentials(): S3Credentials? {
        val prefs = sharedPreferences ?: return null
        val bucket = prefs.getString("s3_bucket", null)
        val accessKeyId = prefs.getString("s3_access_key_id", null)
        val secretAccessKey = prefs.getString("s3_secret_access_key", null)
        val secret = prefs.getString("s3_encryption_secret", null)
        // region and endpoint are optional; blanks are treated as unset.
        val region = prefs.getString("s3_region", null)?.takeIf { it.isNotBlank() }
        val endpointUrl = prefs.getString("s3_endpoint_url", null)?.takeIf { it.isNotBlank() }

        Log.d("SettingsRepository", "Getting S3 credentials")

        return if (
            !bucket.isNullOrBlank() &&
            !accessKeyId.isNullOrBlank() &&
            !secretAccessKey.isNullOrBlank() &&
            !secret.isNullOrBlank()
        ) {
            S3Credentials(bucket, region, endpointUrl, accessKeyId, secretAccessKey, secret)
        } else {
            null
        }
    }

    override fun saveS3Credentials(
        bucket: String,
        region: String?,
        endpointUrl: String?,
        accessKeyId: String,
        secretAccessKey: String,
        secret: String,
    ) {
        val prefs = sharedPreferences
        if (prefs == null) {
            Log.e("SettingsRepository", "Cannot save: SharedPreferences is null")
            return
        }
        Log.d("SettingsRepository", "Saving S3 credentials")
        try {
            val success =
                prefs.edit()
                    .putString("s3_bucket", bucket)
                    .putString("s3_region", region ?: "")
                    .putString("s3_endpoint_url", endpointUrl ?: "")
                    .putString("s3_access_key_id", accessKeyId)
                    .putString("s3_secret_access_key", secretAccessKey)
                    .putString("s3_encryption_secret", secret)
                    .commit()
            Log.d("SettingsRepository", "Save success: $success")
        } catch (e: Exception) {
            Log.e("SettingsRepository", "Failed to save S3 credentials", e)
        }
    }

    override fun saveCredentials(
        url: String,
        clientId: String,
        secret: String,
    ) {
        val prefs = sharedPreferences
        if (prefs == null) {
            Log.e("SettingsRepository", "Cannot save: SharedPreferences is null")
            return
        }
        Log.d("SettingsRepository", "Saving credentials")
        try {
            val success =
                prefs.edit()
                    .putString("sync_url", url)
                    .putString("client_id", clientId)
                    .putString("encryption_secret", secret)
                    .commit()
            Log.d("SettingsRepository", "Save success: $success")
        } catch (e: Exception) {
            Log.e("SettingsRepository", "Failed to save credentials", e)
        }
    }

    override fun clearCredentials() {
        val prefs = sharedPreferences ?: return
        Log.d("SettingsRepository", "Clearing credentials")
        try {
            val success =
                prefs.edit()
                    .remove("sync_url")
                    .remove("client_id")
                    .remove("encryption_secret")
                    .remove("sync_type")
                    .remove("s3_bucket")
                    .remove("s3_region")
                    .remove("s3_endpoint_url")
                    .remove("s3_access_key_id")
                    .remove("s3_secret_access_key")
                    .remove("s3_encryption_secret")
                    .commit()
            Log.d("SettingsRepository", "Clear success: $success")
        } catch (e: Exception) {
            Log.e("SettingsRepository", "Failed to clear credentials", e)
        }
    }

    override fun hasCredentials(): Boolean {
        return when (getSyncType()) {
            SyncType.S3 -> getS3Credentials() != null
            SyncType.SERVER -> getCredentials() != null
        }
    }

    override fun getShowCompleted(): Boolean {
        return sharedPreferences?.getBoolean("show_completed", true) ?: true
    }

    override fun setShowCompleted(show: Boolean) {
        sharedPreferences?.edit()?.putBoolean("show_completed", show)?.apply()
        _showCompleted.value = show
    }

    override fun getShowInternalTags(): Boolean {
        return sharedPreferences?.getBoolean("show_internal_tags", false) ?: false
    }

    override fun setShowInternalTags(show: Boolean) {
        sharedPreferences?.edit()?.putBoolean("show_internal_tags", show)?.apply()
        _showInternalTags.value = show
    }

    override fun getShowEmptyProjects(): Boolean {
        return sharedPreferences?.getBoolean("show_empty_projects", false) ?: false
    }

    override fun setShowEmptyProjects(show: Boolean) {
        sharedPreferences?.edit()?.putBoolean("show_empty_projects", show)?.apply()
        _showEmptyProjects.value = show
    }

    override fun getTagsPerLine(): Int {
        return sharedPreferences?.getInt("tags_per_line", 4) ?: 4
    }

    override fun setTagsPerLine(count: Int) {
        sharedPreferences?.edit()?.putInt("tags_per_line", count)?.apply()
        _tagsPerLine.value = count
    }

    override fun getDailyNotificationsEnabled(): Boolean {
        return sharedPreferences?.getBoolean("daily_notifications", false) ?: false
    }

    override fun setDailyNotificationsEnabled(enabled: Boolean) {
        sharedPreferences?.edit()?.putBoolean("daily_notifications", enabled)?.apply()
        _dailyNotificationsEnabled.value = enabled
    }

    override fun getNotificationHour(): Int {
        return sharedPreferences?.getInt("notification_hour", 9) ?: 9
    }

    override fun setNotificationHour(hour: Int) {
        sharedPreferences?.edit()?.putInt("notification_hour", hour)?.apply()
        _notificationHour.value = hour
    }

    override fun getIncludeDueToday(): Boolean {
        return sharedPreferences?.getBoolean("include_due_today", true) ?: true
    }

    override fun setIncludeDueToday(enabled: Boolean) {
        sharedPreferences?.edit()?.putBoolean("include_due_today", enabled)?.apply()
        _includeDueToday.value = enabled
    }

    override fun getIncludeScheduledToday(): Boolean {
        return sharedPreferences?.getBoolean("include_scheduled_today", true) ?: true
    }

    override fun setIncludeScheduledToday(enabled: Boolean) {
        sharedPreferences?.edit()?.putBoolean("include_scheduled_today", enabled)?.apply()
        _includeScheduledToday.value = enabled
    }

    override fun getIncludeOverdue(): Boolean {
        return sharedPreferences?.getBoolean("include_overdue", false) ?: false
    }

    override fun setIncludeOverdue(enabled: Boolean) {
        sharedPreferences?.edit()?.putBoolean("include_overdue", enabled)?.apply()
        _includeOverdue.value = enabled
    }

    override fun getShowPriorityBadge(): Boolean {
        return sharedPreferences?.getBoolean("show_priority_badge", false) ?: false
    }

    override fun setShowPriorityBadge(enabled: Boolean) {
        sharedPreferences?.edit()?.putBoolean("show_priority_badge", enabled)?.apply()
        _showPriorityBadge.value = enabled
    }

    override fun getShowUrgencyBar(): Boolean {
        return sharedPreferences?.getBoolean("show_urgency_bar", false) ?: false
    }

    override fun setShowUrgencyBar(enabled: Boolean) {
        sharedPreferences?.edit()?.putBoolean("show_urgency_bar", enabled)?.apply()
        _showUrgencyBar.value = enabled
    }

    override fun getThemeMode(): ThemeMode {
        return themeModeSettings.get()
    }

    override fun setThemeMode(value: ThemeMode) {
        themeModeSettings.set(value)
        _themeMode.value = value
    }

    override fun getTaskContexts(): List<TaskContext> {
        return TaskContextCodec.decode(sharedPreferences?.getString("task_contexts", null))
    }

    override fun saveTaskContext(context: TaskContext) {
        val updated =
            (getTaskContexts().filterNot { it.id == context.id } + context)
                .sortedBy { it.name.lowercase(Locale.ROOT) }
        replaceTaskContexts(updated)
    }

    override fun replaceTaskContexts(contexts: List<TaskContext>) {
        val updated = contexts.sortedBy { it.name.lowercase(Locale.ROOT) }
        sharedPreferences
            ?.edit()
            ?.putString("task_contexts", TaskContextCodec.encode(updated))
            ?.apply()
        _taskContexts.value = updated
        val activeId = _activeTaskContextId.value
        if (activeId != null && updated.none { it.id == activeId }) {
            setActiveTaskContextId(null)
        }
    }

    override fun applyTaskrcImport(preview: TaskrcImportPreview) {
        replaceTaskContexts(preview.contextsAfter)
        setActiveTaskContextId(preview.activeContextIdAfter)
        setFirstDayOfWeek(preview.firstDayOfWeekAfter)
        preview.serverCredentialsAfter?.let {
            saveCredentials(it.url, it.clientId, it.secret)
        }
        preview.s3CredentialsAfter?.let {
            saveS3Credentials(it.bucket, it.region, it.endpointUrl, it.accessKeyId, it.secretAccessKey, it.secret)
        }
        preview.encryptionSecretAfter?.let { secret ->
            sharedPreferences
                ?.edit()
                ?.putString("encryption_secret", secret)
                ?.putString("s3_encryption_secret", secret)
                ?.apply()
        }
        if (preview.syncTypeAfter != getSyncType()) {
            setSyncType(preview.syncTypeAfter)
        }
    }

    override fun deleteTaskContext(id: String) {
        replaceTaskContexts(getTaskContexts().filterNot { it.id == id })
    }

    override fun getActiveTaskContextId(): String? {
        return sharedPreferences?.getString("active_task_context_id", null)
    }

    override fun setActiveTaskContextId(id: String?) {
        if (id == null) {
            sharedPreferences?.edit()?.remove("active_task_context_id")?.apply()
        } else {
            sharedPreferences?.edit()?.putString("active_task_context_id", id)?.apply()
        }
        _activeTaskContextId.value = id
    }
}

private class ThemeModeSettings(
    private val sharedPreferences: SharedPreferences?,
) {
    fun get(): ThemeMode {
        val storedValue = sharedPreferences?.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.storedValue)
        return ThemeMode.fromStoredValue(storedValue)
    }

    fun set(value: ThemeMode) {
        sharedPreferences?.edit()?.putString(KEY_THEME_MODE, value.storedValue)?.apply()
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
    }
}
