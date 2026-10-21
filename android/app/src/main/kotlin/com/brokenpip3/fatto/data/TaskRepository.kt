package com.brokenpip3.fatto.data

import android.content.Context
import android.util.Log
import com.brokenpip3.fatto.data.model.Annotation
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.TaskContext
import com.brokenpip3.fatto.data.model.toEditPatch
import com.brokenpip3.fatto.data.model.toModel
import com.brokenpip3.fatto.data.review.ReviewPolicy
import com.brokenpip3.fatto.data.review.ReviewTaskStore
import com.brokenpip3.fatto.widget.WidgetRefreshReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uniffi.taskchampion_android.ReplicaWrapper
import uniffi.taskchampion_android.TaskAddProps
import uniffi.taskchampion_android.TaskStatus
import java.io.File

class TaskRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val baseDir: File = context.filesDir,
) : ReviewTaskStore {
    private var replica: ReplicaWrapper? = null
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    override val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()
    val showCompleted: StateFlow<Boolean> = settingsRepository.showCompleted
    val showInternalTags: StateFlow<Boolean> = settingsRepository.showInternalTags
    val showEmptyProjects: StateFlow<Boolean> = settingsRepository.showEmptyProjects
    val tagsPerLine: StateFlow<Int> = settingsRepository.tagsPerLine
    val firstDayOfWeek: StateFlow<Int> = settingsRepository.firstDayOfWeek
    val hideBlockedTasksWaiting: StateFlow<Boolean> = settingsRepository.hideBlockedTasksWaiting
    val showWaitingTasks: StateFlow<Boolean> = settingsRepository.showWaitingTasks
    val autoWaiting: StateFlow<Boolean> = settingsRepository.autoWaiting
    val sortOrder: StateFlow<String> = settingsRepository.sortOrder
    val sortDirection: StateFlow<String> = settingsRepository.sortDirection
    val showPriorityBadge: StateFlow<Boolean> = settingsRepository.showPriorityBadge
    val showUrgencyBar: StateFlow<Boolean> = settingsRepository.showUrgencyBar
    val taskContexts: StateFlow<List<TaskContext>> = settingsRepository.taskContexts
    val activeTaskContextId: StateFlow<String?> = settingsRepository.activeTaskContextId

    fun setSortOrder(order: String) {
        settingsRepository.setSortOrder(order)
    }

    fun setSortDirection(direction: String) {
        settingsRepository.setSortDirection(direction)
    }

    fun saveTaskContext(context: TaskContext) {
        settingsRepository.saveTaskContext(context)
    }

    fun replaceTaskContexts(contexts: List<TaskContext>) {
        settingsRepository.replaceTaskContexts(contexts)
    }

    fun deleteTaskContext(id: String) {
        settingsRepository.deleteTaskContext(id)
    }

    fun setActiveTaskContextId(id: String?) {
        settingsRepository.setActiveTaskContextId(id)
    }

    private val databasePath = File(baseDir, "taskchampion").absolutePath
    private val _recurrenceDiagnostics = MutableStateFlow<List<uniffi.taskchampion_android.RecurrenceDiagnostic>>(emptyList())
    val recurrenceDiagnostics = _recurrenceDiagnostics.asStateFlow()

    private var snapshotSequence = 0L // accessed under the database coordinator
    private var publishedSequence = 0L
    private val publicationLock = Any()
    private val _syncTransportError = MutableStateFlow<String?>(null)
    val syncTransportError = _syncTransportError.asStateFlow()

    private data class Snapshot(
        val sequence: Long,
        val tasks: List<Task>,
        val diagnostics: List<uniffi.taskchampion_android.RecurrenceDiagnostic>,
    )

    private fun readUnlocked(r: ReplicaWrapper) =
        Snapshot(
            ++snapshotSequence, r.allTaskData().map { it.toModel() }, r.recurrenceDiagnostics(),
        )

    private suspend fun <T> database(block: suspend () -> T): T {
        val (result, snapshot) =
            TaskDatabaseCoordinator.withDatabase(databasePath) {
                val result = block()
                result to readUnlocked(requireReplica())
            }
        // The coordinator has finished native work and checked caller cancellation.
        synchronized(publicationLock) {
            if (snapshot.sequence > publishedSequence) {
                publishedSequence = snapshot.sequence
                _recurrenceDiagnostics.value = snapshot.diagnostics
                _tasks.value = snapshot.tasks
            }
        }
        return result
    }

    private fun requireReplica(): ReplicaWrapper = replica ?: error("Replica not initialized")

    suspend fun init() =
        database {
            if (replica == null) replica = ReplicaWrapper.newOnDisk(databasePath)
        }

    suspend fun loadTasks() = database { Unit }

    override suspend fun refresh() = init()

    private fun notifyWidgetRefresh() = WidgetRefreshReceiver.sendRefresh(context)

    private suspend fun <T> mutate(
        sync: Boolean = true,
        block: (ReplicaWrapper) -> T,
    ): T {
        val result =
            database {
                val r = requireReplica()
                val result = block(r)
                if (sync) triggerSyncUnlocked(r)
                result
            }
        notifyWidgetRefresh()
        return result
    }

    suspend fun addTask(
        description: String,
        project: String?,
        tags: List<String>,
        wait: String?,
        due: String?,
        scheduled: String?,
        start: String? = null,
        priority: String? = null,
        dependencies: List<String> = emptyList(),
    ): Task =
        mutate { r ->
            r.addTask(
                TaskAddProps(
                    description = description,
                    project =
                        resolveProject(
                            project,
                            settingsRepository.defaultProjectEnabled.value,
                            settingsRepository.defaultProject.value,
                        ),
                    tags = tags, wait = wait, due = due, scheduled = scheduled, start = start,
                    priority = priority, dependencies = dependencies,
                ),
            ).toModel()
        }

    suspend fun addRecurringTask(
        props: TaskAddProps,
        rule: com.brokenpip3.fatto.data.model.RecurrenceRule,
    ): Task =
        mutate { r ->
            val resolved =
                props.copy(
                    project =
                        resolveProject(
                            props.project, settingsRepository.defaultProjectEnabled.value, settingsRepository.defaultProject.value,
                        ),
                )
            r.addRecurringTask(resolved, rule.toNative()).toModel()
        }

    suspend fun convertTaskToRecurring(
        patch: uniffi.taskchampion_android.TaskEditPatch,
        rule: com.brokenpip3.fatto.data.model.RecurrenceRule,
    ) = mutate { it.convertTaskToRecurring(patch, rule.toNative()) }

    suspend fun updateRecurrenceTemplate(
        patch: uniffi.taskchampion_android.TaskEditPatch,
        rule: uniffi.taskchampion_android.RecurrenceRulePatch,
    ) = mutate { it.updateRecurrenceTemplate(patch, rule) }

    suspend fun replaceRecurrence(
        patch: uniffi.taskchampion_android.TaskEditPatch,
        rule: uniffi.taskchampion_android.RecurrenceRulePatch,
    ): Task = mutate { it.replaceRecurrence(patch.uuid, patch, rule).toModel() }

    suspend fun stopRecurrence(uuid: String) = mutate { it.stopRecurrence(uuid) }

    suspend fun updateTask(patch: uniffi.taskchampion_android.TaskEditPatch) = mutate { it.updateTask(patch) }

    override suspend fun markReviewed(uuid: String) = mutate { it.markReviewed(uuid) }

    override suspend fun saveReviewEdit(patch: uniffi.taskchampion_android.TaskEditPatch) = mutate { it.updateTaskForReview(patch) }

    override suspend fun saveReviewDependencies(
        reviewedUuid: String,
        patch: uniffi.taskchampion_android.TaskEditPatch,
    ) = mutate { it.updateDependenciesForReview(reviewedUuid, patch) }

    override suspend fun complete(uuid: String) =
        mutate { r ->
            requireReviewPending(r, uuid)
            r.transitionOccurrence(uuid, TaskStatus.Completed, settingsRepository.autoStopActiveOnComplete.value)
        }

    override suspend fun delete(uuid: String) =
        mutate { r ->
            requireReviewPending(r, uuid)
            r.transitionOccurrence(uuid, TaskStatus.Deleted, false)
        }

    override suspend fun addReviewAnnotation(
        uuid: String,
        description: String,
    ): Annotation =
        mutate {
            val annotation = it.addAnnotationForReview(uuid, description)
            Annotation(annotation.entry, annotation.description)
        }

    override suspend fun removeReviewAnnotation(
        uuid: String,
        entry: String,
    ) = mutate { it.removeAnnotationForReview(uuid, entry) }

    suspend fun addDependencies(
        uuid: String,
        depUuids: List<String>,
    ) = mutate { r ->
        val current = r.getTask(uuid)?.toModel() ?: error("Task not found")
        r.updateTask(current.copy(dependencies = (current.dependencies + depUuids).distinct()).toEditPatch(current))
    }

    suspend fun removeDependency(
        uuid: String,
        depUuid: String,
    ) = mutate { r ->
        val current = r.getTask(uuid)?.toModel() ?: error("Task not found")
        r.updateTask(current.copy(dependencies = current.dependencies - depUuid).toEditPatch(current))
    }

    suspend fun completeTask(
        uuid: String,
        sync: Boolean = true,
    ) = mutate(sync) {
        it.transitionOccurrence(uuid, TaskStatus.Completed, settingsRepository.autoStopActiveOnComplete.value)
    }

    suspend fun restoreTask(uuid: String) = mutate { it.transitionOccurrence(uuid, TaskStatus.Pending, false) }

    suspend fun deleteTask(uuid: String) = mutate { it.transitionOccurrence(uuid, TaskStatus.Deleted, false) }

    suspend fun addAnnotation(
        uuid: String,
        description: String,
    ): Annotation =
        mutate {
            val ann = it.addAnnotation(uuid, description)
            Annotation(ann.entry, ann.description)
        }

    suspend fun removeAnnotation(
        uuid: String,
        entry: String,
    ) = mutate { it.removeAnnotation(uuid, entry) }

    suspend fun sync() {
        database {
            check(settingsRepository.hasCredentials()) { "No sync credentials configured" }
            Syncer.sync(requireReplica(), settingsRepository)
        }
        notifyWidgetRefresh()
    }

    private fun triggerSyncUnlocked(r: ReplicaWrapper) {
        if (!settingsRepository.hasCredentials()) return
        try {
            Syncer.sync(r, settingsRepository)
            _syncTransportError.value = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Local changes remain committed. Recurrence diagnostics are refreshed
            // from storage independently of this transport failure.
            _syncTransportError.value = "Local changes saved; sync failed: ${safeSyncError(e)}"
            Log.e("TaskRepository", _syncTransportError.value!!)
        }
    }

    fun safeSyncError(error: Throwable): String {
        val serverCredentials = settingsRepository.getCredentials()
        val s3Credentials = settingsRepository.getS3Credentials()
        val sensitiveValues =
            setOfNotNull(
                serverCredentials?.clientId, serverCredentials?.secret,
                s3Credentials?.accessKeyId, s3Credentials?.secretAccessKey, s3Credentials?.secret,
            )
        return SyncDiagnosticsFormatter.safeError(error, sensitiveValues)
    }
}

private fun requireReviewPending(
    r: ReplicaWrapper,
    uuid: String,
) {
    check(r.getTask(uuid)?.toModel()?.let(ReviewPolicy::isPending) == true) { "Task changed elsewhere; reload the review" }
}
