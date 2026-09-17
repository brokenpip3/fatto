package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.TaskSwipeAction
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskSwipeActionTest {
    @Test
    fun `known persisted values round trip`() {
        assertEquals(TaskSwipeAction.NONE, TaskSwipeAction.fromPersistedValue("none"))
        assertEquals(TaskSwipeAction.COMPLETE, TaskSwipeAction.fromPersistedValue("complete"))
        assertEquals(TaskSwipeAction.EDIT, TaskSwipeAction.fromPersistedValue("edit"))
        assertEquals(TaskSwipeAction.START_STOP, TaskSwipeAction.fromPersistedValue("start_stop"))
        assertEquals(TaskSwipeAction.DELETE, TaskSwipeAction.fromPersistedValue("delete"))
    }

    @Test
    fun `legacy toggle completion value migrates to complete`() {
        assertEquals(TaskSwipeAction.COMPLETE, TaskSwipeAction.fromPersistedValue("toggle_completion"))
    }

    @Test
    fun `missing and unknown values disable swiping`() {
        assertEquals(TaskSwipeAction.NONE, TaskSwipeAction.fromPersistedValue(null))
        assertEquals(TaskSwipeAction.NONE, TaskSwipeAction.fromPersistedValue("unexpected"))
    }
}
