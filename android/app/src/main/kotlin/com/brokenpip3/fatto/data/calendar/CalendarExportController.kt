package com.brokenpip3.fatto.data.calendar

import kotlinx.coroutines.sync.withLock

/** Configuration changes share the reconciliation lock, so disabled work cannot publish later. */
class CalendarExportController(private val store: CalendarExportStore, private val request: () -> Unit) {
    suspend fun enable(target: CalendarTarget) =
        CalendarExportLock.mutex.withLock {
            val previous = store.readConfig()
            if (previous.target != null && previous.target != target) store.setCleanupPending(previous.target)
            store.activate()
            store.completeCleanup(target)
            store.updateConfig(true, previous.respectWait, target)
            request()
        }

    suspend fun selectTarget(target: CalendarTarget) = enable(target)

    suspend fun setRespectWait(value: Boolean) =
        CalendarExportLock.mutex.withLock {
            val config = store.readConfig()
            store.updateConfig(config.enabled, value, config.target)
            request()
        }

    suspend fun disable(removeEvents: Boolean) =
        CalendarExportLock.mutex.withLock {
            val config = store.readConfig()
            if (removeEvents && config.target != null) store.setCleanupPending(config.target)
            if (!removeEvents) store.cleanupTargets().forEach(store::completeCleanup)
            store.updateConfig(false, config.respectWait, config.target)
            request()
        }

    fun exportNow() = request()
}
