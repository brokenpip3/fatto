package com.brokenpip3.fatto.data

import com.brokenpip3.fatto.data.filter.TaskFilterExpressionParser
import com.brokenpip3.fatto.data.model.TaskContext
import java.util.Calendar
import java.util.Locale
import java.util.UUID

enum class TaskrcImportResultType {
    ADDED,
    UPDATED,
    UNCHANGED,
    ACTIVATED,
    SKIPPED,
    ERROR,
}

data class TaskrcImportAction(
    val type: TaskrcImportResultType,
    val lineNumber: Int,
    val key: String,
    val message: String,
)

data class TaskrcImportPreview(
    val actions: List<TaskrcImportAction>,
    val contextsAfter: List<TaskContext>,
    val activeContextIdAfter: String?,
    val firstDayOfWeekAfter: Int,
    val serverCredentialsAfter: SyncCredentials? = null,
    val s3CredentialsAfter: S3Credentials? = null,
    val encryptionSecretAfter: String? = null,
    val syncTypeAfter: SyncType = SyncType.SERVER,
) {
    val hasErrors: Boolean = actions.any { it.type == TaskrcImportResultType.ERROR }
}

object TaskrcImporter {
    private val UUID_REGEX =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    fun preview(
        text: String,
        existingContexts: List<TaskContext>,
        currentActiveContextId: String?,
        currentFirstDayOfWeek: Int,
        currentSyncCredentials: SyncCredentials? = null,
        currentS3Credentials: S3Credentials? = null,
        currentSyncType: SyncType = SyncType.SERVER,
    ): TaskrcImportPreview {
        val actions = mutableListOf<TaskrcImportAction>()
        val contextsByName = existingContexts.associateBy { it.name }.toMutableMap()
        var requestedActiveName: String? = null
        var activeContextId = currentActiveContextId
        var firstDayOfWeek = currentFirstDayOfWeek

        // Storage key collection: short name -> (line number, value). Only non-empty values.
        val serverValues = mutableMapOf<String, Pair<Int, String>>()
        val s3Values = mutableMapOf<String, Pair<Int, String>>()
        var secretLine: Pair<Int, String>? = null

        text.lines().forEachIndexed { index, rawLine ->
            val lineNumber = index + 1
            parseLine(rawLine)?.let { entry ->
                when {
                    entry.isInclude ->
                        actions += TaskrcImportAction(TaskrcImportResultType.SKIPPED, lineNumber, "include", "Includes are not imported")

                    entry.key == "context" ->
                        requestedActiveName = entry.value

                    entry.key == "weekstart" ->
                        actions += previewWeekstart(lineNumber, entry.value, firstDayOfWeek) { firstDayOfWeek = it }

                    entry.key.startsWith("context.") && entry.key.endsWith(".read") ->
                        actions += previewContextRead(lineNumber, entry.key, entry.value, contextsByName)

                    entry.key.startsWith("context.") && entry.key.contains(".write") ->
                        actions +=
                            TaskrcImportAction(
                                TaskrcImportResultType.SKIPPED,
                                lineNumber,
                                entry.key,
                                "Context write modifications are not supported",
                            )

                    entry.key.startsWith("context.") && entry.key.contains(".rc.") ->
                        actions +=
                            TaskrcImportAction(
                                TaskrcImportResultType.SKIPPED,
                                lineNumber,
                                entry.key,
                                "Context rc overrides are not supported",
                            )

                    entry.key == "sync.encryption_secret" ->
                        if (entry.value.isEmpty()) {
                            actions += emptyValueAction(lineNumber, entry.key)
                        } else {
                            secretLine = lineNumber to entry.value
                        }

                    entry.key == "sync.server.url" ->
                        collectStorageValue(serverValues, "url", lineNumber, entry.key, entry.value, actions)

                    entry.key == "sync.server.client_id" ->
                        collectStorageValue(serverValues, "client_id", lineNumber, entry.key, entry.value, actions)

                    entry.key == "sync.aws.bucket" ->
                        collectStorageValue(s3Values, "bucket", lineNumber, entry.key, entry.value, actions)

                    entry.key == "sync.aws.access_key_id" ->
                        collectStorageValue(s3Values, "access_key_id", lineNumber, entry.key, entry.value, actions)

                    entry.key == "sync.aws.secret_access_key" ->
                        collectStorageValue(s3Values, "secret_access_key", lineNumber, entry.key, entry.value, actions)

                    entry.key == "sync.aws.region" ->
                        collectStorageValue(s3Values, "region", lineNumber, entry.key, entry.value, actions)

                    entry.key == "sync.aws.endpoint" ->
                        collectStorageValue(s3Values, "endpoint", lineNumber, entry.key, entry.value, actions)

                    entry.key.startsWith("taskd.") ->
                        if (entry.value.isEmpty()) {
                            actions += emptyValueAction(lineNumber, entry.key)
                        } else {
                            actions +=
                                TaskrcImportAction(
                                    TaskrcImportResultType.SKIPPED,
                                    lineNumber,
                                    entry.key,
                                    "Taskwarrior 2.x taskd settings are not supported and were not imported",
                                )
                        }

                    else ->
                        actions += TaskrcImportAction(TaskrcImportResultType.SKIPPED, lineNumber, entry.key, "Unsupported taskrc key")
                }
            }
        }

        requestedActiveName?.let { name ->
            val activeContext = contextsByName[name]
            if (activeContext == null) {
                actions += TaskrcImportAction(TaskrcImportResultType.ERROR, 0, "context", "Active context '$name' was not imported")
            } else {
                activeContextId = activeContext.id
                actions += TaskrcImportAction(TaskrcImportResultType.ACTIVATED, 0, "context", "Active context set to '$name'")
            }
        }

        val storage = previewStorage(serverValues, s3Values, secretLine, currentSyncCredentials, currentS3Credentials, currentSyncType)
        actions += storage.actions

        return TaskrcImportPreview(
            actions = actions,
            contextsAfter = contextsByName.values.sortedBy { it.name.lowercase(Locale.ROOT) },
            activeContextIdAfter = activeContextId,
            firstDayOfWeekAfter = firstDayOfWeek,
            serverCredentialsAfter = storage.serverCredentialsAfter,
            s3CredentialsAfter = storage.s3CredentialsAfter,
            encryptionSecretAfter = storage.encryptionSecretAfter,
            syncTypeAfter = storage.syncTypeAfter,
        )
    }

    private data class StoragePreview(
        val actions: List<TaskrcImportAction>,
        val serverCredentialsAfter: SyncCredentials?,
        val s3CredentialsAfter: S3Credentials?,
        val encryptionSecretAfter: String?,
        val syncTypeAfter: SyncType,
    )

    private fun previewStorage(
        serverValues: Map<String, Pair<Int, String>>,
        s3Values: Map<String, Pair<Int, String>>,
        secretLine: Pair<Int, String>?,
        currentSyncCredentials: SyncCredentials?,
        currentS3Credentials: S3Credentials?,
        currentSyncType: SyncType,
    ): StoragePreview {
        val actions = mutableListOf<TaskrcImportAction>()
        val secret = secretLine?.second
        val clientIdInvalid = serverValues["client_id"]?.second?.let { !UUID_REGEX.matches(it) } == true

        val serverComplete =
            !clientIdInvalid &&
                serverValues.containsKey("url") &&
                serverValues.containsKey("client_id") &&
                secret != null
        val s3Complete =
            s3Values.containsKey("bucket") &&
                s3Values.containsKey("access_key_id") &&
                s3Values.containsKey("secret_access_key") &&
                secret != null
        val hasServerKeys = serverValues.isNotEmpty()
        val hasS3Keys = s3Values.isNotEmpty()

        if (clientIdInvalid) {
            actions +=
                TaskrcImportAction(
                    TaskrcImportResultType.ERROR,
                    serverValues["client_id"]!!.first,
                    "sync.server.client_id",
                    "Invalid client ID",
                )
        }
        if (serverComplete && s3Complete) {
            actions +=
                TaskrcImportAction(
                    TaskrcImportResultType.ERROR,
                    0,
                    "sync",
                    "Both server and S3 credentials are complete; remove one set to import storage settings",
                )
        }
        if (hasServerKeys && !serverComplete && !clientIdInvalid) {
            val missing =
                buildList {
                    if (!serverValues.containsKey("url")) add("sync.server.url")
                    if (!serverValues.containsKey("client_id")) add("sync.server.client_id")
                    if (secret == null) add("sync.encryption_secret")
                }
            actions +=
                TaskrcImportAction(
                    TaskrcImportResultType.ERROR,
                    0,
                    "sync.server",
                    "Server credentials incomplete: missing ${missing.joinToString(", ")}",
                )
        }
        if (hasS3Keys && !s3Complete) {
            val missing =
                buildList {
                    if (!s3Values.containsKey("bucket")) add("sync.aws.bucket")
                    if (!s3Values.containsKey("access_key_id")) add("sync.aws.access_key_id")
                    if (!s3Values.containsKey("secret_access_key")) add("sync.aws.secret_access_key")
                    if (secret == null) add("sync.encryption_secret")
                }
            actions +=
                TaskrcImportAction(
                    TaskrcImportResultType.ERROR,
                    0,
                    "sync.aws",
                    "S3 credentials incomplete: missing ${missing.joinToString(", ")}",
                )
        }

        val hasStorageError =
            clientIdInvalid ||
                (serverComplete && s3Complete) ||
                (hasServerKeys && !serverComplete) ||
                (hasS3Keys && !s3Complete)

        return when {
            hasStorageError ->
                StoragePreview(actions, null, null, null, currentSyncType)

            serverComplete -> {
                val url = serverValues["url"]!!.second
                val clientId = serverValues["client_id"]!!.second
                val urlLine = serverValues["url"]!!.first
                val clientIdLine = serverValues["client_id"]!!.first
                actions += storageAction("sync.server.url", urlLine, url, currentSyncCredentials?.url, "Server URL")
                actions +=
                    storageAction(
                        "sync.server.client_id",
                        clientIdLine,
                        clientId,
                        currentSyncCredentials?.clientId,
                        "Client ID",
                    )
                actions +=
                    storageAction(
                        "sync.encryption_secret",
                        secretLine.first,
                        secret,
                        currentSyncCredentials?.secret,
                        "Encryption secret",
                    )
                if (currentSyncType != SyncType.SERVER) {
                    actions += TaskrcImportAction(TaskrcImportResultType.UPDATED, 0, "sync.type", "Sync type switched to Server")
                }
                StoragePreview(actions, SyncCredentials(url, clientId, secret), null, null, SyncType.SERVER)
            }

            s3Complete -> {
                val bucket = s3Values["bucket"]!!.second
                val accessKeyId = s3Values["access_key_id"]!!.second
                val secretAccessKey = s3Values["secret_access_key"]!!.second
                val region = s3Values["region"]?.second
                val endpoint = s3Values["endpoint"]?.second
                actions += storageAction("sync.aws.bucket", s3Values["bucket"]!!.first, bucket, currentS3Credentials?.bucket, "S3 bucket")
                actions +=
                    storageAction(
                        "sync.aws.access_key_id",
                        s3Values["access_key_id"]!!.first,
                        accessKeyId,
                        currentS3Credentials?.accessKeyId,
                        "S3 access key ID",
                    )
                actions +=
                    storageAction(
                        "sync.aws.secret_access_key",
                        s3Values["secret_access_key"]!!.first,
                        secretAccessKey,
                        currentS3Credentials?.secretAccessKey,
                        "S3 secret access key",
                    )
                s3Values["region"]?.let { (line, value) ->
                    actions += storageAction("sync.aws.region", line, value, currentS3Credentials?.region, "S3 region")
                }
                s3Values["endpoint"]?.let { (line, value) ->
                    actions += storageAction("sync.aws.endpoint", line, value, currentS3Credentials?.endpointUrl, "S3 endpoint URL")
                }
                actions +=
                    storageAction(
                        "sync.encryption_secret",
                        secretLine.first,
                        secret,
                        currentS3Credentials?.secret,
                        "Encryption secret",
                    )
                if (currentSyncType != SyncType.S3) {
                    actions += TaskrcImportAction(TaskrcImportResultType.UPDATED, 0, "sync.type", "Sync type switched to S3")
                }
                StoragePreview(
                    actions,
                    null,
                    S3Credentials(bucket, region, endpoint, accessKeyId, secretAccessKey, secret),
                    null,
                    SyncType.S3,
                )
            }

            secret != null -> {
                val type =
                    when {
                        secret == currentSyncCredentials?.secret &&
                            secret == currentS3Credentials?.secret -> TaskrcImportResultType.UNCHANGED

                        currentSyncCredentials?.secret == null &&
                            currentS3Credentials?.secret == null -> TaskrcImportResultType.ADDED

                        else -> TaskrcImportResultType.UPDATED
                    }
                val message =
                    when (type) {
                        TaskrcImportResultType.ADDED -> "Encryption secret imported for both backends"
                        TaskrcImportResultType.UPDATED -> "Encryption secret updated for both backends"
                        else -> "Encryption secret already set for both backends"
                    }
                actions += TaskrcImportAction(type, secretLine.first, "sync.encryption_secret", message)
                StoragePreview(actions, null, null, secret, currentSyncType)
            }

            else -> StoragePreview(actions, null, null, null, currentSyncType)
        }
    }

    private fun collectStorageValue(
        store: MutableMap<String, Pair<Int, String>>,
        shortKey: String,
        lineNumber: Int,
        key: String,
        value: String,
        actions: MutableList<TaskrcImportAction>,
    ) {
        if (value.isEmpty()) {
            actions += emptyValueAction(lineNumber, key)
        } else {
            store[shortKey] = lineNumber to value
        }
    }

    private fun emptyValueAction(
        lineNumber: Int,
        key: String,
    ): TaskrcImportAction = TaskrcImportAction(TaskrcImportResultType.SKIPPED, lineNumber, key, "Empty value ignored")

    private fun storageAction(
        key: String,
        lineNumber: Int,
        value: String,
        currentValue: String?,
        label: String,
    ): TaskrcImportAction {
        val type =
            when {
                currentValue == null -> TaskrcImportResultType.ADDED
                currentValue == value -> TaskrcImportResultType.UNCHANGED
                else -> TaskrcImportResultType.UPDATED
            }
        return TaskrcImportAction(type, lineNumber, key, label)
    }

    private fun parseLine(rawLine: String): TaskrcLine? {
        val line = rawLine.substringBefore("#").trim()
        return when {
            line.isBlank() -> null
            line.startsWith("include ") ->
                TaskrcLine(key = "include", value = line.removePrefix("include").trim(), isInclude = true)
            !line.contains("=") ->
                TaskrcLine(key = line, value = "")
            else ->
                TaskrcLine(
                    key = line.substringBefore("=").trim(),
                    value = line.substringAfter("=").trim(),
                )
        }
    }

    private fun previewWeekstart(
        lineNumber: Int,
        value: String,
        currentValue: Int,
        update: (Int) -> Unit,
    ): TaskrcImportAction {
        val parsed =
            when (value.lowercase(Locale.ROOT)) {
                "sunday" -> Calendar.SUNDAY
                "monday" -> Calendar.MONDAY
                else -> null
            }
        return when {
            parsed == null ->
                TaskrcImportAction(TaskrcImportResultType.ERROR, lineNumber, "weekstart", "Unsupported weekstart '$value'")
            parsed == currentValue ->
                TaskrcImportAction(TaskrcImportResultType.UNCHANGED, lineNumber, "weekstart", "Week already starts on $value")
            else -> {
                update(parsed)
                TaskrcImportAction(TaskrcImportResultType.UPDATED, lineNumber, "weekstart", "Weekstart changed to $value")
            }
        }
    }

    private fun previewContextRead(
        lineNumber: Int,
        key: String,
        value: String,
        contextsByName: MutableMap<String, TaskContext>,
    ): TaskrcImportAction {
        val name = key.removePrefix("context.").removeSuffix(".read")
        val parseError = TaskFilterExpressionParser.parse(value).exceptionOrNull()
        return when {
            name.isBlank() ->
                TaskrcImportAction(TaskrcImportResultType.ERROR, lineNumber, key, "Empty context name")
            parseError != null ->
                TaskrcImportAction(TaskrcImportResultType.ERROR, lineNumber, key, parseError.message ?: "Invalid context expression")
            else -> {
                val existing = contextsByName[name]
                contextsByName[name] =
                    existing?.copy(expressionText = value)
                        ?: TaskContext(id = UUID.randomUUID().toString(), name = name, expressionText = value)

                val type =
                    when {
                        existing == null -> TaskrcImportResultType.ADDED
                        existing.expressionText == value -> TaskrcImportResultType.UNCHANGED
                        else -> TaskrcImportResultType.UPDATED
                    }
                TaskrcImportAction(type, lineNumber, key, "Context '$name'")
            }
        }
    }

    private data class TaskrcLine(
        val key: String,
        val value: String,
        val isInclude: Boolean = false,
    )
}
