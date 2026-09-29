package com.brokenpip3.fatto.data

import com.brokenpip3.fatto.data.model.TaskContext
import com.brokenpip3.fatto.ui.theme.ThemeMode
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private const val SETTINGS_BACKUP_FORMAT_VERSION = 1

data class SettingsBackupDocument(
    val formatVersion: Int,
    val versionCode: Int,
    val versionName: String,
    val exportedAt: String,
    val settings: SettingsBackupSettings,
)

data class SettingsBackupSettings(
    val syncType: SyncType,
    val serverCredentials: SyncCredentials?,
    val s3Credentials: S3Credentials?,
    val showCompleted: Boolean,
    val showInternalTags: Boolean,
    val showEmptyProjects: Boolean,
    val defaultProjectEnabled: Boolean,
    val defaultProject: String?,
    val tagsPerLine: Int,
    val dailyNotificationsEnabled: Boolean,
    val notificationHour: Int,
    val includeDueToday: Boolean,
    val includeScheduledToday: Boolean,
    val includeOverdue: Boolean,
    val firstDayOfWeek: Int,
    val confirmActions: Boolean,
    val hideBlockedTasksWaiting: Boolean,
    val showWaitingTasks: Boolean,
    val autoWaiting: Boolean,
    val autoStopActiveOnComplete: Boolean,
    val showPriorityBadge: Boolean,
    val showUrgencyBar: Boolean,
    val swipeStartToEndAction: TaskSwipeAction,
    val swipeEndToStartAction: TaskSwipeAction,
    val themeMode: ThemeMode,
    val taskContexts: List<TaskContext>,
    val activeTaskContextId: String?,
)

sealed class SettingsBackupError(message: String) : IllegalArgumentException(message) {
    data object InvalidJson : SettingsBackupError("Invalid settings backup file")

    data class UnsupportedFormat(val foundFormatVersion: Int) :
        SettingsBackupError("Unsupported settings backup format")

    data class NewerVersion(val backupVersionCode: Int, val currentVersionCode: Int) :
        SettingsBackupError("Backup was created by a newer Fatto version")
}

class SettingsBackupCodec(
    private val currentVersionCode: Int,
    private val currentVersionName: String,
    private val clock: () -> String,
) {
    fun document(settings: SettingsBackupSettings): SettingsBackupDocument =
        SettingsBackupDocument(
            formatVersion = SETTINGS_BACKUP_FORMAT_VERSION,
            versionCode = currentVersionCode,
            versionName = currentVersionName,
            exportedAt = clock(),
            settings = settings,
        )

    fun encode(document: SettingsBackupDocument): String =
        JSONObject()
            .put("formatVersion", document.formatVersion)
            .put("versionCode", document.versionCode)
            .put("versionName", document.versionName)
            .put("exportedAt", document.exportedAt)
            .put("settings", encodeSettings(document.settings))
            .toString()

    fun decode(json: String): Result<SettingsBackupDocument> =
        runCatching {
            val root = JSONObject(json)
            val formatVersion = root.getInt("formatVersion")
            if (formatVersion != SETTINGS_BACKUP_FORMAT_VERSION) {
                throw SettingsBackupError.UnsupportedFormat(formatVersion)
            }
            val versionCode = root.getInt("versionCode")
            if (versionCode > currentVersionCode) {
                throw SettingsBackupError.NewerVersion(versionCode, currentVersionCode)
            }
            SettingsBackupDocument(
                formatVersion = formatVersion,
                versionCode = versionCode,
                versionName = root.optString("versionName", ""),
                exportedAt = root.optString("exportedAt", ""),
                settings = decodeSettings(root.getJSONObject("settings")),
            )
        }.recoverCatching { throwable ->
            when (throwable) {
                is SettingsBackupError -> throw throwable
                is JSONException -> throw SettingsBackupError.InvalidJson
                else -> throw SettingsBackupError.InvalidJson
            }
        }

    private fun encodeSettings(settings: SettingsBackupSettings): JSONObject =
        JSONObject()
            .put("syncType", settings.syncType.value)
            .put("serverCredentials", settings.serverCredentials?.let(::encodeServerCredentials) ?: JSONObject.NULL)
            .put("s3Credentials", settings.s3Credentials?.let(::encodeS3Credentials) ?: JSONObject.NULL)
            .put("showCompleted", settings.showCompleted)
            .put("showInternalTags", settings.showInternalTags)
            .put("showEmptyProjects", settings.showEmptyProjects)
            .put("defaultProjectEnabled", settings.defaultProjectEnabled)
            .putNullable("defaultProject", settings.defaultProject)
            .put("tagsPerLine", settings.tagsPerLine)
            .put("dailyNotificationsEnabled", settings.dailyNotificationsEnabled)
            .put("notificationHour", settings.notificationHour)
            .put("includeDueToday", settings.includeDueToday)
            .put("includeScheduledToday", settings.includeScheduledToday)
            .put("includeOverdue", settings.includeOverdue)
            .put("firstDayOfWeek", settings.firstDayOfWeek)
            .put("confirmActions", settings.confirmActions)
            .put("hideBlockedTasksWaiting", settings.hideBlockedTasksWaiting)
            .put("showWaitingTasks", settings.showWaitingTasks)
            .put("autoWaiting", settings.autoWaiting)
            .put("autoStopActiveOnComplete", settings.autoStopActiveOnComplete)
            .put("showPriorityBadge", settings.showPriorityBadge)
            .put("showUrgencyBar", settings.showUrgencyBar)
            .put("swipeStartToEndAction", settings.swipeStartToEndAction.persistedValue)
            .put("swipeEndToStartAction", settings.swipeEndToStartAction.persistedValue)
            .put("themeMode", settings.themeMode.storedValue)
            .put("taskContexts", JSONArray(settings.taskContexts.map(::encodeTaskContext)))
            .putNullable("activeTaskContextId", settings.activeTaskContextId)

    private fun decodeSettings(json: JSONObject): SettingsBackupSettings =
        SettingsBackupSettings(
            syncType = SyncType.fromValue(json.optString("syncType", null)),
            serverCredentials = json.optJSONObject("serverCredentials")?.let(::decodeServerCredentials),
            s3Credentials = json.optJSONObject("s3Credentials")?.let(::decodeS3Credentials),
            showCompleted = json.optBoolean("showCompleted", true),
            showInternalTags = json.optBoolean("showInternalTags", false),
            showEmptyProjects = json.optBoolean("showEmptyProjects", false),
            defaultProjectEnabled = json.optBoolean("defaultProjectEnabled", false),
            defaultProject = json.optNullableString("defaultProject"),
            tagsPerLine = json.optInt("tagsPerLine", 4),
            dailyNotificationsEnabled = json.optBoolean("dailyNotificationsEnabled", false),
            notificationHour = json.optInt("notificationHour", 9),
            includeDueToday = json.optBoolean("includeDueToday", true),
            includeScheduledToday = json.optBoolean("includeScheduledToday", true),
            includeOverdue = json.optBoolean("includeOverdue", false),
            firstDayOfWeek = json.optInt("firstDayOfWeek", java.util.Calendar.MONDAY),
            confirmActions = json.optBoolean("confirmActions", true),
            hideBlockedTasksWaiting = json.optBoolean("hideBlockedTasksWaiting", false),
            showWaitingTasks = json.optBoolean("showWaitingTasks", true),
            autoWaiting = json.optBoolean("autoWaiting", false),
            autoStopActiveOnComplete = json.optBoolean("autoStopActiveOnComplete", true),
            showPriorityBadge = json.optBoolean("showPriorityBadge", false),
            showUrgencyBar = json.optBoolean("showUrgencyBar", false),
            swipeStartToEndAction = TaskSwipeAction.fromPersistedValue(json.optString("swipeStartToEndAction", null)),
            swipeEndToStartAction = TaskSwipeAction.fromPersistedValue(json.optString("swipeEndToStartAction", null)),
            themeMode = ThemeMode.fromStoredValue(json.optString("themeMode", null)),
            taskContexts = decodeTaskContexts(json.optJSONArray("taskContexts")),
            activeTaskContextId = json.optNullableString("activeTaskContextId"),
        )

    private fun encodeServerCredentials(credentials: SyncCredentials): JSONObject =
        JSONObject()
            .put("url", credentials.url)
            .put("clientId", credentials.clientId)
            .put("secret", credentials.secret)

    private fun decodeServerCredentials(json: JSONObject): SyncCredentials =
        SyncCredentials(
            url = json.getString("url"),
            clientId = json.getString("clientId"),
            secret = json.getString("secret"),
        )

    private fun encodeS3Credentials(credentials: S3Credentials): JSONObject =
        JSONObject()
            .put("bucket", credentials.bucket)
            .putNullable("region", credentials.region)
            .putNullable("endpointUrl", credentials.endpointUrl)
            .put("accessKeyId", credentials.accessKeyId)
            .put("secretAccessKey", credentials.secretAccessKey)
            .put("secret", credentials.secret)

    private fun decodeS3Credentials(json: JSONObject): S3Credentials =
        S3Credentials(
            bucket = json.getString("bucket"),
            region = json.optNullableString("region"),
            endpointUrl = json.optNullableString("endpointUrl"),
            accessKeyId = json.getString("accessKeyId"),
            secretAccessKey = json.getString("secretAccessKey"),
            secret = json.getString("secret"),
        )

    private fun encodeTaskContext(context: TaskContext): JSONObject =
        JSONObject()
            .put("id", context.id)
            .put("name", context.name)
            .put("expressionText", context.expressionText)

    private fun decodeTaskContexts(array: JSONArray?): List<TaskContext> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    TaskContext(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        expressionText = item.optString("expressionText", ""),
                    ),
                )
            }
        }
    }
}

class SettingsBackupService(
    private val repository: SettingsRepository,
    private val codec: SettingsBackupCodec,
) {
    fun exportJson(): Result<String> =
        runCatching {
            codec.encode(codec.document(repository.readBackupSettings()))
        }

    fun parseImport(json: String): Result<SettingsBackupDocument> = codec.decode(json)

    fun applyImport(document: SettingsBackupDocument): Result<Unit> =
        runCatching {
            val settings = document.settings
            repository.setSyncType(settings.syncType)
            settings.serverCredentials?.let { repository.saveCredentials(it.url, it.clientId, it.secret) }
            settings.s3Credentials?.let {
                repository.saveS3Credentials(
                    bucket = it.bucket,
                    region = it.region,
                    endpointUrl = it.endpointUrl,
                    accessKeyId = it.accessKeyId,
                    secretAccessKey = it.secretAccessKey,
                    secret = it.secret,
                )
            }
            repository.setShowCompleted(settings.showCompleted)
            repository.setShowInternalTags(settings.showInternalTags)
            repository.setShowEmptyProjects(settings.showEmptyProjects)
            repository.setDefaultProject(settings.defaultProject)
            repository.setDefaultProjectEnabled(settings.defaultProjectEnabled)
            repository.setTagsPerLine(settings.tagsPerLine)
            repository.setDailyNotificationsEnabled(settings.dailyNotificationsEnabled)
            repository.setNotificationHour(settings.notificationHour)
            repository.setIncludeDueToday(settings.includeDueToday)
            repository.setIncludeScheduledToday(settings.includeScheduledToday)
            repository.setIncludeOverdue(settings.includeOverdue)
            repository.setFirstDayOfWeek(settings.firstDayOfWeek)
            repository.setConfirmActions(settings.confirmActions)
            repository.setHideBlockedTasksWaiting(settings.hideBlockedTasksWaiting)
            repository.setShowWaitingTasks(settings.showWaitingTasks)
            repository.setAutoWaiting(settings.autoWaiting)
            repository.setAutoStopActiveOnComplete(settings.autoStopActiveOnComplete)
            repository.setShowPriorityBadge(settings.showPriorityBadge)
            repository.setShowUrgencyBar(settings.showUrgencyBar)
            repository.setSwipeStartToEndAction(settings.swipeStartToEndAction)
            repository.setSwipeEndToStartAction(settings.swipeEndToStartAction)
            repository.setThemeMode(settings.themeMode)
            repository.replaceTaskContexts(settings.taskContexts)
            repository.setActiveTaskContextId(settings.activeTaskContextId)
        }

    private fun SettingsRepository.readBackupSettings(): SettingsBackupSettings =
        SettingsBackupSettings(
            syncType = getSyncType(),
            serverCredentials = getCredentials(),
            s3Credentials = getS3Credentials(),
            showCompleted = getShowCompleted(),
            showInternalTags = getShowInternalTags(),
            showEmptyProjects = getShowEmptyProjects(),
            defaultProjectEnabled = getDefaultProjectEnabled(),
            defaultProject = getDefaultProject(),
            tagsPerLine = getTagsPerLine(),
            dailyNotificationsEnabled = getDailyNotificationsEnabled(),
            notificationHour = getNotificationHour(),
            includeDueToday = getIncludeDueToday(),
            includeScheduledToday = getIncludeScheduledToday(),
            includeOverdue = getIncludeOverdue(),
            firstDayOfWeek = getFirstDayOfWeek(),
            confirmActions = getConfirmActions(),
            hideBlockedTasksWaiting = getHideBlockedTasksWaiting(),
            showWaitingTasks = getShowWaitingTasks(),
            autoWaiting = getAutoWaiting(),
            autoStopActiveOnComplete = getAutoStopActiveOnComplete(),
            showPriorityBadge = getShowPriorityBadge(),
            showUrgencyBar = getShowUrgencyBar(),
            swipeStartToEndAction = getSwipeStartToEndAction(),
            swipeEndToStartAction = getSwipeEndToStartAction(),
            themeMode = getThemeMode(),
            taskContexts = getTaskContexts(),
            activeTaskContextId = getActiveTaskContextId(),
        )
}

private fun JSONObject.putNullable(
    name: String,
    value: String?,
): JSONObject = put(name, value ?: JSONObject.NULL)

private fun JSONObject.optNullableString(name: String): String? =
    if (has(name) && !isNull(name)) optString(name).takeIf { it.isNotBlank() } else null
