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
import com.brokenpip3.fatto.data.model.isSynthetic
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

private data class TaskListFilterState(
    val project: String?,
    val context: TaskContext?,
    val showOnlyActive: Boolean,
    val sort: SortOrder,
    val direction: SortDirection,
)

class TaskViewModel(private val repository: TaskRepository) : ViewModel() {
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
            combine(_sortOrder, _sortDirection) { order, dir -> order to dir },
        ) { project, context, showOnlyActive, (sort, direction) ->
            TaskListFilterState(project, context, showOnlyActive, sort, direction)
        }

    private val baseFilteredTasks: StateFlow<List<Task>> =
        combine(
            repository.tasks,
            _searchQuery,
            _selectedTags,
            uuidFilter,
            taskListFilterState,
        ) { tasks, query, tags, uuidFilter, filters ->
            val parsed = com.brokenpip3.fatto.data.SearchParser.parse(query)
            val effectiveProject = parsed.project ?: filters.project
            val effectiveTags = if (parsed.tags.isNotEmpty()) parsed.tags else tags
            val searchFilter = parsed.description
            val contextExpression = TaskContextMatcher.parseExpression(filters.context).getOrNull()
            val now = Instant.now()

            tasks.filter { task ->
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
                            task.status == TaskStatus.PENDING &&
                                (task.start != null || task.tags.any { it.equals("ACTIVE", ignoreCase = true) })
                        )
                matchesContext && matchesUuid && matchesQuery && matchesTags && matchesProject && matchesActiveState
            }.sortedWith { a, b ->
                val result =
                    when (filters.sort) {
                        SortOrder.DATE_CREATED -> (a.entry ?: "").compareTo(b.entry ?: "")
                        SortOrder.DUE_DATE -> {
                            val dueA = a.due ?: "9999"
                            val dueB = b.due ?: "9999"
                            dueA.compareTo(dueB)
                        }
                        SortOrder.PRIORITY -> {
                            val pA =
                                when (a.priority) {
                                    "H" -> 0
                                    "M" -> 1
                                    "L" -> 2
                                    else -> 3
                                }
                            val pB =
                                when (b.priority) {
                                    "H" -> 0
                                    "M" -> 1
                                    "L" -> 2
                                    else -> 3
                                }
                            pA.compareTo(pB)
                        }
                        SortOrder.URGENCY -> a.urgency.compareTo(b.urgency)
                        SortOrder.ALPHABETICAL -> a.description.lowercase().compareTo(b.description.lowercase())
                        SortOrder.SCHEDULED_DATE -> {
                            val schA = a.scheduled ?: "9999"
                            val schB = b.scheduled ?: "9999"
                            schA.compareTo(schB)
                        }
                    }
                if (filters.direction == SortDirection.DESCENDING) -result else result
            }
        }.combine(repository.showCompleted) { tasks, showCompleted ->
            if (showCompleted) tasks else tasks.filter { it.status != TaskStatus.COMPLETED }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeTasks: StateFlow<List<Task>> =
        combine(
            baseFilteredTasks,
            repository.tasks,
            repository.hideBlockedTasksWaiting,
            MutableStateFlow(Instant.now()),
        ) { tasks, allTasks, hideBlocked, now ->
            tasks.filter { task ->
                if (task.status != TaskStatus.PENDING) return@filter false
                if (DateTimeUtils.parseToInstant(task.wait)?.isAfter(now) == true) return@filter false

                if (hideBlocked && task.isBlocked) {
                    val blockingDeps =
                        task.dependencies.mapNotNull { depUuid ->
                            allTasks.find { it.uuid == depUuid }
                        }.filter { it.status != TaskStatus.COMPLETED }

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
            MutableStateFlow(Instant.now()),
        ) { tasks, showWaiting, now ->
            if (!showWaiting) return@combine emptyList()
            tasks.filter { task ->
                task.status == TaskStatus.PENDING && DateTimeUtils.parseToInstant(task.wait)?.isAfter(now) == true
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedTasks: StateFlow<List<Task>> =
        baseFilteredTasks
            .combine(MutableStateFlow(Unit)) { tasks, _ ->
                tasks.filter { it.status == TaskStatus.COMPLETED }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val showInternalTags: StateFlow<Boolean> = repository.showInternalTags
    val showPriorityBadge: StateFlow<Boolean> = repository.showPriorityBadge
    val showUrgencyBar: StateFlow<Boolean> = repository.showUrgencyBar
    val allTasks: StateFlow<List<Task>> = repository.tasks
    val maxUrgency: StateFlow<Float> =
        repository.tasks
            .map { tasks -> tasks.maxOfOrNull { it.urgency } ?: 0.0f }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0f)

    val availableTags: StateFlow<Set<String>> =
        repository.tasks
            .combine(repository.showInternalTags) { tasks: List<Task>, showInternal: Boolean ->
                tasks.filter { it.status == TaskStatus.PENDING }
                    .flatMap { it.tags }
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
                    val pendingCount = relatedTasks.count { it.status == TaskStatus.PENDING }
                    val completedCount = relatedTasks.count { it.status == TaskStatus.COMPLETED }

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
        repository.tasks
            .combine(repository.showInternalTags) { tasks: List<Task>, showInternal: Boolean ->
                tasks.filter { it.status == TaskStatus.PENDING }
                    .flatMap { it.tags }
                    .filter { tag -> !isSynthetic(tag) && (showInternal || !INTERNAL_TAGS.contains(tag.uppercase())) }
                    .groupingBy { it }
                    .eachCount()
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val tagsPerLine: StateFlow<Int> = repository.tagsPerLine

    val tasksByDate: StateFlow<Map<java.time.LocalDate, List<Task>>> =
        repository.tasks
            .combine(MutableStateFlow(Unit)) { tasks, _ ->
                val map = mutableMapOf<java.time.LocalDate, MutableList<Task>>()
                tasks.forEach { task ->
                    if (task.status == TaskStatus.COMPLETED) return@forEach

                    val dates =
                        listOfNotNull(task.due, task.scheduled).mapNotNull { dateStr ->
                            com.brokenpip3.fatto.data.DateTimeUtils.parseToLocalDate(dateStr)
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
    ) {
        viewModelScope.launch {
            try {
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
                _uiEvent.emit("Task created")
            } catch (e: Exception) {
                _uiEvent.emit("Failed to add task: ${e.message}")
            }
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            try {
                repository.updateTask(
                    task.copy(
                        wait = autoWait(wait = task.wait, due = task.due, scheduled = task.scheduled),
                    ),
                )
            } catch (e: Exception) {
                _uiEvent.emit("Failed to update task: ${e.message}")
            }
        }
    }

    fun addDependencies(
        uuid: String,
        deps: List<String>,
    ) {
        viewModelScope.launch {
            try {
                repository.addDependencies(uuid, deps)
            } catch (e: Exception) {
                _uiEvent.emit("Failed to update task: ${e.message}")
            }
        }
    }

    fun removeDependency(
        uuid: String,
        depUuid: String,
    ) {
        viewModelScope.launch {
            try {
                repository.removeDependency(uuid, depUuid)
            } catch (e: Exception) {
                _uiEvent.emit("Failed to update task: ${e.message}")
            }
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
            } catch (e: Exception) {
                _uiEvent.emit("Failed to complete task: ${e.message}")
            }
        }
    }

    fun deleteTask(uuid: String) {
        viewModelScope.launch {
            try {
                repository.deleteTask(uuid)
            } catch (e: Exception) {
                _uiEvent.emit("Failed to delete task: ${e.message}")
            }
        }
    }

    suspend fun addAnnotation(
        uuid: String,
        description: String,
    ): Annotation = repository.addAnnotation(uuid, description)

    fun removeAnnotation(
        uuid: String,
        entry: String,
    ) {
        viewModelScope.launch {
            try {
                repository.removeAnnotation(uuid, entry)
            } catch (e: Exception) {
                _uiEvent.emit("Failed to remove annotation: ${e.message}")
            }
        }
    }

    fun toggleTaskActive(task: Task) {
        viewModelScope.launch {
            try {
                val updatedTask =
                    task.copy(
                        start = if (task.start == null) Instant.now().truncatedTo(ChronoUnit.SECONDS).toString() else null,
                    )
                repository.updateTask(updatedTask)
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
            } catch (e: Exception) {
                _uiEvent.emit("Sync failed: ${e.message}")
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
