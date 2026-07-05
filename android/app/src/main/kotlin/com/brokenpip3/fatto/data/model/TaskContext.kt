package com.brokenpip3.fatto.data.model

import java.util.UUID

data class TaskContext(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val expressionText: String = "",
) {
    fun summary(): String {
        return expressionText.trim().takeIf { it.isNotBlank() } ?: "All tasks"
    }
}
