package com.brokenpip3.fatto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brokenpip3.fatto.data.calendar.CalendarExportController
import com.brokenpip3.fatto.data.calendar.CalendarExportStore
import com.brokenpip3.fatto.data.calendar.CalendarTarget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CalendarExportLifecycleTest {
    @Test fun restoredPreferencesRequireExplicitReactivationAndKeepNamespace() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "calendar-restore-${System.nanoTime()}"
        CalendarExportStore(context, name).use { store ->
            store.activate()
            store.updateConfig(true, true, CalendarTarget(1, "account", "local", null))
            assertTrue(store.isActivated())
            val namespace = store.readConfig().namespace
            File(context.noBackupFilesDir, "$name.activation").delete()
            assertFalse(store.isActivated())
            assertEquals(namespace, store.readConfig().namespace)
            store.activate()
            assertTrue(store.isActivated())
        }
    }

    @Test fun controllerEnablesWaitByDefaultAndRecordsCleanupBeforeSwitch() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val name = "calendar-controller-${System.nanoTime()}"
            CalendarExportStore(context, name).use { store ->
                var requests = 0
                val first = CalendarTarget(1, "first", "local", null)
                val second = CalendarTarget(2, "second", "local", null)
                val controller = CalendarExportController(store) { requests++ }
                controller.enable(first)
                assertTrue(store.readConfig().respectWait)
                controller.setRespectWait(false)
                assertFalse(store.readConfig().respectWait)
                controller.selectTarget(second)
                assertEquals(listOf(first), store.cleanupTargets())
                assertEquals(second, store.readConfig().target)
                controller.disable(true)
                assertFalse(store.readConfig().enabled)
                assertTrue(second in store.cleanupTargets())
                assertEquals(4, requests)
            }
        }
}
