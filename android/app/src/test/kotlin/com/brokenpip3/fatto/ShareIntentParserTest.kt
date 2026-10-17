package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.ShareIntentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareIntentParserTest {
    @Test
    fun plainTextIsReturnedTrimmed() {
        assertEquals("hello world", ShareIntentParser.descriptionFrom("  hello world  "))
    }

    @Test
    fun urlTextIsReturnedAsIs() {
        assertEquals("https://example.com/docs", ShareIntentParser.descriptionFrom("https://example.com/docs"))
    }

    @Test
    fun nullExtraTextReturnsNull() {
        assertNull(ShareIntentParser.descriptionFrom(null))
    }

    @Test
    fun blankExtraTextReturnsNull() {
        assertNull(ShareIntentParser.descriptionFrom(""))
        assertNull(ShareIntentParser.descriptionFrom("   "))
    }

    @Test
    fun multilineTextIsPreserved() {
        assertEquals("line one\nline two", ShareIntentParser.descriptionFrom("line one\nline two"))
    }

    @Test
    fun projectIsTrimmedAndBlankIsNull() {
        assertEquals("home", ShareIntentParser.projectFrom("  home  "))
        assertNull(ShareIntentParser.projectFrom("   "))
        assertNull(ShareIntentParser.projectFrom(null))
    }

    @Test
    fun tagsAreSplitTrimmedAndDeduplicated() {
        assertEquals(
            listOf("dom", "sklep"),
            ShareIntentParser.tagsFrom(" dom, sklep , dom, "),
        )
    }

    @Test
    fun emptyTagsYieldAnEmptyList() {
        assertEquals(emptyList<String>(), ShareIntentParser.tagsFrom(null))
        assertEquals(emptyList<String>(), ShareIntentParser.tagsFrom(" , ,"))
    }

    @Test
    fun dueAcceptsATimestamp() {
        assertEquals("2026-10-05T22:00:00Z", ShareIntentParser.dueFrom("2026-10-05T22:00:00Z"))
    }

    @Test
    fun dueNormalizesAnOffsetToUtc() {
        assertEquals("2026-10-05T20:00:00Z", ShareIntentParser.dueFrom("2026-10-05T22:00:00+02:00"))
    }

    @Test
    fun dueAcceptsABareDateAsMidnightUtc() {
        assertEquals("2026-10-05T00:00:00Z", ShareIntentParser.dueFrom("2026-10-05"))
    }

    @Test
    fun unusableDueIsNullRatherThanAnError() {
        assertNull(ShareIntentParser.dueFrom("tomorrow"))
        assertNull(ShareIntentParser.dueFrom(""))
        assertNull(ShareIntentParser.dueFrom(null))
    }
}
