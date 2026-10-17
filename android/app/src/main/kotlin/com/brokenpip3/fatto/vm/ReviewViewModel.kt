package com.brokenpip3.fatto.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brokenpip3.fatto.data.SettingsRepository
import com.brokenpip3.fatto.data.model.Annotation
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.TaskEditResult
import com.brokenpip3.fatto.data.model.toEditPatch
import com.brokenpip3.fatto.data.review.ReviewAction
import com.brokenpip3.fatto.data.review.ReviewPolicy
import com.brokenpip3.fatto.data.review.ReviewSettings
import com.brokenpip3.fatto.data.review.ReviewTaskStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import uniffi.taskchampion_android.StringEdit
import uniffi.taskchampion_android.StringSetEdit
import uniffi.taskchampion_android.TaskEditPatch
import java.time.Instant

enum class ReviewStage { CLOSED, NOTICE, START, ACTIVE, SUMMARY }

data class ReviewUiState(
    val stage: ReviewStage = ReviewStage.CLOSED,
    val currentTask: Task? = null,
    val total: Int = 0,
    val position: Int = 0,
    val reviewed: Int = 0,
    val skipped: Int = 0,
    val completed: Int = 0,
    val deleted: Int = 0,
    val omitted: Int = 0,
    val invalidDates: Int = 0,
    val busy: Boolean = false,
    val error: String? = null,
    val settings: ReviewSettings = ReviewSettings(),
) {
    val reviewedActions: Int get() = reviewed + completed + deleted
}

class ReviewViewModel(
    private var store: ReviewTaskStore,
    private var preferences: SettingsRepository,
    private val clock: () -> Instant = Instant::now,
    private var onProductiveSessionEnded: (Instant) -> Unit = {},
) : ViewModel() {
    private val _state = MutableStateFlow(ReviewUiState())
    val state = _state.asStateFlow()
    private var queue = emptyList<String>()
    private var index = 0
    private var productive = false
    private var reported = false
    private var taskObservation: Job? = null
    private var preferenceObservation: Job? = null

    init {
        observeSources()
    }

    private fun observeSources() {
        taskObservation?.cancel()
        preferenceObservation?.cancel()
        val observedStore = store
        val observedPreferences = preferences
        taskObservation =
            viewModelScope.launch {
                observedStore.tasks.collect {
                    when (_state.value.stage) {
                        ReviewStage.ACTIVE -> if (!_state.value.busy) refreshCurrent()
                        ReviewStage.START -> preview()
                        else -> Unit
                    }
                }
            }
        preferenceObservation =
            viewModelScope.launch {
                observedPreferences.reviewSettings.collect {
                    if (_state.value.stage == ReviewStage.START) preview()
                }
            }
    }

    suspend fun attach(
        store: ReviewTaskStore,
        preferences: SettingsRepository,
        onProductiveSessionEnded: (Instant) -> Unit,
    ) {
        state.first { !it.busy }
        _state.value = _state.value.copy(busy = true)
        try {
            // Read after any in-flight action finishes so recreation cannot publish an older snapshot.
            store.refresh()
            this.store = store
            this.preferences = preferences
            this.onProductiveSessionEnded = onProductiveSessionEnded
            observeSources()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = _state.value.copy(error = e.message ?: "Could not reload review tasks")
        } finally {
            _state.value = _state.value.copy(busy = false)
        }
        when (_state.value.stage) {
            ReviewStage.ACTIVE -> refreshCurrent()
            ReviewStage.START -> preview()
            else -> Unit
        }
    }

    fun open() {
        if (_state.value.stage == ReviewStage.ACTIVE) return
        _state.value = ReviewUiState(stage = if (preferences.getReviewConsentAccepted()) ReviewStage.START else ReviewStage.NOTICE)
        if (_state.value.stage == ReviewStage.START) preview()
    }

    fun acceptNotice() {
        try {
            preferences.setReviewConsentAccepted(true)
            _state.value = _state.value.copy(stage = ReviewStage.START, error = null)
            preview()
        } catch (e: Exception) {
            _state.value = _state.value.copy(error = e.message ?: "Could not save consent")
        }
    }

    private fun preview() {
        val settings = preferences.reviewSettings.value
        val selected = ReviewPolicy.select(store.tasks.value, settings, clock())
        _state.value =
            _state.value.copy(total = selected.tasks.size, settings = settings, invalidDates = selected.invalidReviewedTaskIds.size)
    }

    fun start() {
        if (_state.value.stage != ReviewStage.START || _state.value.busy) return
        val settings = preferences.reviewSettings.value
        val selected = ReviewPolicy.select(store.tasks.value, settings, clock())
        queue = selected.tasks.map { it.uuid }
        index = 0
        productive = false
        reported = false
        _state.value = ReviewUiState(stage = ReviewStage.ACTIVE, total = queue.size, settings = settings)
        refreshCurrent()
    }

    private fun refreshCurrent() {
        var omitted = _state.value.omitted
        while (index < queue.size) {
            val task = store.tasks.value.find { it.uuid == queue[index] }
            if (task != null && ReviewPolicy.isPending(task)) {
                _state.value = _state.value.copy(currentTask = task, position = index + 1, omitted = omitted)
                return
            }
            index++
            omitted++
        }
        _state.value = _state.value.copy(omitted = omitted)
        finish()
    }

    fun perform(action: ReviewAction) {
        val current = _state.value
        val task = current.currentTask ?: return
        if (current.stage != ReviewStage.ACTIVE || current.busy) return
        _state.value = current.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val fresh = store.tasks.value.find { it.uuid == task.uuid }
                if (fresh == null || !ReviewPolicy.isPending(fresh)) {
                    index++
                    _state.value = _state.value.copy(omitted = _state.value.omitted + 1)
                    return@launch
                }
                when (action) {
                    ReviewAction.REVIEWED -> store.markReviewed(task.uuid)
                    ReviewAction.SKIP -> Unit
                    ReviewAction.COMPLETE -> store.complete(task.uuid)
                    ReviewAction.DELETE -> store.delete(task.uuid)
                }
                productive = productive || action != ReviewAction.SKIP
                val s = _state.value
                _state.value =
                    s.copy(
                        reviewed = s.reviewed + if (action == ReviewAction.REVIEWED) 1 else 0,
                        skipped = s.skipped + if (action == ReviewAction.SKIP) 1 else 0,
                        completed = s.completed + if (action == ReviewAction.COMPLETE) 1 else 0,
                        deleted = s.deleted + if (action == ReviewAction.DELETE) 1 else 0,
                    )
                index++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "Could not save review action")
            } finally {
                _state.value = _state.value.copy(busy = false)
                refreshCurrent()
            }
        }
    }

    suspend fun saveEdit(patch: TaskEditPatch): TaskEditResult =
        try {
            store.saveReviewEdit(patch)
            val scalars = listOf(patch.description, patch.project, patch.priority, patch.due, patch.wait, patch.scheduled, patch.start)
            if (scalars.any { it != StringEdit.Unchanged } ||
                patch.tags != StringSetEdit.Unchanged ||
                patch.dependencies != StringSetEdit.Unchanged
            ) {
                productive = true
            }
            TaskEditResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TaskEditResult.Failure(e.message ?: "Could not save review edit")
        }

    suspend fun addAnnotation(
        uuid: String,
        description: String,
    ): Annotation =
        store.addReviewAnnotation(uuid, description).also {
            productive =
                true
        }

    suspend fun removeAnnotation(
        uuid: String,
        entry: String,
    ) {
        store.removeReviewAnnotation(uuid, entry)
        productive = true
    }

    suspend fun addDependencies(
        reviewedUuid: String,
        uuid: String,
        dependencies: List<String>,
    ) {
        check(_state.value.currentTask?.uuid == reviewedUuid) { "Reviewed task changed; reopen the review" }
        val current = store.tasks.value.first { it.uuid == uuid }
        val patch = current.copy(dependencies = (current.dependencies + dependencies).distinct()).toEditPatch(current)
        store.saveReviewDependencies(reviewedUuid, patch)
        if (patch.dependencies != StringSetEdit.Unchanged) productive = true
    }

    suspend fun removeDependency(
        reviewedUuid: String,
        uuid: String,
        dependency: String,
    ) {
        check(_state.value.currentTask?.uuid == reviewedUuid) { "Reviewed task changed; reopen the review" }
        val current = store.tasks.value.first { it.uuid == uuid }
        val patch = current.copy(dependencies = current.dependencies - dependency).toEditPatch(current)
        store.saveReviewDependencies(reviewedUuid, patch)
        if (patch.dependencies != StringSetEdit.Unchanged) productive = true
    }

    fun finish() {
        if (_state.value.busy) return
        if (_state.value.stage == ReviewStage.ACTIVE) {
            _state.value = _state.value.copy(stage = ReviewStage.SUMMARY, currentTask = null)
            if (productive && !reported) {
                reported = true
                try {
                    onProductiveSessionEnded(clock())
                } catch (e: Exception) {
                    _state.value = _state.value.copy(error = "Review saved; could not reset reminder: ${e.message}")
                }
            }
        } else {
            _state.value = _state.value.copy(stage = ReviewStage.CLOSED)
        }
    }
}
