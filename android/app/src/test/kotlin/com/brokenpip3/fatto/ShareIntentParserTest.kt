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
}
