package com.brokenpip3.fatto.data.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ReviewReminderPolicyTest {
    private val now = Instant.parse("2026-10-17T12:00:00Z")

    @Test fun `next due preserves time for configured day intervals`() {
        assertEquals(Instant.parse("2026-10-24T12:00:00Z"), ReviewReminderPolicy.nextDue(now, 7))
        assertEquals(Instant.parse("2026-11-16T12:00:00Z"), ReviewReminderPolicy.nextDue(now, 30))
        assertEquals(Instant.parse("2026-10-31T12:00:00Z"), ReviewReminderPolicy.nextDue(now, 14))
    }

    @Test fun `nonpositive intervals are rejected`() {
        assertTrue(runCatching { ReviewReminderPolicy.nextDue(now, 0) }.isFailure)
        assertTrue(runCatching { ReviewReminderPolicy.nextDue(now, -1) }.isFailure)
    }
}
