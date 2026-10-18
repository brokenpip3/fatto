package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.DailyTaskSelector
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.calendarDateModes
import com.brokenpip3.fatto.data.model.visibleUdas
import org.junit.Assert.assertEquals
import org.junit.Test
import uniffi.taskchampion_android.DateDisplayMode
import uniffi.taskchampion_android.TaskStatus
import java.time.Instant
import java.time.ZoneId

class DailyTaskSelectorTest {
    private fun task(
        id: String,
        due: String? = null,
        scheduled: String? = null,
    ) = Task(
        id, id, TaskStatus.Pending, emptyList(), due, null, null, null, scheduled, null, null,
        0f, false, false, emptyList(), emptyMap(),
    )

    @Test fun malformedNativeDateIsNeverReinterpretedAsAValidTaskDate() {
        val original = task("malformed").copy(nativeDates = mapOf("due" to "2026-04-28"))
        assertEquals(
            emptyList<Task>(),
            DailyTaskSelector.select(
                listOf(original), true, true, true,
                Instant.parse("2026-04-28T12:00:00Z"), ZoneId.of("Europe/Rome"),
            ),
        )
        assertEquals(mapOf("due" to "2026-04-28"), original.nativeDates)
    }

    @Test fun legacyCorpusMatchesEveryPrePrIncludeFlagCombinationInEveryZone() {
        val tasks =
            listOf(
                task("midnight", "2026-04-28T00:00:00Z"),
                task("late-clock", "2026-04-28T22:30:45+00:00"),
                task("scheduled", scheduled = "2026-04-28T23:00:00Z"),
                task("yesterday", "2026-04-27T22:30:45Z"),
                task("tomorrow", "2026-04-29T00:00:00Z"),
                task("completed", "2026-04-28T00:00:00Z").copy(status = TaskStatus.Completed),
            )
        for (zone in listOf("Europe/Rome", "America/New_York", "UTC")) {
            for (flags in 0..7) {
                val due = flags and 1 != 0
                val scheduled = flags and 2 != 0
                val overdue = flags and 4 != 0
                val expected =
                    buildSet {
                        if (due) addAll(listOf("midnight", "late-clock"))
                        if (scheduled) add("scheduled")
                        if (overdue) add("yesterday")
                    }
                val result =
                    DailyTaskSelector.select(
                        tasks, due, scheduled, overdue,
                        Instant.parse("2026-04-28T12:00:00Z"), ZoneId.of(zone),
                    )
                assertEquals("$zone flags=$flags", expected, result.map { it.uuid }.toSet())
            }
        }
        assertEquals(emptyMap<String, String>(), tasks.first().udas)
    }

    @Test fun earlierClockTodayIsNeverOverdueInEitherMode() {
        val original = task("earlier-today", "2026-04-28T07:30:45Z")
        for (mode in DateDisplayMode.entries) {
            val timed = original.copy(dateModes = calendarDateModes().copy(due = mode))
            assertEquals(
                emptyList<Task>(),
                DailyTaskSelector.select(
                    listOf(timed), false, false, true,
                    Instant.parse("2026-04-28T12:00:00Z"), ZoneId.of("Europe/Rome"),
                ),
            )
        }
    }

    @Test fun markedDatesUseLocalDayButUnmarkedDatesStayOnTheirExistingDay() {
        val original = task("legacy", "2026-04-28T22:30:45Z")
        val marked = original.copy(uuid = "timed", dateModes = calendarDateModes().copy(due = DateDisplayMode.LOCAL_DATE_TIME))
        val now = Instant.parse("2026-04-29T10:00:00Z")
        assertEquals(
            listOf("timed"),
            DailyTaskSelector.select(
                listOf(original, marked), true, false, false, now,
                ZoneId.of("Europe/Rome"),
            ).map { it.uuid },
        )
        assertEquals(
            listOf("legacy", "timed"),
            DailyTaskSelector.select(
                listOf(original, marked), false, false, true, now,
                ZoneId.of("America/New_York"),
            ).map { it.uuid },
        )
    }

    @Test fun onlyRecognizedInternalMarkerValuesAreHidden() {
        val original =
            task("metadata").copy(
                udas =
                    mapOf(
                        "fatto_display_due" to "v1:local-datetime", "fatto_display_scheduled" to "v99:future",
                        "fatto_display_wait" to "other", "custom" to "v1:local-datetime",
                    ),
            )
        assertEquals(original.udas - "fatto_display_due", original.visibleUdas)
        assertEquals("v1:local-datetime", original.udas["fatto_display_due"])
    }
}
