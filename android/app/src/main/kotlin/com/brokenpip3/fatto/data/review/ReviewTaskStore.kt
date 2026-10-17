package com.brokenpip3.fatto.data.review

import com.brokenpip3.fatto.data.model.Annotation
import com.brokenpip3.fatto.data.model.Task
import kotlinx.coroutines.flow.StateFlow
import uniffi.taskchampion_android.TaskEditPatch

interface ReviewTaskStore {
    val tasks: StateFlow<List<Task>>

    suspend fun refresh()

    suspend fun markReviewed(uuid: String)

    suspend fun saveReviewEdit(patch: TaskEditPatch)

    suspend fun saveReviewDependencies(
        reviewedUuid: String,
        patch: TaskEditPatch,
    )

    suspend fun complete(uuid: String)

    suspend fun delete(uuid: String)

    suspend fun addReviewAnnotation(
        uuid: String,
        description: String,
    ): Annotation

    suspend fun removeReviewAnnotation(
        uuid: String,
        entry: String,
    )
}
