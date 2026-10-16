package com.brokenpip3.fatto.data.model

sealed interface TaskEditResult {
    data object Success : TaskEditResult

    data class Failure(val message: String, val conflictFields: List<String> = emptyList()) : TaskEditResult
}
