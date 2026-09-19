package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.resolveProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultProjectResolverTest {
    @Test
    fun `explicit project wins and is trimmed`() {
        assertEquals("Work", resolveProject("  Work  ", true, "Inbox"))
    }

    @Test
    fun `enabled default fills missing project`() {
        assertEquals("Inbox", resolveProject(null, true, "  Inbox  "))
        assertEquals("Inbox", resolveProject("   ", true, "Inbox"))
    }

    @Test
    fun `disabled or blank default leaves project absent`() {
        assertNull(resolveProject(null, false, "Inbox"))
        assertNull(resolveProject(null, true, "   "))
        assertNull(resolveProject(null, true, null))
    }
}
