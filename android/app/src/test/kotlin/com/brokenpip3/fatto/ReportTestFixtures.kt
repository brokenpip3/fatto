package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.model.Task
import uniffi.taskchampion_android.TaskStatus

internal fun reportTaskFixture(
    uuid: String,
    project: String? = "Work",
    entry: String = "2026-10-22T10:00:00Z",
): Task =
    Task(
        uuid = uuid, description = "Report task $uuid", status = TaskStatus.Pending, tags = emptyList(),
        due = null, entry = entry, project = project, wait = null, scheduled = null, start = null,
        priority = null, urgency = 0f, isBlocked = false, isBlocking = false, dependencies = emptyList(), udas = emptyMap(),
    )
