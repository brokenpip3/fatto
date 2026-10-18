package com.brokenpip3.fatto.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brokenpip3.fatto.data.DateTimeUtils
import com.brokenpip3.fatto.data.TaskContextMatcher
import com.brokenpip3.fatto.data.TaskRepository
import com.brokenpip3.fatto.data.model.Annotation
import com.brokenpip3.fatto.data.model.INTERNAL_TAGS
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.TaskContext
import com.brokenpip3.fatto.data.model.TaskEditResult
import com.brokenpip3.fatto.data.model.dateValue
import com.brokenpip3.fatto.data.model.isSynthetic
import com.brokenpip3.fatto.data.model.toEditPatch
import com.brokenpip3.fatto.data.model.withResolvedWait
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uniffi.taskchampion_android.TaskEditPatch
import uniffi.taskchampion_android.TaskException
import uniffi.taskchampion_android.TaskStatus
import java.time.Instant
import java.time.temporal.ChronoUnit

enum class SortOrder {
    DATE_CREATED,
    DUE_DATE,
    PRIORITY,
    URGENCY,
    ALPHABETICAL,
    SCHEDULED_DATE,
    ;

    val defaultDirection: SortDirection
        get() =
            when (this) {
                DATE_CREATED -> SortDirection.DESCENDING
                URGENCY -> SortDirection.DESCENDING
                else -> SortDirection.ASCENDING
            }
}

enum class SortDirection { ASCENDING, DESCENDING }

data class ProjectNode(
    val name: String,
    val fullName: String,
    val count: Int,
    val completedCount: Int,
    val totalCount: Int,
    val level: Int,
)

data class Breadcrumb(val name: String, val fullPath: String?)

data class TaskCreatedEvent(val task: Task, val openEditor: Boolean)

private data class TaskListFilterState(
    val project: String?,
    val context: TaskContext?,
    val showOnlyActive: Boolean,
)

class TaskViewModel(
    private val repository: TaskRepository,
    private val timeSource: com.brokenpip3.fatto.data.TaskTimeSource = com.brokenpip3.fatto.data.SystemTaskTimeSource(),
) : ViewModel() {
    val taskTime =
        timeSource.snapshots.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000),
            com.brokenpip3.fatto.data.TaskTimeSnapshot(Instant.now(), java.time.ZoneId.systemDefault()),
        )

    fun refreshTaskTime() = timeSource.refresh()

    private val _searchQuery = MutableStateFlow("")
    private val uuidFilter = MutableStateFlow<Set<String>>(emptySet())
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedTags = MutableStateFlow<Set<String>>(emptySet())
    val selectedTags = _selectedTags.asStateFlow()

    private val _activeProject = MutableStateFlow<String?>(null)
    val activeProject = _activeProject.asStateFlow()

    private val _showOnlyActiveTasks = MutableStateFlow(false)
    val showOnlyActiveTasks = _showOnlyActiveTasks.asStateFlow()

    private val _currentProjectPath = MutableStateFlow<String?>(null)
    val currentProjectPath = _currentProjectPath.asStateFlow()

    private val _sortOrder =
        MutableStateFlow(
            try {
                SortOrder.valueOf(repository.sortOrder.value)
            } catch (_: IllegalArgumentException) {
                SortOrder.DATE_CREATED
            },
        )
    val sortOrder = _sortOrder.asStateFlow()

    private val _sortDirection =
        MutableStateFlow(
            try {
                SortDirection.valueOf(repository.sortDirection.value)
            } catch (_: IllegalArgumentException) {
                _sortOrder.value.defaultDirection
            },
        )
    val sortDirection = _sortDirection.asStateFlow()

    val taskContexts: StateFlow<List<TaskContext>> = repository.taskContexts
    val activeTaskContextId: StateFlow<String?> = repository.activeTaskContextId
    val activeTaskContext: StateFlow<TaskContext?> =
        combine(taskContexts, activeTaskContextId) { contexts, activeId ->
            contexts.firstOrNull { it.id == activeId }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeTaskContextError: StateFlow<String?> =
        activeTaskContext
            .map { context -> context?.let { TaskContextMatcher.parseError(it) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val taskListFilterState =
        combine(
            _activeProject,
            activeTaskContext,
            _showOnlyActiveTasks,
        ) { project, context, showOnlyActive ->
            TaskListFilterState(project, context, showOnlyActive)
        }

    private val baseFilteredTasks: StateFlow<List<Task>> =
        combine(
            repository.tasks,
            _searchQuery,
            _selectedTags,
            uuidFilter,
            taskListFilterState.combine(taskTime) { filters, time -> filters to time },
        ) { tasks, query, tags, uuidFilter, (filters, time) ->
            val parsed = com.brokenpip3.fatto.data.SearchParser.parse(query)
            val effectiveProject = parsed.project ?: filters.project
            val effectiveTags = if (parsed.tags.isNotEmpty()) parsed.tags else tags
            val searchFilter = parsed.description
            val contextExpression = TaskContextMatcher.parseExpression(filters.context).getOrNull()
            val now = time.now

            tasks.map { task -> task.withCurrentWaitingTag(now) }.filter { task ->
                val matchesContext = contextExpression?.let { TaskContextMatcher.matches(task, it, now) } ?: false
                val matchesUuid =
                    (parsed.uuid == null || task.uuid == parsed.uuid) &&
                        (uuidFilter.isEmpty() || task.uuid in uuidFilter)
                val matchesQuery = task.description.contains(searchFilter, ignoreCase = true)
                val matchesTags = effectiveTags.isEmpty() || task.tags.intersect(effectiveTags).isNotEmpty()
                val matchesProject =
                    effectiveProject == null ||
                        task.project == effectiveProject ||
                        task.project?.startsWith("$effectiveProject.") == true
                val matchesActiveState =
                    !filters.showOnlyActive ||
                        (
                            task.status == TaskStatus.Pending &&
                                (task.start != null || task.tags.any { it.equals("ACTIVE", ignoreCase = true) })
                        )
                matchesContext && matchesUuid && matchesQuery && matchesTags && matchesProject && matchesActiveState
            }
        }.distinctUntilChanged()
            .combine(combine(_sortOrder, _sortDirection) { order, direction -> order to direction }) { tasks, (order, direction) ->
                tasks.sortedWith(taskComparator(order, direction))
            }.combine(repository.showCompleted) { tasks, showCompleted ->
                if (showCompleted) tasks else tasks.filter { it.status != TaskStatus.Completed }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeTasks: StateFlow<List<Task>> =
        combine(
            baseFilteredTasks,
            repository.tasks,
            repository.hideBlockedTasksWaiting,
            taskTime,
        ) { tasks, allTasks, hideBlocked, time ->
            val now = time.now
            tasks.filter { task ->
                if (task.status != TaskStatus.Pending || !task.isActionableStatus) return@filter false
                if (DateTimeUtils.parseToInstant(task.wait)?.isAfter(now) == true) return@filter false

                if (hideBlocked && task.isBlocked) {
                    val blockingDeps =
                        task.dependencies.mapNotNull { depUuid ->
                            allTasks.find { it.uuid == depUuid }
                        }.filter { it.status != TaskStatus.Completed }

                    if (blockingDeps.isNotEmpty()) {
                        val hasDepsWait = blockingDeps.all { DateTimeUtils.parseToInstant(it.wait)?.isAfter(now) == true }
                        if (hasDepsWait) return@filter false
                    }
                }

                true
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val waitingTasks: StateFlow<List<Task>> =
        combine(
            baseFilteredTasks,
            repository.showWaitingTasks,
            taskTime,
        ) { tasks, showWaiting, time ->
            val now = time.now
            if (!showWaiting) return@combine emptyList()
            tasks.filter { task ->
                task.status == TaskStatus.Pending &&
                    task.isActionableStatus &&
                    DateTimeUtils.parseToInstant(task.wait)?.isAfter(now) == true
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedTasks: StateFlow<List<Task>> =
        baseFilteredTasks
            .combine(MutableStateFlow(Unit)) { tasks, _ ->
                tasks.filter { it.status == TaskStatus.Completed && !it.isRecurrenceTemplate }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val showInternalTags: StateFlow<Boolean> = repository.showInternalTags
    val showPriorityBadge: StateFlow<Boolean> = repository.showPriorityBadge
    val showUrgencyBar: StateFlow<Boolean> = repository.showUrgencyBar
    val allTasks: StateFlow<List<Task>> = repository.tasks
    val recurrenceDiagnostics = repository.recurrenceDiagnostics
    val syncTransportError = repository.syncTransportError
    val autoWaiting = repository.autoWaiting
    val recurringTemplates: StateFlow<List<Task>> =
        repository.tasks
            .map { tasks -> tasks.filter { it.status == TaskStatus.Recurring } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val maxUrgency: StateFlow<Float> =
        repository.tasks
            .map { tasks -> tasks.maxOfOrNull { it.urgency } ?: 0.0f }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0f)

    val availableTags: StateFlow<Set<String>> =
        combine(repository.tasks, repository.showInternalTags, taskTime) { tasks, showInternal, time ->
            tasks.filter { it.status == TaskStatus.Pending }
                .flatMap { it.withCurrentWaitingTag(time.now).tags }
                .filter { tag -> !isSynthetic(tag) && (showInternal || !INTERNAL_TAGS.contains(tag.uppercase())) }
                .toSet()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val hierarchicalProjects: StateFlow<List<ProjectNode>> =
        repository.tasks
            .combine(MutableStateFlow(Unit)) { tasks, _ ->
                val allProjects = tasks.filter { it.project != null }

                val allProjectNames =
                    allProjects.flatMap { task ->
                        val parts = task.project!!.split('.')
                        List(parts.size) { i -> parts.subList(0, i + 1).joinToString(".") }
                    }.toSet()

                allProjectNames.map { fullName ->
                    val relatedTasks = allProjects.filter { it.project == fullName || it.project?.startsWith("$fullName.") == true }
                    val pendingCount = relatedTasks.count { it.status == TaskStatus.Pending }
                    val completedCount = relatedTasks.count { it.status == TaskStatus.Completed }

                    ProjectNode(
                        name = fullName.split('.').last(),
                        fullName = fullName,
                        count = pendingCount,
                        completedCount = completedCount,
                        totalCount = pendingCount + completedCount,
                        level = fullName.count { it == '.' },
                    )
                }.sortedBy { it.fullName }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectableProjects: StateFlow<List<String>> =
        combine(hierarchicalProjects, repository.showEmptyProjects) { nodes, showEmpty ->
            selectableProjectNames(nodes, showEmpty)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredProjectNodes: StateFlow<List<ProjectNode>> =
        combine(
            hierarchicalProjects,
            _currentProjectPath,
            repository.showEmptyProjects,
        ) { allProjects, currentPath, showEmpty ->
            allProjects.filter { node ->
                val matchesPath =
                    if (currentPath == null) {
                        node.level == 0
                    } else {
                        node.fullName.startsWith("$currentPath.") && node.level == currentPath.count { it == '.' } + 1
                    }
                matchesPath && (showEmpty || node.count > 0)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val breadcrumbs: StateFlow<List<Breadcrumb>> =
        _currentProjectPath.map { path ->
            val crumbs = mutableListOf(Breadcrumb("Home", null))
            if (path != null) {
                val parts = path.split('.')
                var current = ""
                parts.forEach { part ->
                    current = if (current.isEmpty()) part else "$current.$part"
                    crumbs.add(Breadcrumb(part, current))
                }
            }
            crumbs
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(Breadcrumb("Home", null)))

    val tagCounts: StateFlow<Map<String, Int>> =
        combine(repository.tasks, repository.showInternalTags, taskTime) { tasks, showInternal, time ->
            tasks.filter { it.status == TaskStatus.Pending }
                .flatMap { it.withCurrentWaitingTag(time.now).tags }
                .filter { tag -> !isSynthetic(tag) && (showInternal || !INTERNAL_TAGS.contains(tag.uppercase())) }
                .groupingBy { it }
                .eachCount()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val tagsPerLine: StateFlow<Int> = repository.tagsPerLine

    val tasksByDate: StateFlow<Map<java.time.LocalDate, List<Task>>> =
        repository.tasks
            .combine(taskTime) { tasks, time ->
                val map = mutableMapOf<java.time.LocalDate, MutableList<Task>>()
                tasks.forEach { task ->
                    if (task.status != TaskStatus.Pending) return@forEach

                    val dates =
                        listOf("due", "scheduled").mapNotNull { field ->
                            com.brokenpip3.fatto.data.TaskDateTime.localDate(task.dateValue(field), time.zone)
                        }.distinct()

                    dates.forEach { date ->
                        map.getOrPut(date) { mutableListOf() }.add(task)
                    }
                }
                map
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val firstDayOfWeek: StateFlow<Int> = repository.firstDayOfWeek

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private var lastSyncTime: Long = 0L
    private val syncCooldownMs = 30_000L

    private val _syncStatusMessage = MutableStateFlow<String?>(null)
    val syncStatusMessage: StateFlow<String?> = _syncStatusMessage.asStateFlow()

    private val _uiEvent = MutableSharedFlow<String>()
    val uiEvent = _uiEvent.asSharedFlow()

    private val _taskCreatedEvent = MutableSharedFlow<TaskCreatedEvent>()
    val taskCreatedEvent = _taskCreatedEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.init()
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun toggleTag(tag: String) {
        _selectedTags.value =
            if (_selectedTags.value.contains(tag)) {
                _selectedTags.value - tag
            } else {
                _selectedTags.value + tag
            }
    }

    fun clearTags() {
        _selectedTags.value = emptySet()
    }

    fun setActiveProject(project: String?) {
        _activeProject.value = if (_activeProject.value == project) null else project
    }

    fun navigateToProject(path: String?) {
        _currentProjectPath.value = path
    }

    fun navigateUp() {
        val current = _currentProjectPath.value ?: return
        val parts = current.split('.')
        _currentProjectPath.value = if (parts.size == 1) null else parts.dropLast(1).joinToString(".")
    }

    fun clearProject() {
        _activeProject.value = null
    }

    fun clearFilters() {
        _searchQuery.value = ""
        _selectedTags.value = emptySet()
        _activeProject.value = null
    }

    fun toggleShowOnlyActiveTasks() {
        _showOnlyActiveTasks.value = !_showOnlyActiveTasks.value
    }

    fun saveTaskContext(context: TaskContext) {
        repository.saveTaskContext(context)
    }

    fun deleteTaskContext(id: String) {
        repository.deleteTaskContext(id)
    }

    fun setActiveTaskContextId(id: String?) {
        repository.setActiveTaskContextId(id)
    }

    fun setSortOrder(order: SortOrder) {
        if (_sortOrder.value == order) {
            val newDirection = if (_sortDirection.value == SortDirection.ASCENDING) SortDirection.DESCENDING else SortDirection.ASCENDING
            _sortDirection.value = newDirection
            repository.setSortDirection(newDirection.name)
        } else {
            _sortOrder.value = order
            _sortDirection.value = order.defaultDirection
            repository.setSortOrder(order.name)
            repository.setSortDirection(order.defaultDirection.name)
        }
    }

    @Suppress("LongParameterList")
    fun addTask(
        description: String,
        project: String?,
        tags: List<String>,
        wait: String?,
        due: String?,
        scheduled: String?,
        start: String? = null,
        priority: String? = null,
        dependencies: List<String> = emptyList(),
        openEditor: Boolean = false,
    ) {
        viewModelScope.launch {
            try {
                val createdTask =
                    repository.addTask(
                        description,
                        project,
                        tags,
                        autoWait(wait = wait, due = due, scheduled = scheduled),
                        due,
                        scheduled,
                        start,
                        priority,
                        dependencies,
                    )
                _taskCreatedEvent.emit(TaskCreatedEvent(createdTask, openEditor))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiEvent.emit("Failed to add task: ${e.message}")
            }
        }
    }

    suspend fun saveTask(patch: TaskEditPatch): TaskEditResult =
        try {
            repository.updateTask(patch)
            TaskEditResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: TaskException.EditConflict) {
            try {
                repository.loadTasks()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            }
            TaskEditResult.Failure("Changed elsewhere: ${e.fields.joinToString()}. Reload to review the latest task.", e.fields)
        } catch (e: Exception) {
            TaskEditResult.Failure("Could not save task: ${e.message}")
        }

    private suspend fun checkedSeriesAction(
        errorPrefix: String = "Could not save series",
        action: suspend () -> Unit,
    ): TaskEditResult =
        try {
            action()
            TaskEditResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: TaskException.EditConflict) {
            try {
                repository.loadTasks()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            }
            TaskEditResult.Failure("Changed elsewhere: ${e.fields.joinToString()}. Reload to review the latest series.", e.fields)
        } catch (e: Exception) {
            TaskEditResult.Failure("$errorPrefix: ${e.message}")
        }

    suspend fun createTask(
        props: uniffi.taskchampion_android.TaskAddProps,
        rule: com.brokenpip3.fatto.data.model.RecurrenceRule?,
        openEditor: Boolean,
        explicitDateClears: Set<String> = emptySet(),
    ): TaskEditResult =
        checkedSeriesAction(errorPrefix = "Failed to add task") {
            val adjustedWait = if ("wait" in explicitDateClears) props.wait else autoWait(props.wait, props.due, props.scheduled)
            val adjusted = props.withResolvedWait(adjustedWait)
            val created =
                if (rule ==
                    null
                ) {
                    repository.addTask(adjusted)
                } else {
                    repository.addRecurringTask(adjusted.copy(start = null), rule)
                }
            // Snackbar consumers may remain suspended while another task is created.
            // Delivery must not hold the checked creation form open after commit.
            viewModelScope.launch { _taskCreatedEvent.emit(TaskCreatedEvent(created, openEditor)) }
        }

    suspend fun convertTaskToRecurring(
        patch: TaskEditPatch,
        rule: com.brokenpip3.fatto.data.model.RecurrenceRule,
    ): TaskEditResult = checkedSeriesAction { repository.convertTaskToRecurring(patch, rule) }

    suspend fun saveSeries(
        patch: TaskEditPatch,
        rule: uniffi.taskchampion_android.RecurrenceRulePatch,
        replace: Boolean,
    ): TaskEditResult =
        checkedSeriesAction {
            if (replace) repository.replaceRecurrence(patch, rule) else repository.updateRecurrenceTemplate(patch, rule)
        }

    suspend fun stopSeries(uuid: String): TaskEditResult = checkedSeriesAction { repository.stopRecurrence(uuid) }

    fun reloadTasks() {
        viewModelScope.launch {
            try {
                repository.init()
            } catch (
                e: CancellationException,
            ) {
                throw e
            } catch (e: Exception) {
                _uiEvent.emit("Could not reload tasks: ${e.message}")
            }
        }
    }

    suspend fun addDependencies(
        uuid: String,
        deps: List<String>,
    ) {
        try {
            repository.addDependencies(uuid, deps)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiEvent.emit("Failed to update task: ${e.message}")
            throw e
        }
    }

    suspend fun removeDependency(
        uuid: String,
        depUuid: String,
    ) {
        try {
            repository.removeDependency(uuid, depUuid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiEvent.emit("Failed to update task: ${e.message}")
            throw e
        }
    }

    private fun autoWait(
        wait: String?,
        due: String?,
        scheduled: String?,
    ): String? {
        val shouldApplyAutoWait = repository.autoWaiting.value && wait.isNullOrBlank()
        val target =
            if (shouldApplyAutoWait) {
                listOfNotNull(
                    DateTimeUtils.parseToInstant(due),
                    DateTimeUtils.parseToInstant(scheduled),
                ).minOrNull()
            } else {
                null
            }
        val candidate = target?.minus(AUTO_WAIT_LEAD_DAYS, ChronoUnit.DAYS)
        return if (candidate?.isAfter(Instant.now()) == true) candidate.toString() else wait
    }

    fun showTasksWithUuids(uuids: List<String>) {
        uuidFilter.value = uuids.toSet()
    }

    fun completeTask(uuid: String) {
        viewModelScope.launch {
            try {
                repository.completeTask(uuid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiEvent.emit("Failed to complete task: ${e.message}")
            }
        }
    }

    fun restoreTask(uuid: String) {
        viewModelScope.launch {
            try {
                repository.restoreTask(uuid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiEvent.emit("Failed to restore task: ${e.message}")
            }
        }
    }

    fun deleteTask(uuid: String) {
        viewModelScope.launch {
            try {
                repository.deleteTask(uuid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiEvent.emit("Failed to delete task: ${e.message}")
            }
        }
    }

    suspend fun addAnnotation(
        uuid: String,
        description: String,
    ): Annotation = repository.addAnnotation(uuid, description)

    suspend fun removeAnnotation(
        uuid: String,
        entry: String,
    ) {
        repository.removeAnnotation(uuid, entry)
    }

    fun toggleTaskActive(task: Task) {
        viewModelScope.launch {
            try {
                if (task.status != TaskStatus.Pending) return@launch
                val updatedTask =
                    task.copy(
                        start = if (task.start == null) Instant.now().truncatedTo(ChronoUnit.SECONDS).toString() else null,
                    )
                repository.updateTask(updatedTask.toEditPatch(task))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiEvent.emit("Failed to toggle task active state: ${e.message}")
            }
        }
    }

    fun sync() {
        if (_isSyncing.value) return

        val now = System.currentTimeMillis()
        val elapsed = now - lastSyncTime
        if (elapsed < syncCooldownMs) {
            val remainingSec = ((syncCooldownMs - elapsed) / 1000).toInt()
            val myMessage = "Synced recently, wait ${remainingSec}s"
            _syncStatusMessage.value = myMessage
            viewModelScope.launch {
                delay(3000)
                if (_syncStatusMessage.value == myMessage) {
                    _syncStatusMessage.value = null
                }
            }
            return
        }
        lastSyncTime = now

        viewModelScope.launch {
            _isSyncing.value = true
            _syncStatusMessage.value = "Syncing..."
            try {
                repository.sync()
                _uiEvent.emit("Sync successful")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiEvent.emit("Sync failed: ${repository.safeSyncError(e)}")
            } finally {
                _isSyncing.value = false
                _syncStatusMessage.value = null
            }
        }
    }

    private companion object {
        const val AUTO_WAIT_LEAD_DAYS = 7L
    }
}

/** Project the transient native WAITING tag from the same clock used by task sections. */
private fun Task.withCurrentWaitingTag(now: Instant): Task {
    val waiting = status == TaskStatus.Pending && DateTimeUtils.parseToInstant(wait)?.isAfter(now) == true
    if (waiting == ("WAITING" in tags)) return this
    return copy(tags = if (waiting) tags + "WAITING" else tags.filterNot { it == "WAITING" })
}
