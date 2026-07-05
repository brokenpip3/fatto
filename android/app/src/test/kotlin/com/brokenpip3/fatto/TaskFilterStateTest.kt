package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.model.TaskContext
import com.brokenpip3.fatto.ui.tasklist.ContextBuilderPolarity
import com.brokenpip3.fatto.ui.tasklist.ContextBuilderTerm
import com.brokenpip3.fatto.ui.tasklist.ContextBuilderTermKind
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

    @Test
    fun `builder terms serialize include and exclude expression`() {
        val state =
            TaskFilterState(
                builderTerms =
                    listOf(
                        ContextBuilderTerm(ContextBuilderTermKind.KEYWORD, "call or email", ContextBuilderPolarity.INCLUDE),
                        ContextBuilderTerm(ContextBuilderTermKind.PROJECT, "Work", ContextBuilderPolarity.INCLUDE),
                        ContextBuilderTerm(ContextBuilderTermKind.TAG, "office", ContextBuilderPolarity.INCLUDE),
                        ContextBuilderTerm(ContextBuilderTermKind.KEYWORD, "blocked", ContextBuilderPolarity.EXCLUDE),
                        ContextBuilderTerm(ContextBuilderTermKind.VIRTUAL_TAG, "WAITING", ContextBuilderPolarity.EXCLUDE),
                    ),
            )

        val context = state.toContext(name = "Work")

        assertEquals("\"call or email\" project:Work +office -blocked -+WAITING", context.expressionText)
    }

    @Test
    fun `multiple included projects serialize as or group`() {
        val state =
            TaskFilterState(
                builderTerms =
                    listOf(
                        ContextBuilderTerm(ContextBuilderTermKind.PROJECT, "home", ContextBuilderPolarity.INCLUDE),
                        ContextBuilderTerm(ContextBuilderTermKind.PROJECT, "work", ContextBuilderPolarity.INCLUDE),
                        ContextBuilderTerm(ContextBuilderTermKind.TAG, "next", ContextBuilderPolarity.INCLUDE),
                        ContextBuilderTerm(ContextBuilderTermKind.PROJECT, "archive", ContextBuilderPolarity.EXCLUDE),
                    ),
            )

        val context = state.toContext(name = "Mixed")

        assertEquals("(project:home or project:work) +next -project:archive", context.expressionText)
    }

    @Test
    fun `blank raw expression falls back to builder terms`() {
        val state =
            TaskFilterState(
                rawExpressionText = "   ",
                builderTerms =
                    listOf(
                        ContextBuilderTerm(ContextBuilderTermKind.PROJECT, "Work", ContextBuilderPolarity.INCLUDE),
                    ),
            )

        val context = state.toContext(name = "Work")

        assertEquals("project:Work", context.expressionText)
    }

    @Test
    fun `flat advanced expressions become builder terms`() {
        val context =
            TaskContext(
                id = "id",
                name = "Work",
                expressionText = "project:Work +office -blocked -+WAITING",
            )

        val state = TaskFilterState.fromContext(context)

        assertEquals(
            listOf(
                ContextBuilderTerm(ContextBuilderTermKind.PROJECT, "Work", ContextBuilderPolarity.INCLUDE),
                ContextBuilderTerm(ContextBuilderTermKind.TAG, "office", ContextBuilderPolarity.INCLUDE),
                ContextBuilderTerm(ContextBuilderTermKind.KEYWORD, "blocked", ContextBuilderPolarity.EXCLUDE),
                ContextBuilderTerm(ContextBuilderTermKind.VIRTUAL_TAG, "WAITING", ContextBuilderPolarity.EXCLUDE),
            ),
            state.builderTerms,
        )
        assertEquals(null, state.rawExpressionText)
    }

    @Test
    fun `project or group becomes builder project terms`() {
        val context =
            TaskContext(
                id = "id",
                name = "Projects",
                expressionText = "(project:home or project:work) +next -project:archive",
            )

        val state = TaskFilterState.fromContext(context)

        assertEquals(
            listOf(
                ContextBuilderTerm(ContextBuilderTermKind.PROJECT, "home", ContextBuilderPolarity.INCLUDE),
                ContextBuilderTerm(ContextBuilderTermKind.PROJECT, "work", ContextBuilderPolarity.INCLUDE),
                ContextBuilderTerm(ContextBuilderTermKind.TAG, "next", ContextBuilderPolarity.INCLUDE),
                ContextBuilderTerm(ContextBuilderTermKind.PROJECT, "archive", ContextBuilderPolarity.EXCLUDE),
            ),
            state.builderTerms,
        )
        assertEquals(null, state.rawExpressionText)
    }

    @Test
    fun `parenthesized and expressions become builder terms`() {
        val context =
            TaskContext(
                id = "id",
                name = "Work",
                expressionText = "(project:Work +office) call",
            )

        val state = TaskFilterState.fromContext(context)

        assertEquals(
            listOf(
                ContextBuilderTerm(ContextBuilderTermKind.KEYWORD, "call", ContextBuilderPolarity.INCLUDE),
                ContextBuilderTerm(ContextBuilderTermKind.PROJECT, "Work", ContextBuilderPolarity.INCLUDE),
                ContextBuilderTerm(ContextBuilderTermKind.TAG, "office", ContextBuilderPolarity.INCLUDE),
            ),
            state.builderTerms,
        )
        assertEquals(null, state.rawExpressionText)
    }

    @Test
    fun `or project groups and negative terms become builder terms`() {
        val context =
            TaskContext(
                id = "id",
                name = "Either",
                expressionText = "(project:Work or project:Home) -+WAITING",
            )

        val state = TaskFilterState.fromContext(context)

        assertEquals(
            listOf(
                ContextBuilderTerm(ContextBuilderTermKind.PROJECT, "Work", ContextBuilderPolarity.INCLUDE),
                ContextBuilderTerm(ContextBuilderTermKind.PROJECT, "Home", ContextBuilderPolarity.INCLUDE),
                ContextBuilderTerm(ContextBuilderTermKind.VIRTUAL_TAG, "WAITING", ContextBuilderPolarity.EXCLUDE),
            ),
            state.builderTerms,
        )
        assertEquals(null, state.rawExpressionText)
    }
}
