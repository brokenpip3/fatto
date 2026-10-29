package com.brokenpip3.fatto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brokenpip3.fatto.data.calendar.CalendarExportStore
import com.brokenpip3.fatto.data.calendar.CalendarTarget
import com.brokenpip3.fatto.data.calendar.EventKey
import com.brokenpip3.fatto.data.calendar.ExportOperation
import com.brokenpip3.fatto.data.calendar.ExportRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarExportStoreTest {
    @Test fun defaultsAndPendingOperationsSurviveReopening() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "calendar-test-${System.nanoTime()}"
        val target = CalendarTarget(12, "account", "type", "owner")
        val store = CalendarExportStore(context, name)
        try {
            assertFalse(store.readConfig().enabled)
            assertTrue(store.readConfig().respectWait)
            val namespace = store.readConfig().namespace
            val config = store.updateConfig(true, false, target)
            assertTrue(config.generation > 0)
            val requested = store.request()
            val key = EventKey(namespace, "task")
            store.saveRecord(ExportRecord(key, target, null, "fp", ExportOperation.INSERT))
            store.close()
            CalendarExportStore(context, name).use { reopened ->
                assertEquals(namespace, reopened.readConfig().namespace)
                assertEquals(requested, reopened.readRequestedGeneration())
                assertEquals(ExportOperation.INSERT, reopened.records(target).single().operation)
                assertFalse(reopened.readConfig().respectWait)
            }
        } finally {
            store.close()
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
            java.io.File(context.noBackupFilesDir, name).delete()
        }
    }
}
