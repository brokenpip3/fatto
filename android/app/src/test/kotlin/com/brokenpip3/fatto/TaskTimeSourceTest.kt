package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.SystemTaskTimeSource
import com.brokenpip3.fatto.data.TaskTimeSnapshot
import com.brokenpip3.fatto.data.TaskTimeSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class FixedTaskTimeSource(
    now: Instant = Instant.parse("2026-10-18T12:00:00Z"),
    zone: ZoneId = ZoneOffset.UTC,
) : TaskTimeSource {
    override val snapshots = MutableStateFlow(TaskTimeSnapshot(now, zone))
    var refreshes = 0

    override fun refresh() {
        refreshes++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TaskTimeSourceTest {
    @Test fun sourceEmitsImmediatelyEveryThirtySecondsAndOnRefreshUntilCancelled() =
        runTest {
            var now = Instant.parse("2026-10-18T12:00:00Z")
            var zone = ZoneId.of("Europe/Rome")
            val clock =
                object : Clock() {
                    override fun instant(): Instant = now

                    override fun getZone(): ZoneId = ZoneOffset.UTC

                    override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
                }
            val source = SystemTaskTimeSource(clock) { zone }
            val received = mutableListOf<TaskTimeSnapshot>()
            val job = backgroundScope.launch { source.snapshots.collect { received += it } }
            runCurrent()
            assertEquals(listOf(TaskTimeSnapshot(now, zone)), received)
            advanceTimeBy(29_999)
            runCurrent()
            assertEquals(1, received.size)
            now = now.plusSeconds(30)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(TaskTimeSnapshot(now, zone), received.last())
            assertEquals(2, received.size)
            zone = ZoneId.of("America/New_York")
            source.refresh()
            runCurrent()
            assertEquals(TaskTimeSnapshot(now, zone), received.last())
            assertEquals(3, received.size)
            job.cancel()
            runCurrent()
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(3, received.size)
        }
}
