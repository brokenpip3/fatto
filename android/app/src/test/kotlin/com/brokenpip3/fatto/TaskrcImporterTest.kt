package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.TaskrcImportResultType
import com.brokenpip3.fatto.data.TaskrcImporter
import com.brokenpip3.fatto.data.model.TaskContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TaskrcImporterTest {
    @Test
    fun `preview imports contexts active context and weekstart`() {
        val preview =
            TaskrcImporter.preview(
                text =
                    """
                    # contexts
                    context.work.read=project:Work +office
                    context=work
                    weekstart=Sunday
                    """.trimIndent(),
                existingContexts = emptyList(),
                currentActiveContextId = null,
                currentFirstDayOfWeek = Calendar.MONDAY,
            )

        assertEquals(
            listOf(TaskrcImportResultType.ADDED, TaskrcImportResultType.UPDATED, TaskrcImportResultType.ACTIVATED),
            preview.types(),
        )
        assertEquals(Calendar.SUNDAY, preview.firstDayOfWeekAfter)
        assertEquals("project:Work +office", preview.contextsAfter.single().expressionText)
        assertEquals(preview.contextsAfter.single().id, preview.activeContextIdAfter)
    }

    @Test
    fun `preview updates contexts by name and preserves id`() {
        val existing = listOf(TaskContext(id = "work-id", name = "work", expressionText = "+old"))

        val preview = TaskrcImporter.preview("context.work.read=+new", existing, null, Calendar.MONDAY)

        assertEquals("work-id", preview.contextsAfter.single().id)
        assertEquals("+new", preview.contextsAfter.single().expressionText)
        assertEquals(TaskrcImportResultType.UPDATED, preview.actions.single().type)
    }

    @Test
    fun `unchanged context is logged without changing expression`() {
        val existing = listOf(TaskContext(id = "work-id", name = "work", expressionText = "+work"))

        val preview = TaskrcImporter.preview("context.work.read=+work", existing, null, Calendar.MONDAY)

        assertEquals("+work", preview.contextsAfter.single().expressionText)
        assertEquals(TaskrcImportResultType.UNCHANGED, preview.actions.single().type)
    }

    @Test
    fun `invalid context read is not applied`() {
        val preview = TaskrcImporter.preview("context.work.read=priority:H", emptyList(), null, Calendar.MONDAY)

        assertTrue(preview.contextsAfter.isEmpty())
        assertEquals(TaskrcImportResultType.ERROR, preview.actions.single().type)
    }

    @Test
    fun `unsupported lines are skipped with line numbers`() {
        val preview = TaskrcImporter.preview("include holidays.en-US.rc", emptyList(), null, Calendar.MONDAY)

        assertEquals(TaskrcImportResultType.SKIPPED, preview.actions.single().type)
        assertEquals(1, preview.actions.single().lineNumber)
    }

    @Test
    fun `active context reports error when context is missing`() {
        val preview = TaskrcImporter.preview("context=missing", emptyList(), null, Calendar.MONDAY)

        assertEquals(TaskrcImportResultType.ERROR, preview.actions.single().type)
        assertEquals(null, preview.activeContextIdAfter)
    }

    @Test
    fun `unsupported weekstart reports error`() {
        val preview = TaskrcImporter.preview("weekstart=Tuesday", emptyList(), null, Calendar.MONDAY)

        assertEquals(TaskrcImportResultType.ERROR, preview.actions.single().type)
        assertEquals(Calendar.MONDAY, preview.firstDayOfWeekAfter)
    }

    private fun com.brokenpip3.fatto.data.TaskrcImportPreview.types(): List<TaskrcImportResultType> {
        return actions.map { it.type }
    }
}
