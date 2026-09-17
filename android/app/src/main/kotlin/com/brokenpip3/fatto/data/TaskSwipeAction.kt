package com.brokenpip3.fatto.data

enum class TaskSwipeAction(val persistedValue: String) {
    NONE("none"),
    COMPLETE("complete"),
    EDIT("edit"),
    START_STOP("start_stop"),
    DELETE("delete"),
    ;

    companion object {
        fun fromPersistedValue(value: String?): TaskSwipeAction {
            return when (value) {
                "toggle_completion" -> COMPLETE
                else -> entries.firstOrNull { it.persistedValue == value } ?: NONE
            }
        }
    }
}
