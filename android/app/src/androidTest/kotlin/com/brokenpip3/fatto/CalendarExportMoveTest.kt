package com.brokenpip3.fatto

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.brokenpip3.fatto.data.calendar.CalendarAccessException
import com.brokenpip3.fatto.data.calendar.CalendarAccessFailure
import com.brokenpip3.fatto.data.calendar.CalendarChange
import com.brokenpip3.fatto.data.calendar.CalendarExportController
import com.brokenpip3.fatto.data.calendar.CalendarExportReconciler
import com.brokenpip3.fatto.data.calendar.CalendarExportStore
import com.brokenpip3.fatto.data.calendar.CalendarGateway
import com.brokenpip3.fatto.data.calendar.CalendarTarget
import com.brokenpip3.fatto.data.calendar.CalendarTaskSnapshotSource
import com.brokenpip3.fatto.data.calendar.ExportPhase
import com.brokenpip3.fatto.data.calendar.ProviderEvent
import com.brokenpip3.fatto.data.model.Task
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.taskchampion_android.TaskStatus
import java.time.Clock
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class CalendarExportMoveTest {
    @get:Rule val permissions = GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    @Test fun inaccessibleOldCalendarDoesNotBlockMoveAndCleanupResumesAfterRestart() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val name = "calendar-move-${System.nanoTime()}"
            CalendarProviderFixture(context).use { old ->
                CalendarProviderFixture(context).use { new ->
                    var store = CalendarExportStore(context, name)
                    var inaccessible = false
                    val gateway =
                        object : CalendarGateway {
                            override suspend fun listTargets() = old.gateway.listTargets()

                            override suspend fun read(target: CalendarTarget): List<ProviderEvent> {
                                if (inaccessible && target == old.target) throw CalendarAccessException(CalendarAccessFailure.TARGET)
                                return old.gateway.read(target)
                            }

                            override suspend fun apply(
                                target: CalendarTarget,
                                changes: List<CalendarChange>,
                            ) = old.gateway.apply(target, changes)
                        }
                    val task =
                        Task(
                            "move-task", "Move me", TaskStatus.Pending, emptyList(), null, null, null, null,
                            "2050-01-01T00:00:00Z", null, null, 0f, false, false, emptyList(), emptyMap(),
                        )

                    suspend fun reconcile() =
                        CalendarExportReconciler(
                            store, gateway, CalendarTaskSnapshotSource { listOf(task) },
                            Clock.systemUTC(), { ZoneId.of("UTC") },
                        ).reconcile()
                    try {
                        CalendarExportController(store) {}.enable(old.target)
                        reconcile()
                        CalendarExportController(store) {}.selectTarget(new.target)
                        inaccessible = true
                        reconcile()
                        assertEquals(1, new.gateway.read(new.target).size)
                        assertEquals(1, old.gateway.read(old.target).size)
                        assertEquals(ExportPhase.CLEANUP_PENDING, store.readStatus().phase)
                        val id = new.gateway.read(new.target).single().id
                        store.close()
                        store = CalendarExportStore(context, name)
                        inaccessible = false
                        reconcile()
                        assertTrue(old.gateway.read(old.target).isEmpty())
                        assertEquals(id, new.gateway.read(new.target).single().id)
                        assertTrue(store.cleanupTargets().isEmpty())
                    } finally {
                        store.close()
                    }
                }
            }
        }
}
