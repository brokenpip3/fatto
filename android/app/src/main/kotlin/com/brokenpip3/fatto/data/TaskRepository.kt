package com.brokenpip3.fatto.data

import android.content.Context
import android.util.Log
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.toModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import uniffi.taskchampion_android.ReplicaWrapper
import uniffi.taskchampion_android.TaskAddProps
import uniffi.taskchampion_android.TaskStatus
import uniffi.taskchampion_android.TaskUpdateProps
import java.io.File

class TaskRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val baseDir: File = context.filesDir,
) {
    private var replica: ReplicaWrapper? = null
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()
    val showCompleted: StateFlow<Boolean> = settingsRepository.showCompleted
    val showInternalTags: StateFlow<Boolean> = settingsRepository.showInternalTags
    val showEmptyProjects: StateFlow<Boolean> = settingsRepository.showEmptyProjects
    val tagsPerLine: StateFlow<Int> = settingsRepository.tagsPerLine
    val firstDayOfWeek: StateFlow<Int> = settingsRepository.firstDayOfWeek

    suspend fun init() =
        withContext(Dispatchers.IO) {
            val path = File(baseDir, "taskchampion").absolutePath
            Log.d("TaskRepository", "Initializing replica at $path")
            replica = ReplicaWrapper.newOnDisk(path)
            loadTasks()
        }

    suspend fun loadTasks() =
        withContext(Dispatchers.IO) {
            replica?.let { r ->
                try {
                    val taskData = r.allTaskData()
                    _tasks.value = taskData.map { it.toModel() }
                    Log.d("TaskRepository", "Loaded ${taskData.size} tasks")
                } catch (e: Exception) {
                    Log.e("TaskRepository", "Failed to load tasks", e)
                }
            }
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
    ) = withContext(Dispatchers.IO) {
        val r = replica ?: throw Exception("Replica not initialized")
        try {
            val props =
                TaskAddProps(
                    description = description,
                    project = project,
                    tags = tags,
                    due = due,
                    wait = wait,
                    scheduled = scheduled,
                    start = start,
                    priority = priority,
                    dependencies = dependencies,
                )
            r.addTask(props)
            loadTasks()
            triggerSync()
        } catch (e: Exception) {
            Log.e("TaskRepository", "Failed to add task", e)
            throw e
        }
    }

    suspend fun updateTask(task: Task) =
        withContext(Dispatchers.IO) {
            val r = replica ?: throw Exception("Replica not initialized")
            try {
                val props =
                    TaskUpdateProps(
                        uuid = task.uuid,
                        description = task.description,
                        status = task.status,
                        project = task.project,
                        tags = task.tags,
                        due = task.due,
                        wait = task.wait,
                        scheduled = task.scheduled,
                        start = task.start,
                        priority = task.priority,
                        dependencies = task.dependencies,
                    )
                r.updateTask(props)
                loadTasks()
                triggerSync()
            } catch (e: Exception) {
                Log.e("TaskRepository", "Failed to update task", e)
                throw e
            }
        }

    suspend fun completeTask(uuid: String) =
        withContext(Dispatchers.IO) {
            val r = replica ?: throw Exception("Replica not initialized")
            try {
                r.updateTaskStatus(uuid, TaskStatus.COMPLETED)
                loadTasks()
                triggerSync()
            } catch (e: Exception) {
                Log.e("TaskRepository", "Failed to complete task", e)
                throw e
            }
        }

    suspend fun deleteTask(uuid: String) =
        withContext(Dispatchers.IO) {
            val r = replica ?: throw Exception("Replica not initialized")
            try {
                r.updateTaskStatus(uuid, TaskStatus.DELETED)
                loadTasks()
                triggerSync()
            } catch (e: Exception) {
                Log.e("TaskRepository", "Failed to delete task", e)
                throw e
            }
        }

    suspend fun sync() =
        withContext(Dispatchers.IO) {
            val creds = settingsRepository.getCredentials()
            if (creds == null) {
                Log.w("TaskRepository", "Cannot sync: No credentials found")
                throw Exception("No sync credentials configured")
            }

            replica?.let { r ->
                Log.d("TaskRepository", "Starting manual sync")
                try {
                    r.sync(creds.url, creds.clientId, creds.secret)
                    Log.d("TaskRepository", "Manual sync successful")
                    loadTasks()
                } catch (e: Exception) {
                    Log.e("TaskRepository", "Manual sync failed", e)
                    throw e
                }
            } ?: run {
                Log.e("TaskRepository", "Cannot sync: Replica not initialized")
                throw Exception("Replica not initialized")
            }
        }

    private suspend fun triggerSync() {
        val creds = settingsRepository.getCredentials() ?: return
        replica?.let { r ->
            try {
                Log.d("TaskRepository", "Triggering reactive sync...")
                r.sync(creds.url, creds.clientId, creds.secret)
                loadTasks()
            } catch (e: Exception) {
                Log.e("TaskRepository", "Reactive sync failed", e)
            }
        }
    }
}
