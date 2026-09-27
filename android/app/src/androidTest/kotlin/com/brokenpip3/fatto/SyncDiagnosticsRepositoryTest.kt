package com.brokenpip3.fatto

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brokenpip3.fatto.data.SyncDiagnosticEvent
import com.brokenpip3.fatto.data.SyncDiagnosticsRepositoryImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncDiagnosticsRepositoryTest {
    @Test
    fun eventsPersistAcrossRepositoryInstancesAndClearPersists() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("sync_diagnostics", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()

        try {
            val event =
                SyncDiagnosticEvent(
                    timestampEpochMillis = 1234L,
                    stage = "sync",
                    outcome = "success",
                    summary = "Sync completed",
                    serverOrigin = "https://sync.example.com",
                    elapsedMillis = 456L,
                    appVersion = "0.12.0",
                )

            SyncDiagnosticsRepositoryImpl(context).append(event)

            val restoredRepository = SyncDiagnosticsRepositoryImpl(context)
            assertEquals(listOf(event), restoredRepository.events.value)

            restoredRepository.clear()

            assertTrue(SyncDiagnosticsRepositoryImpl(context).events.value.isEmpty())
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
