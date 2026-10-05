package com.brokenpip3.fatto.data

import com.brokenpip3.fatto.data.model.TaskContext
import com.brokenpip3.fatto.ui.theme.ThemeMode
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private const val SETTINGS_BACKUP_FORMAT_VERSION = 2
private val SORT_ORDERS = setOf("DATE_CREATED", "DUE_DATE", "PRIORITY", "URGENCY", "ALPHABETICAL", "SCHEDULED_DATE")
private val SORT_DIRECTIONS = setOf("", "ASCENDING", "DESCENDING")

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
    val journalTimeEnabled: Boolean = false,
    val journalStartAnnotation: String = JournalTimeDefaults.START_ANNOTATION,
    val journalStopAnnotation: String = JournalTimeDefaults.STOP_ANNOTATION,
    val tagsPerLine: Int,
    val sortOrder: String,
    val sortDirection: String,
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
            val formatVersion = root.requiredInt("formatVersion")
            if (formatVersion != SETTINGS_BACKUP_FORMAT_VERSION) {
                throw SettingsBackupError.UnsupportedFormat(formatVersion)
            }
            val versionCode = root.requiredInt("versionCode")
            if (versionCode > currentVersionCode) {
                throw SettingsBackupError.NewerVersion(versionCode, currentVersionCode)
            }
            SettingsBackupDocument(
                formatVersion = formatVersion,
                versionCode = versionCode,
                versionName = root.requiredString("versionName"),
                exportedAt = root.requiredString("exportedAt"),
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
            .put("journalTimeEnabled", settings.journalTimeEnabled)
            .put("journalStartAnnotation", settings.journalStartAnnotation)
            .put("journalStopAnnotation", settings.journalStopAnnotation)
            .put("tagsPerLine", settings.tagsPerLine)
            .put("sortOrder", settings.sortOrder)
            .put("sortDirection", settings.sortDirection)
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
            syncType =
                SyncType.entries.firstOrNull { it.value == json.requiredString("syncType") }
                    ?: throw JSONException("Invalid syncType"),
            serverCredentials = json.requiredNullableObject("serverCredentials")?.let(::decodeServerCredentials),
            s3Credentials = json.requiredNullableObject("s3Credentials")?.let(::decodeS3Credentials),
            showCompleted = json.requiredBoolean("showCompleted"),
            showInternalTags = json.requiredBoolean("showInternalTags"),
            showEmptyProjects = json.requiredBoolean("showEmptyProjects"),
            defaultProjectEnabled = json.requiredBoolean("defaultProjectEnabled"),
            defaultProject = json.requiredNullableString("defaultProject"),
            // Added after format v1 shipped, so older backups may not carry these.
            journalTimeEnabled = if (json.has("journalTimeEnabled")) json.requiredBoolean("journalTimeEnabled") else false,
            journalStartAnnotation =
                (if (json.has("journalStartAnnotation")) json.requiredString("journalStartAnnotation") else "")
                    .ifEmpty { JournalTimeDefaults.START_ANNOTATION },
            journalStopAnnotation =
                (if (json.has("journalStopAnnotation")) json.requiredString("journalStopAnnotation") else "")
                    .ifEmpty { JournalTimeDefaults.STOP_ANNOTATION },
            tagsPerLine = json.requiredInt("tagsPerLine"),
            sortOrder =
                json.requiredString("sortOrder").also { value ->
                    if (value !in SORT_ORDERS) throw JSONException("Invalid sortOrder")
                },
            sortDirection =
                json.requiredString("sortDirection").also { value ->
                    if (value !in SORT_DIRECTIONS) throw JSONException("Invalid sortDirection")
                },
            dailyNotificationsEnabled = json.requiredBoolean("dailyNotificationsEnabled"),
            notificationHour = json.requiredInt("notificationHour"),
            includeDueToday = json.requiredBoolean("includeDueToday"),
            includeScheduledToday = json.requiredBoolean("includeScheduledToday"),
            includeOverdue = json.requiredBoolean("includeOverdue"),
            firstDayOfWeek = json.requiredInt("firstDayOfWeek"),
            confirmActions = json.requiredBoolean("confirmActions"),
            hideBlockedTasksWaiting = json.requiredBoolean("hideBlockedTasksWaiting"),
            showWaitingTasks = json.requiredBoolean("showWaitingTasks"),
            autoWaiting = json.requiredBoolean("autoWaiting"),
            autoStopActiveOnComplete = json.requiredBoolean("autoStopActiveOnComplete"),
            showPriorityBadge = json.requiredBoolean("showPriorityBadge"),
            showUrgencyBar = json.requiredBoolean("showUrgencyBar"),
            swipeStartToEndAction =
                TaskSwipeAction.entries.firstOrNull { it.persistedValue == json.requiredString("swipeStartToEndAction") }
                    ?: throw JSONException("Invalid swipeStartToEndAction"),
            swipeEndToStartAction =
                TaskSwipeAction.entries.firstOrNull { it.persistedValue == json.requiredString("swipeEndToStartAction") }
                    ?: throw JSONException("Invalid swipeEndToStartAction"),
            themeMode =
                ThemeMode.entries.firstOrNull { it.storedValue == json.requiredString("themeMode") }
                    ?: throw JSONException("Invalid themeMode"),
            taskContexts = decodeTaskContexts(json.getJSONArray("taskContexts")),
            activeTaskContextId = json.requiredNullableString("activeTaskContextId"),
        )

    private fun encodeServerCredentials(credentials: SyncCredentials): JSONObject =
        JSONObject()
            .put("url", credentials.url)
            .put("clientId", credentials.clientId)
            .put("secret", credentials.secret)

    private fun decodeServerCredentials(json: JSONObject): SyncCredentials =
        SyncCredentials(
            url = json.requiredString("url"),
            clientId = json.requiredString("clientId"),
            secret = json.requiredString("secret"),
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
            bucket = json.requiredString("bucket"),
            region = json.requiredNullableString("region"),
            endpointUrl = json.requiredNullableString("endpointUrl"),
            accessKeyId = json.requiredString("accessKeyId"),
            secretAccessKey = json.requiredString("secretAccessKey"),
            secret = json.requiredString("secret"),
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
                        id = item.requiredString("id"),
                        name = item.requiredString("name"),
                        expressionText = item.requiredString("expressionText"),
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
            check(
                repository.replaceSyncSettings(
                    type = settings.syncType,
                    serverCredentials = settings.serverCredentials,
                    s3Credentials = settings.s3Credentials,
                ),
            ) { "Could not save sync settings" }
            repository.setShowCompleted(settings.showCompleted)
            repository.setShowInternalTags(settings.showInternalTags)
            repository.setShowEmptyProjects(settings.showEmptyProjects)
            repository.setDefaultProject(settings.defaultProject)
            repository.setDefaultProjectEnabled(settings.defaultProjectEnabled)
            repository.setJournalTimeEnabled(settings.journalTimeEnabled)
            repository.setJournalStartAnnotation(settings.journalStartAnnotation)
            repository.setJournalStopAnnotation(settings.journalStopAnnotation)
            repository.setTagsPerLine(settings.tagsPerLine)
            repository.setSortOrder(settings.sortOrder)
            repository.setSortDirection(settings.sortDirection)
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
            journalTimeEnabled = getJournalTimeEnabled(),
            journalStartAnnotation = getJournalStartAnnotation(),
            journalStopAnnotation = getJournalStopAnnotation(),
            tagsPerLine = getTagsPerLine(),
            sortOrder = getSortOrder(),
            sortDirection = getSortDirection(),
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

private fun JSONObject.requiredString(name: String): String = (get(name) as? String) ?: throw JSONException("Invalid $name")

private fun JSONObject.requiredNullableString(name: String): String? = if (has(name) && isNull(name)) null else requiredString(name)

private fun JSONObject.requiredBoolean(name: String): Boolean = (get(name) as? Boolean) ?: throw JSONException("Invalid $name")

private fun JSONObject.requiredInt(name: String): Int {
    val number = get(name) as? Number ?: throw JSONException("Invalid $name")
    val value = number.toDouble()
    if (!value.isFinite() || value % 1.0 != 0.0 || value < Int.MIN_VALUE || value > Int.MAX_VALUE) {
        throw JSONException("Invalid $name")
    }
    return value.toInt()
}

private fun JSONObject.requiredNullableObject(name: String): JSONObject? =
    if (has(name) && isNull(name)) null else get(name) as? JSONObject ?: throw JSONException("Invalid $name")
