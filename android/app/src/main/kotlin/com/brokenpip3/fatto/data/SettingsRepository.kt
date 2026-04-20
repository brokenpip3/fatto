package com.brokenpip3.fatto.data

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SyncCredentials(
    val url: String,
    val clientId: String,
    val secret: String,
)

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

    fun getCredentials(): SyncCredentials?

    fun saveCredentials(
        url: String,
        clientId: String,
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
}

class SettingsRepositoryImpl(context: Context) : SettingsRepository {
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
                    .commit()
            Log.d("SettingsRepository", "Clear success: $success")
        } catch (e: Exception) {
            Log.e("SettingsRepository", "Failed to clear credentials", e)
        }
    }

    override fun hasCredentials(): Boolean {
        return getCredentials() != null
    }

    override fun getShowCompleted(): Boolean {
        return sharedPreferences?.getBoolean("show_completed", true) ?: true
    }

    override fun setShowCompleted(show: Boolean) {
        sharedPreferences?.edit()?.putBoolean("show_completed", show)?.apply()
        _showCompleted.value = show
    }

    override fun getShowInternalTags(): Boolean {
        return sharedPreferences?.getBoolean("show_internal_tags", true) ?: true
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
}
