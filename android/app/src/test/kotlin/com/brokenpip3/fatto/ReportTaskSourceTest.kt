package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.reports.ReportSourcePublisher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportTaskSourceTest {
    @Test fun coldStartIsNotEmptySuccess() {
        val publisher = ReportSourcePublisher()
        assertNull(publisher.state.value.tasks)
        val request = publisher.begin()
        assertTrue(publisher.state.value.refreshing)
        publisher.succeed(request, 1, emptyList(), null)
        assertEquals(emptyList<Any>(), publisher.state.value.tasks)
        assertFalse(publisher.state.value.refreshing)
    }

    @Test fun failedRefreshRetainsLastSuccessfulRevision() {
        val p = ReportSourcePublisher()
        p.succeed(p.begin(), 3, emptyList(), null)
        p.fail(p.begin(), "Could not reload local tasks")
        assertEquals(3L, p.state.value.revision)
        assertNotNull(p.state.value.tasks)
        assertNotNull(p.state.value.error)
    }

    @Test fun staleFailureCannotReplaceNewSuccessAndTransportIsSeparate() {
        val p = ReportSourcePublisher()
        val old = p.begin()
        val latest = p.begin()
        p.succeed(latest, 2, emptyList(), "Sync unavailable")
        p.fail(old, "Old error")
        assertNull(p.state.value.error)
        assertEquals("Sync unavailable", p.state.value.syncError)
        assertEquals(2L, p.state.value.revision)
        assertFalse(p.state.value.refreshing)
        val newer = p.begin()
        p.succeed(newer, 1, emptyList(), null)
        assertEquals(2L, p.state.value.revision)
    }

    @Test fun cancellationClearsBusyWithoutReportingError() {
        val p = ReportSourcePublisher()
        val request = p.begin()
        p.cancel(request)
        assertFalse(p.state.value.refreshing)
        assertNull(p.state.value.error)
    }
}
