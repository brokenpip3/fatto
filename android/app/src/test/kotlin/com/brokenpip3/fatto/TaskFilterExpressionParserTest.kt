package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.filter.AndExpression
import com.brokenpip3.fatto.data.filter.KeywordTerm
import com.brokenpip3.fatto.data.filter.NotExpression
import com.brokenpip3.fatto.data.filter.OrExpression
import com.brokenpip3.fatto.data.filter.ProjectTerm
import com.brokenpip3.fatto.data.filter.TagTerm
import com.brokenpip3.fatto.data.filter.TaskFilterExpressionParser
import com.brokenpip3.fatto.data.filter.TaskFilterParseException
import com.brokenpip3.fatto.data.filter.VirtualTagTerm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskFilterExpressionParserTest {
    @Test
    fun `parses positive and negative terms`() {
        assertEquals(TagTerm("work"), TaskFilterExpressionParser.parse("+work").getOrThrow())
        assertEquals(NotExpression(TagTerm("work")), TaskFilterExpressionParser.parse("-+work").getOrThrow())
        assertEquals(NotExpression(KeywordTerm("work")), TaskFilterExpressionParser.parse("-work").getOrThrow())
        assertEquals(ProjectTerm("Home"), TaskFilterExpressionParser.parse("project:Home").getOrThrow())
        assertEquals(NotExpression(ProjectTerm("Home")), TaskFilterExpressionParser.parse("-project:Home").getOrThrow())
        assertEquals(KeywordTerm("buy"), TaskFilterExpressionParser.parse("buy").getOrThrow())
        assertEquals(NotExpression(KeywordTerm("buy")), TaskFilterExpressionParser.parse("-buy").getOrThrow())
    }

    @Test
    fun `recognizes supported virtual tags`() {
        assertEquals(VirtualTagTerm("DUE"), TaskFilterExpressionParser.parse("+DUE").getOrThrow())
        assertEquals(NotExpression(VirtualTagTerm("DUE")), TaskFilterExpressionParser.parse("-+DUE").getOrThrow())
        assertEquals(KeywordTerm("WAITING"), TaskFilterExpressionParser.parse("WAITING").getOrThrow())
        assertEquals(NotExpression(KeywordTerm("WAITING")), TaskFilterExpressionParser.parse("-WAITING").getOrThrow())
        assertEquals(NotExpression(VirtualTagTerm("WAITING")), TaskFilterExpressionParser.parse("-+WAITING").getOrThrow())
    }

    @Test
    fun `normalizes virtual tag casing`() {
        assertEquals(VirtualTagTerm("DUE"), TaskFilterExpressionParser.parse("+due").getOrThrow())
        assertEquals(VirtualTagTerm("WAITING"), TaskFilterExpressionParser.parse("+Waiting").getOrThrow())
        assertEquals(NotExpression(VirtualTagTerm("ACTIVE")), TaskFilterExpressionParser.parse("-+active").getOrThrow())
    }

    @Test
    fun `implicit and binds adjacent terms`() {
        assertEquals(
            AndExpression(listOf(ProjectTerm("Work"), TagTerm("office"), NotExpression(KeywordTerm("inbox")))),
            TaskFilterExpressionParser.parse("project:Work +office -inbox").getOrThrow(),
        )
    }

    @Test
    fun `and has higher precedence than or`() {
        assertEquals(
            OrExpression(
                listOf(
                    TagTerm("home"),
                    AndExpression(listOf(TagTerm("work"), NotExpression(KeywordTerm("inbox")))),
                ),
            ),
            TaskFilterExpressionParser.parse("+home or +work -inbox").getOrThrow(),
        )
    }

    @Test
    fun `explicit and binds adjacent terms`() {
        assertEquals(
            AndExpression(listOf(TagTerm("work"), NotExpression(KeywordTerm("inbox")))),
            TaskFilterExpressionParser.parse("+work and -inbox").getOrThrow(),
        )
    }

    @Test
    fun `operators are case insensitive`() {
        assertEquals(
            OrExpression(
                listOf(
                    TagTerm("home"),
                    AndExpression(listOf(TagTerm("work"), NotExpression(KeywordTerm("inbox")))),
                ),
            ),
            TaskFilterExpressionParser.parse("+home Or +work AND -inbox").getOrThrow(),
        )
    }

    @Test
    fun `implicit and binds adjacent groups`() {
        assertEquals(
            AndExpression(listOf(TagTerm("work"), NotExpression(KeywordTerm("inbox")))),
            TaskFilterExpressionParser.parse("(+work) (-inbox)").getOrThrow(),
        )
    }

    @Test
    fun `parentheses group expressions without surrounding spaces`() {
        assertEquals(
            OrExpression(
                listOf(
                    AndExpression(listOf(NotExpression(KeywordTerm("buy")), NotExpression(KeywordTerm("ninu")))),
                    VirtualTagTerm("DUE"),
                ),
            ),
            TaskFilterExpressionParser.parse("(-buy -ninu) or +DUE").getOrThrow(),
        )
    }

    @Test
    fun `quoted terms preserve spaces and reserved syntax`() {
        assertEquals(KeywordTerm("call or email"), TaskFilterExpressionParser.parse("\"call or email\"").getOrThrow())
        assertEquals(KeywordTerm("(review)"), TaskFilterExpressionParser.parse("\"(review)\"").getOrThrow())
        assertEquals(ProjectTerm("Work Mobile"), TaskFilterExpressionParser.parse("\"project:Work Mobile\"").getOrThrow())
    }

    @Test
    fun `reports missing closing quote`() {
        val result = TaskFilterExpressionParser.parse("\"call or email")

        assertParseFailure(result, "Missing closing quote")
    }

    @Test
    fun `reports unsupported attributes`() {
        val result = TaskFilterExpressionParser.parse("priority:H")

        assertParseFailure(result, "Unsupported attribute: priority")
    }

    @Test
    fun `reports unexpected end after explicit and`() {
        val result = TaskFilterExpressionParser.parse("+work and")

        assertParseFailure(result, "Unexpected end of expression")
    }

    @Test
    fun `reports unexpected end after explicit or`() {
        val result = TaskFilterExpressionParser.parse("+home or")

        assertParseFailure(result, "Unexpected end of expression")
    }

    @Test
    fun `reports empty project values`() {
        val positive = TaskFilterExpressionParser.parse("project:")
        val negative = TaskFilterExpressionParser.parse("-project:")

        assertParseFailure(positive, "Empty project value")
        assertParseFailure(negative, "Empty project value")
    }

    @Test
    fun `reports missing closing parenthesis`() {
        val result = TaskFilterExpressionParser.parse("(+work or +home")

        assertParseFailure(result, "Missing closing parenthesis")
    }

    @Test
    fun `empty expression matches all`() {
        assertEquals(com.brokenpip3.fatto.data.filter.MatchAll, TaskFilterExpressionParser.parse("").getOrThrow())
    }

    private fun assertParseFailure(
        result: Result<*>,
        message: String,
    ) {
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is TaskFilterParseException)
        assertEquals(message, exception?.message)
    }
}
