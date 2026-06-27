package com.brokenpip3.fatto.notification

object NotificationNavigation {
    const val EXTRA_TASK_UUID = "com.brokenpip3.fatto.extra.TASK_UUID"
    const val ACTION_COMPLETE_TASK = "com.brokenpip3.fatto.action.COMPLETE_TASK"
    const val TASK_NOTIFICATION_ID = 1

    fun taskSearchQuery(uuid: String): String = "uuid:$uuid"

    fun notificationTag(uuid: String): String = "task:$uuid"
}
