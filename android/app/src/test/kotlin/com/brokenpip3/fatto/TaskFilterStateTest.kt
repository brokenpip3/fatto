package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.model.TaskContext
import com.brokenpip3.fatto.ui.tasklist.TaskFilterState
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskFilterStateTest {
    @Test
    fun `lossless expressions round trip through builder state`() {
        val context =
            TaskContext(
                id = "id",
                name = "Work",
                expressionText = "project:Work +office call",
            )

        val rebuilt =
            TaskFilterState.fromContext(context).toContext(
                name = "Work",
                id = context.id,
            )

        assertEquals("call project:Work +office", rebuilt.expressionText)
    }

    @Test
    fun `quoted keyword phrases become search text and serialize safely`() {
        val context =
            TaskContext(
                id = "id",
                name = "Review",
                expressionText = "\"call or email\" project:Work",
            )

        val state = TaskFilterState.fromContext(context)
        val rebuilt = state.toContext(name = "Review", id = context.id)

        assertEquals("call or email", state.descriptionQuery)
        assertEquals("\"call or email\" project:Work", rebuilt.expressionText)
    }

    @Test
    fun `negative terms preserve raw expression text`() {
        val context =
            TaskContext(
                id = "id",
                name = "Work",
                expressionText = "project:Work -home",
            )

        val rebuilt =
            TaskFilterState.fromContext(context).toContext(
                name = "Work",
                id = context.id,
            )

        assertEquals("project:Work -home", rebuilt.expressionText)
    }

    @Test
    fun `or expressions preserve raw expression text`() {
        val context =
            TaskContext(
                id = "id",
                name = "Work",
                expressionText = "project:Work or +office",
            )

        val rebuilt =
            TaskFilterState.fromContext(context).toContext(
                name = "Work",
                id = context.id,
            )

        assertEquals("project:Work or +office", rebuilt.expressionText)
    }
}
