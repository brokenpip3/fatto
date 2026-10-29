package com.brokenpip3.fatto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brokenpip3.fatto.data.calendar.CalendarAccessException
import com.brokenpip3.fatto.data.calendar.CalendarAccessFailure
import com.brokenpip3.fatto.data.calendar.CalendarChange
import com.brokenpip3.fatto.data.calendar.CalendarExportReconciler
import com.brokenpip3.fatto.data.calendar.CalendarExportStore
import com.brokenpip3.fatto.data.calendar.CalendarGateway
import com.brokenpip3.fatto.data.calendar.CalendarTarget
import com.brokenpip3.fatto.data.calendar.CalendarTaskSnapshotSource
import com.brokenpip3.fatto.data.calendar.CalendarWriteResult
import com.brokenpip3.fatto.data.calendar.EventKey
import com.brokenpip3.fatto.data.calendar.ExportDisposition
import com.brokenpip3.fatto.data.calendar.ExportOperation
import com.brokenpip3.fatto.data.calendar.ExportPhase
import com.brokenpip3.fatto.data.calendar.ProviderEvent
import com.brokenpip3.fatto.data.model.Task
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.taskchampion_android.TaskStatus
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class CalendarExportReconcilerTest {
    private class FakeGateway : CalendarGateway {
        val events = mutableListOf<ProviderEvent>()
        var writes = 0
        var failRead = false
        var crashAfterInsert = false
        var failWrite: CalendarAccessFailure? = null
        var beforeRead: (() -> Unit)? = null

        override suspend fun listTargets() = listOf(CalendarTarget(1, "test", "local", null))

        override suspend fun read(target: CalendarTarget): List<ProviderEvent> {
            beforeRead?.invoke()
            if (failRead) throw CalendarAccessException(CalendarAccessFailure.QUERY)
            return events.toList()
        }

        override suspend fun apply(
            target: CalendarTarget,
            changes: List<CalendarChange>,
        ): List<CalendarWriteResult> =
            changes.map { change ->
                failWrite?.let { throw CalendarAccessException(it) }
                writes++
                when (change) {
                    is CalendarChange.Insert -> {
                        val id = (events.maxOfOrNull { it.id } ?: 0) + 1
                        events.add(ProviderEvent(id, change.event.key, change.event))
                        if (crashAfterInsert) {
                            crashAfterInsert = false
                            error("Process stopped after provider commit")
                        }
                        CalendarWriteResult(change, id)
                    }
                    is CalendarChange.Update -> {
                        events.removeAll { it.id == change.eventId }
                        events.add(ProviderEvent(change.eventId, change.event.key, change.event))
                        CalendarWriteResult(change, change.eventId)
                    }
                    is CalendarChange.Delete -> {
                        events.removeAll { it.id == change.eventId }
                        CalendarWriteResult(change, change.eventId)
                    }
                }
            }
    }

    @Test fun updatesSameEventRepairsExternalEditsAndRemovesOnlyOwnedEvents() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val name = "calendar-reconcile-${System.nanoTime()}"
            val store = CalendarExportStore(context, name)
            val gateway = FakeGateway()
            val target = gateway.listTargets().single()
            var tasks =
                listOf(
                    Task(
                        "uuid", "Original", TaskStatus.Pending, emptyList(), null, null, null, null,
                        "2026-10-30T00:00:00Z", null, null, 0f, false, false, emptyList(), emptyMap(),
                    ),
                )
            store.activate()
            store.updateConfig(true, true, target)
            val worker =
                CalendarExportReconciler(
                    store, gateway, CalendarTaskSnapshotSource { tasks },
                    Clock.fixed(Instant.parse("2026-10-29T12:00:00Z"), ZoneOffset.UTC), { ZoneId.of("UTC") },
                )
            try {
                worker.reconcile()
                val id = gateway.events.single().id
                worker.reconcile()
                assertEquals(1, gateway.writes)
                gateway.events[0] = gateway.events[0].copy(event = gateway.events[0].event.copy(title = "External edit"))
                worker.reconcile()
                assertEquals("Original", gateway.events.single().event.title)
                assertEquals(id, gateway.events.single().id)
                val foreign = gateway.events.single().copy(id = 999, key = EventKey("other", "other"))
                gateway.events.add(foreign)
                tasks = emptyList()
                worker.reconcile()
                assertEquals(listOf(foreign), gateway.events)
            } finally {
                store.close()
                context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
                File(context.noBackupFilesDir, name).delete()
            }
        }

    @Test fun failedSnapshotReadNeverCleansCalendar() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val name = "calendar-incomplete-${System.nanoTime()}"
            val store = CalendarExportStore(context, name)
            val gateway = FakeGateway()
            store.activate()
            store.updateConfig(true, true, gateway.listTargets().single())
            try {
                val worker =
                    CalendarExportReconciler(
                        store, gateway, CalendarTaskSnapshotSource { error("snapshot failed") },
                        Clock.systemUTC(), { ZoneId.of("UTC") },
                    )
                assertEquals(ExportDisposition.RETRY, worker.reconcile().disposition)
                assertEquals(0, gateway.writes)
            } finally {
                store.close()
            }
        }

    private suspend fun fixture(block: suspend (CalendarExportStore, FakeGateway, CalendarExportReconciler) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "calendar-recovery-${System.nanoTime()}"
        val store = CalendarExportStore(context, name)
        val gateway = FakeGateway()
        store.activate()
        store.updateConfig(true, true, gateway.listTargets().single())
        val task =
            Task(
                "uuid", "Planned work", TaskStatus.Pending, emptyList(), null, null, null, null,
                "2026-10-30T00:00:00Z", null, null, 0f, false, false, emptyList(), emptyMap(),
            )
        val reconciler =
            CalendarExportReconciler(
                store, gateway, CalendarTaskSnapshotSource { listOf(task) },
                Clock.fixed(Instant.parse("2026-10-29T12:00:00Z"), ZoneOffset.UTC), { ZoneId.of("UTC") },
            )
        try {
            block(store, gateway, reconciler)
        } finally {
            store.close()
        }
    }

    @Test fun interruptedInsertRecoversWithoutDuplicate() =
        runBlocking {
            fixture { store, gateway, reconciler ->
                gateway.crashAfterInsert = true
                assertEquals(ExportDisposition.RETRY, reconciler.reconcile().disposition)
                assertEquals(1, gateway.events.size)
                assertEquals(ExportDisposition.SUCCESS, reconciler.reconcile().disposition)
                assertEquals(1, gateway.events.size)
                assertEquals(1, gateway.writes)
                assertEquals(ExportOperation.SETTLED, store.records(gateway.listTargets().single()).single().operation)
            }
        }

    @Test fun duplicatesAreRemovedWithoutLosingCanonicalLedger() =
        runBlocking {
            fixture { store, gateway, reconciler ->
                reconciler.reconcile()
                val original = gateway.events.single()
                gateway.events.add(original.copy(id = 999))
                reconciler.reconcile()
                assertEquals(listOf(original), gateway.events)
                assertEquals(original.id, store.records(gateway.listTargets().single()).single().eventId)
            }
        }

    @Test fun knownEventWithRemovedMarkerIsRepairedButForeignMarkersAreNotClaimed() =
        runBlocking {
            fixture { _, gateway, reconciler ->
                reconciler.reconcile()
                val original = gateway.events.single()
                gateway.events[0] = original.copy(key = null, event = original.event.copy(key = EventKey("", "")))
                reconciler.reconcile()
                assertEquals(original.key, gateway.events.single().key)
                gateway.events[0] = original.copy(key = EventKey("foreign", "uuid"))
                reconciler.reconcile()
                assertEquals(2, gateway.events.size)
                assertTrue(gateway.events.any { it.key?.namespace == "foreign" })
            }
        }

    @Test fun incompleteCalendarReadCannotRemoveExistingEvents() =
        runBlocking {
            fixture { _, gateway, reconciler ->
                reconciler.reconcile()
                gateway.failRead = true
                val writes = gateway.writes
                assertEquals(ExportDisposition.RETRY, reconciler.reconcile().disposition)
                assertEquals(writes, gateway.writes)
                assertEquals(1, gateway.events.size)
            }
        }

    @Test fun permissionRevokedDuringWritePausesAndCanRecover() =
        runBlocking {
            fixture { store, gateway, reconciler ->
                gateway.failWrite = CalendarAccessFailure.PERMISSION
                assertEquals(ExportDisposition.PAUSED, reconciler.reconcile().disposition)
                assertEquals(ExportPhase.PAUSED_PERMISSION, store.readStatus().phase)
                gateway.failWrite = null
                assertEquals(ExportDisposition.SUCCESS, reconciler.reconcile().disposition)
                assertEquals(1, gateway.events.size)
            }
        }

    @Test fun staleConfigCannotPublishAndNewRequestsRemainPending() =
        runBlocking {
            fixture { store, gateway, reconciler ->
                gateway.beforeRead = { store.updateConfig(false, true, null) }
                reconciler.reconcile()
                assertEquals(0, gateway.writes)
            }
            fixture { store, gateway, reconciler ->
                val before = store.readRequestedGeneration()
                gateway.beforeRead = { store.request() }
                val result = reconciler.reconcile()
                assertEquals(before, result.processedRequestGeneration)
                assertTrue(store.readRequestedGeneration() > result.processedRequestGeneration)
            }
        }

    @Test fun disabledCleanupRemovesOnlyOwnedEvents() =
        runBlocking {
            fixture { store, gateway, reconciler ->
                reconciler.reconcile()
                val target = gateway.listTargets().single()
                val foreign = gateway.events.single().copy(id = 999, key = EventKey("other", "other"))
                gateway.events.add(foreign)
                store.setCleanupPending(target)
                store.updateConfig(false, true, target)
                reconciler.reconcile()
                assertEquals(listOf(foreign), gateway.events)
                assertTrue(store.cleanupTargets().isEmpty())
            }
        }

    @Test fun elapsedWaitPublishesWithoutTaskEditAndToggleRemovesFutureWaitAgain() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val store = CalendarExportStore(context, "calendar-wait-${System.nanoTime()}")
            val gateway = FakeGateway()
            val target = gateway.listTargets().single()
            store.activate()
            store.updateConfig(true, true, target)
            val task =
                Task(
                    "uuid", "Planned work", TaskStatus.Pending, emptyList(), null, null, null, "2026-10-30T00:00:00Z",
                    "2026-10-28T00:00:00Z", null, null, 0f, false, false, emptyList(), emptyMap(),
                )

            fun worker(at: String) =
                CalendarExportReconciler(
                    store, gateway, CalendarTaskSnapshotSource { listOf(task) },
                    Clock.fixed(Instant.parse(at), ZoneOffset.UTC), { ZoneId.of("UTC") },
                )
            try {
                assertEquals(Instant.parse("2026-10-30T00:00:00Z"), worker("2026-10-29T12:00:00Z").reconcile().nextRelease)
                assertTrue(gateway.events.isEmpty())
                worker("2026-10-30T00:00:00Z").reconcile()
                assertEquals(1, gateway.events.size)
                worker("2026-10-29T12:00:00Z").reconcile()
                assertTrue(gateway.events.isEmpty())
                store.updateConfig(true, false, target)
                worker("2026-10-29T12:00:00Z").reconcile()
                assertEquals(1, gateway.events.size)
                store.updateConfig(true, true, target)
                worker("2026-10-29T12:00:00Z").reconcile()
                assertTrue(gateway.events.isEmpty())
            } finally {
                store.close()
            }
        }

    @Test fun transientCleanupFailureRetriesInsteadOfWaitingForPeriodicRepair() =
        runBlocking {
            fixture { store, gateway, reconciler ->
                reconciler.reconcile()
                val target = gateway.listTargets().single()
                store.setCleanupPending(target)
                store.updateConfig(false, true, target)
                gateway.failWrite = CalendarAccessFailure.WRITE
                assertEquals(ExportDisposition.RETRY, reconciler.reconcile().disposition)
                assertEquals(ExportPhase.CLEANUP_PENDING, store.readStatus().phase)
                assertEquals(1, gateway.events.size)
                gateway.failWrite = null
                assertEquals(ExportDisposition.SUCCESS, reconciler.reconcile().disposition)
                assertTrue(gateway.events.isEmpty())
            }
        }
}
