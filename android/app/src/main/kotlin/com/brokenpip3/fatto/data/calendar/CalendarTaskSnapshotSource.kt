package com.brokenpip3.fatto.data.calendar

import android.content.Context
import com.brokenpip3.fatto.data.TaskDatabaseCoordinator
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.toModel
import uniffi.taskchampion_android.ReplicaWrapper
import java.io.File

fun interface CalendarTaskSnapshotSource {
    suspend fun read(): List<Task>
}

class NativeCalendarTaskSnapshotSource(context: Context, private val baseDir: File = context.filesDir) : CalendarTaskSnapshotSource {
    override suspend fun read(): List<Task> {
        val path = File(baseDir, "taskchampion").absolutePath
        return TaskDatabaseCoordinator.withDatabase(path) {
            val replica = ReplicaWrapper.newOnDisk(path)
            try {
                replica.allTaskData().map { it.toModel() }
            } finally {
                replica.destroy()
            }
        }
    }
}
