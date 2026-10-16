package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.model.RecurrenceRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import uniffi.taskchampion_android.RecurrenceData
import uniffi.taskchampion_android.StringEdit

class RecurrenceRuleTest {
    @Test fun createRequiresDueAndPositiveSupportedRuleAndOrderedUntil() {
        assertNotNull(RecurrenceRule("daily", null).creationError(null))
        for (interval in listOf("daily", "weekdays", "weekly", "monthly", "yearly", "3months")) {
            assertNull(RecurrenceRule(interval, null).creationError("2030-01-01T00:00:00Z"))
        }
        for (interval in listOf("", "0days", "-1days", "999999999999days", "fortnightly")) {
            assertNotNull(RecurrenceRule(interval, null).creationError("2030-01-01T00:00:00Z"))
        }
        assertNotNull(RecurrenceRule("daily", "2029-01-01T00:00:00Z").creationError("2030-01-01T00:00:00Z"))
    }

    @Test fun unchangedImportedRulesAndInvalidUntilRetainRawValues() {
        val baseline = RecurrenceData("imported-rule", "periodic", null, null, "--", "broken", null, null, null)
        val patch = RecurrenceRule("imported-rule", "broken").toPatch(baseline)
        assertEquals(StringEdit.Unchanged, patch.interval)
        assertEquals(StringEdit.Unchanged, patch.until)
        val cleared = RecurrenceRule("imported-rule", null).toPatch(baseline)
        assertEquals(StringEdit.Replace("broken", null), cleared.until)
        assertEquals(StringEdit.Unchanged, cleared.interval)
    }

    @Test fun equalUntilInstantsDoNotNormalizeNativeSpelling() {
        val baseline = RecurrenceData("daily", "periodic", null, null, null, "01893456000", "2030-01-01T00:00:00+00:00", null, null)
        assertEquals(StringEdit.Unchanged, RecurrenceRule("daily", "2030-01-01T00:00:00Z").toPatch(baseline).until)
    }
}
