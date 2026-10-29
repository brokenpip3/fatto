package com.brokenpip3.fatto.data.calendar

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.ZoneId

class CalendarExportReconciler(
    private val store: CalendarExportStore,
    private val gateway: CalendarGateway,
    private val source: CalendarTaskSnapshotSource,
    private val clock: Clock,
    private val zone: () -> ZoneId,
) {
    suspend fun reconcile(): ExportRunResult =
        CalendarExportLock.mutex.withLock {
            val requested = store.readRequestedGeneration()
            val config = store.readConfig()
            val previous = store.readStatus().lastSuccessMillis
            try {
                if (!store.isActivated()) {
                    store.writeStatus(
                        ExportStatus(
                            if (config.enabled) ExportPhase.PAUSED_TARGET else ExportPhase.DISABLED, previous,
                            if (config.enabled) "Choose a calendar and enable export on this device." else null,
                        ),
                    )
                    return@withLock ExportRunResult(ExportDisposition.PAUSED, processedRequestGeneration = requested)
                }
                store.writeStatus(ExportStatus(ExportPhase.RUNNING, previous))
                var projection = Projection(emptyList(), null)
                if (config.enabled) {
                    val target = config.target ?: throw CalendarAccessException(CalendarAccessFailure.TARGET)
                    projection = CalendarExportProjection.project(source.read(), config, clock.instant(), zone())
                    reconcileTarget(target, projection.events, config)
                }
                var cleanupFailed = false
                var cleanupRetry = false
                for (target in store.cleanupTargets()) {
                    if (config.enabled && target == config.target) {
                        store.completeCleanup(target)
                        continue
                    }
                    try {
                        reconcileTarget(target, emptyList(), config)
                        store.completeCleanup(target)
                    } catch (e: CalendarAccessException) {
                        cleanupFailed = true
                        cleanupRetry = cleanupRetry || e.failure == CalendarAccessFailure.WRITE || e.failure == CalendarAccessFailure.QUERY
                    }
                }
                store.writeStatus(
                    ExportStatus(
                        if (cleanupFailed) {
                            ExportPhase.CLEANUP_PENDING
                        } else if (config.enabled) {
                            ExportPhase.SUCCESS
                        } else {
                            ExportPhase.DISABLED
                        },
                        if (config.enabled) clock.millis() else previous,
                        if (cleanupFailed) {
                            "Old calendar events remain; restore access or remove them manually."
                        } else {
                            projection.warnings.firstOrNull()
                        },
                    ),
                )
                ExportRunResult(if (cleanupRetry) ExportDisposition.RETRY else ExportDisposition.SUCCESS, projection.nextRelease, requested)
            } catch (e: CancellationException) {
                throw e
            } catch (e: CalendarItemTooLargeException) {
                store.writeStatus(
                    ExportStatus(
                        ExportPhase.PAUSED_DATA, previous,
                        "Task ${e.key.taskUuid}: calendar event exceeds the 256 KiB write budget. " +
                            "Shorten its task text, project, or tags, then export again.",
                    ),
                )
                ExportRunResult(ExportDisposition.PAUSED, processedRequestGeneration = requested)
            } catch (e: CalendarAccessException) {
                val phase =
                    when (e.failure) {
                        CalendarAccessFailure.PERMISSION -> ExportPhase.PAUSED_PERMISSION
                        CalendarAccessFailure.TARGET -> ExportPhase.PAUSED_TARGET
                        else -> ExportPhase.RETRY
                    }
                store.writeStatus(
                    ExportStatus(
                        phase, previous,
                        when (phase) {
                            ExportPhase.PAUSED_PERMISSION -> "Calendar permission is required."
                            ExportPhase.PAUSED_TARGET -> "Selected calendar is unavailable. Choose a writable calendar."
                            else -> "Calendar export failed; changes remain saved in Fatto. Retrying."
                        },
                    ),
                )
                ExportRunResult(
                    if (phase ==
                        ExportPhase.RETRY
                    ) {
                        ExportDisposition.RETRY
                    } else {
                        ExportDisposition.PAUSED
                    },
                    processedRequestGeneration = requested,
                )
            } catch (e: Exception) {
                store.writeStatus(
                    ExportStatus(
                        ExportPhase.RETRY,
                        previous,
                        "Calendar export failed; changes remain saved in Fatto. Retrying.",
                    ),
                )
                ExportRunResult(ExportDisposition.RETRY, processedRequestGeneration = requested)
            }
        }

    private suspend fun reconcileTarget(
        target: CalendarTarget,
        desired: List<ProjectedEvent>,
        config: ExportConfig,
    ) {
        val actual = gateway.read(target)
        val records = store.records(target).associateBy { it.key }
        val owned =
            actual.filter { row ->
                row.key?.namespace == config.namespace || (row.key == null && records.values.any { it.eventId == row.id })
            }
        val used = mutableSetOf<Long>()
        for (event in desired) {
            val matches = owned.filter { it.key == event.key || (it.key == null && records[event.key]?.eventId == it.id) }
            val existing = matches.firstOrNull { it.id == records[event.key]?.eventId } ?: matches.minByOrNull { it.id }
            if (existing != null) used.add(existing.id)
            if (existing != null &&
                existing.key == event.key &&
                existing.available &&
                existing.event.copy(fingerprint = "") == event.copy(fingerprint = "")
            ) {
                store.saveRecord(ExportRecord(event.key, target, existing.id, event.fingerprint, ExportOperation.SETTLED))
                continue
            }
            ensureCurrent(config)
            store.saveRecord(
                ExportRecord(
                    event.key, target, existing?.id, event.fingerprint,
                    if (existing == null) ExportOperation.INSERT else ExportOperation.UPDATE,
                ),
            )
            val change = if (existing == null) CalendarChange.Insert(event) else CalendarChange.Update(existing.id, event)
            val id = gateway.apply(target, listOf(change)).single().eventId
            used.add(id)
            store.saveRecord(ExportRecord(event.key, target, id, event.fingerprint, ExportOperation.SETTLED))
        }
        for (row in owned.filterNot { it.id in used }) {
            ensureCurrent(config)
            val key = row.key ?: records.values.first { it.eventId == row.id }.key
            // Do not replace the settled canonical row's ledger while deleting a duplicate.
            val canonical = store.records(target).firstOrNull { it.key == key }
            if (canonical?.eventId == row.id ||
                canonical == null
            ) {
                store.saveRecord(ExportRecord(key, target, row.id, "", ExportOperation.DELETE))
            }
            gateway.apply(target, listOf(CalendarChange.Delete(row.id)))
            if (canonical?.eventId == row.id || canonical == null) store.removeRecord(key, target)
        }
        val desiredKeys = desired.map { it.key }.toSet()
        for (record in store.records(target)) if (record.key !in desiredKeys) store.removeRecord(record.key, target)
    }

    private fun ensureCurrent(config: ExportConfig) {
        check(store.readConfig().generation == config.generation) { "Calendar configuration changed" }
    }
}
