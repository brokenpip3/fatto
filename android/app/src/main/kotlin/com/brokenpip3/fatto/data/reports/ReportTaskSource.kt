package com.brokenpip3.fatto.data.reports

import com.brokenpip3.fatto.data.TaskRepository
import com.brokenpip3.fatto.data.model.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ReportSourceState(
    val tasks: List<Task>? = null,
    val revision: Long = 0,
    val refreshing: Boolean = false,
    val error: String? = null,
    val syncError: String? = null,
)

interface ReportTaskSource {
    val state: StateFlow<ReportSourceState>

    suspend fun refresh()
}

class RepositoryReportTaskSource(private val repository: TaskRepository) : ReportTaskSource {
    override val state: StateFlow<ReportSourceState> = repository.reportSourceState

    override suspend fun refresh() = repository.init()
}

/** Orders busy/error notifications by request and successful snapshots by native read sequence. */
internal class ReportSourcePublisher {
    private val mutable = MutableStateFlow(ReportSourceState())
    val state = mutable.asStateFlow()
    private var nextRequest = 0L
    private var lastSuccess = 0L
    private val pending = mutableSetOf<Long>()

    @Synchronized fun begin(): Long {
        val request = ++nextRequest
        pending += request
        mutable.value = mutable.value.copy(refreshing = true)
        return request
    }

    @Synchronized fun succeed(
        request: Long,
        revision: Long,
        tasks: List<Task>,
        syncError: String?,
    ) {
        pending -= request
        if (revision > mutable.value.revision) {
            lastSuccess = maxOf(lastSuccess, request)
            mutable.value = ReportSourceState(tasks, revision, pending.isNotEmpty(), null, syncError)
        } else {
            mutable.value = mutable.value.copy(refreshing = pending.isNotEmpty())
        }
    }

    @Synchronized fun fail(
        request: Long,
        message: String,
    ) {
        pending -= request
        mutable.value =
            mutable.value.copy(refreshing = pending.isNotEmpty(), error = if (request >= lastSuccess) message else mutable.value.error)
    }

    @Synchronized fun cancel(request: Long) {
        pending -= request
        mutable.value = mutable.value.copy(refreshing = pending.isNotEmpty())
    }
}
