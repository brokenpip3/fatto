package com.brokenpip3.fatto.data

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

data class TaskTimeSnapshot(val now: Instant, val zone: ZoneId)

interface TaskTimeSource {
    val snapshots: Flow<TaskTimeSnapshot>

    fun refresh()
}

/** The ticker lives only while collected; a resume refresh reads the current clock and device zone. */
class SystemTaskTimeSource(
    private val clock: Clock = Clock.systemUTC(),
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() },
) : TaskTimeSource {
    private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val snapshots: Flow<TaskTimeSnapshot> =
        merge(
            flow {
                emit(Unit)
                while (true) {
                    delay(30_000)
                    emit(Unit)
                }
            },
            refreshes,
        ).map { TaskTimeSnapshot(clock.instant(), zoneProvider()) }

    override fun refresh() {
        refreshes.tryEmit(Unit)
    }
}
