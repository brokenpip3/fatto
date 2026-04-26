package com.brokenpip3.fatto.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brokenpip3.fatto.data.TaskRepository
import com.brokenpip3.fatto.data.model.INTERNAL_TAGS
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.isSynthetic
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
}

data class ProjectNode(
    val name: String,
    val fullName: String,
    val count: Int,
    val completedCount: Int,
    val totalCount: Int,
    val level: Int,
)

data class Breadcrumb(val name: String, val fullPath: String?)

class TaskViewModel(private val repository: TaskRepository) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedTags = MutableStateFlow<Set<String>>(emptySet())
    val selectedTags = _selectedTags.asStateFlow()

    private val _activeProject = MutableStateFlow<String?>(null)
    val activeProject = _activeProject.asStateFlow()

    private val _currentProjectPath = MutableStateFlow<String?>(null)
    val currentProjectPath = _currentProjectPath.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.DATE_CREATED)
    val sortOrder = _sortOrder.asStateFlow()

    private val baseFilteredTasks: StateFlow<List<Task>> =
        combine(
            repository.tasks,
            _searchQuery,
            _selectedTags,
            _activeProject,
            _sortOrder,
        ) { tasks, query, tags, project, sort ->
            val parsed = com.brokenpip3.fatto.data.SearchParser.parse(query)
            val effectiveProject = parsed.project ?: project
            val effectiveTags = if (parsed.tags.isNotEmpty()) parsed.tags else tags
            val searchFilter = parsed.description

            tasks.filter { task ->
                val matchesUuid = parsed.uuid == null || task.uuid == parsed.uuid
                val matchesQuery = task.description.contains(searchFilter, ignoreCase = true)
                val matchesTags = effectiveTags.isEmpty() || task.tags.intersect(effectiveTags).isNotEmpty()
                val matchesProject =
                    effectiveProject == null ||
                        task.project == effectiveProject ||
                        task.project?.startsWith("$effectiveProject.") == true
                matchesUuid && matchesQuery && matchesTags && matchesProject
            }.sortedWith { a, b ->
                when (sort) {
                    SortOrder.DATE_CREATED -> (b.entry ?: "").compareTo(a.entry ?: "")
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
                    SortOrder.URGENCY -> b.urgency.compareTo(a.urgency)
                    SortOrder.ALPHABETICAL -> a.description.lowercase().compareTo(b.description.lowercase())
                    SortOrder.SCHEDULED_DATE -> {
                        val schA = a.scheduled ?: "9999"
                        val schB = b.scheduled ?: "9999"
                        schA.compareTo(schB)
                    }
                }
            }
        }.combine(repository.showCompleted) { tasks, showCompleted ->
            if (showCompleted) tasks else tasks.filter { it.status != TaskStatus.COMPLETED }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeTasks: StateFlow<List<Task>> =
        baseFilteredTasks
            .combine(MutableStateFlow(Instant.now())) { tasks, now ->
                tasks.filter { task ->
                    task.status == TaskStatus.PENDING && (task.wait == null || Instant.parse(task.wait).isBefore(now))
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val waitingTasks: StateFlow<List<Task>> =
        baseFilteredTasks
            .combine(MutableStateFlow(Instant.now())) { tasks, now ->
                tasks.filter { task ->
                    task.status == TaskStatus.PENDING && task.wait != null && Instant.parse(task.wait).isAfter(now)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedTasks: StateFlow<List<Task>> =
        baseFilteredTasks
            .combine(MutableStateFlow(Unit)) { tasks, _ ->
                tasks.filter { it.status == TaskStatus.COMPLETED }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val showInternalTags: StateFlow<Boolean> = repository.showInternalTags

    val availableTags: StateFlow<Set<String>> =
        repository.tasks
            .combine(repository.showInternalTags) { tasks: List<Task>, showInternal: Boolean ->
                tasks.flatMap { it.tags }
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
                            try {
                                java.time.LocalDate.parse(dateStr.take(10))
                            } catch (e: Exception) {
                                null
                            }
                        }.distinct()

                    dates.forEach { date ->
                        map.getOrPut(date) { mutableListOf() }.add(task)
                    }
                }
                map
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private val _syncEvent = MutableSharedFlow<String>()
    val syncEvent = _syncEvent.asSharedFlow()

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

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
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
                repository.addTask(description, project, tags, wait, due, scheduled, start, priority, dependencies)
            } catch (e: Exception) {
                _syncEvent.emit("Failed to add task: ${e.message}")
            }
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            try {
                repository.updateTask(task)
            } catch (e: Exception) {
                _syncEvent.emit("Failed to update task: ${e.message}")
            }
        }
    }

    fun completeTask(uuid: String) {
        viewModelScope.launch {
            try {
                repository.completeTask(uuid)
            } catch (e: Exception) {
                _syncEvent.emit("Failed to complete task: ${e.message}")
            }
        }
    }

    fun deleteTask(uuid: String) {
        viewModelScope.launch {
            try {
                repository.deleteTask(uuid)
            } catch (e: Exception) {
                _syncEvent.emit("Failed to delete task: ${e.message}")
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
                _syncEvent.emit("Failed to toggle task active state: ${e.message}")
            }
        }
    }

    fun sync() {
        if (_isSyncing.value) return

        viewModelScope.launch {
            _isSyncing.value = true
            try {
                repository.sync()
                _syncEvent.emit("Sync successful")
            } catch (e: Exception) {
                _syncEvent.emit("Sync failed: ${e.message}")
            } finally {
                _isSyncing.value = false
            }
        }
    }
}
