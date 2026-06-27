package com.brokenpip3.fatto

import com.brokenpip3.fatto.notification.NotificationNavigation
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationNavigationTest {
    @Test
    fun taskSearchQueryUsesUuidFilter() {
        val uuid = "550e8400-e29b-41d4-a716-446655440000"

        assertEquals("uuid:$uuid", NotificationNavigation.taskSearchQuery(uuid))
    }

    @Test
    fun notificationTagUsesUuid() {
        val uuid = "550e8400-e29b-41d4-a716-446655440000"

        assertEquals("task:$uuid", NotificationNavigation.notificationTag(uuid))
    }
}
