package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.SearchParser
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchParserTest {
    @Test
    fun testParseProject() {
        val query = "project:Work buy milk"
        val parsed = SearchParser.parse(query)
        assertEquals("Work", parsed.project)
        assertEquals("buy milk", parsed.description)
    }

    @Test
    fun testParseTags() {
        val query = "buy milk tags:grocery,urgent"
        val parsed = SearchParser.parse(query)
        assertEquals(setOf("grocery", "urgent"), parsed.tags)
        assertEquals("buy milk", parsed.description)
    }

    @Test
    fun testCaseInsensitivityAndSpaces() {
        val query = "PROJECT: Home tags: test"
        val parsed = SearchParser.parse(query)
        assertEquals("Home", parsed.project)
        assertEquals(setOf("test"), parsed.tags)
        assertEquals("", parsed.description)
    }

    @Test
    fun testSubProject() {
        val query = "project:Work.Dev coding"
        val parsed = SearchParser.parse(query)
        assertEquals("Work.Dev", parsed.project)
        assertEquals("coding", parsed.description)
    }

    @Test
    fun testParseUuid() {
        val query = "uuid:550e8400-e29b-41d4-a716-446655440000"
        val parsed = SearchParser.parse(query)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", parsed.uuid)
        assertEquals("", parsed.description)
    }
}
