package com.brokenpip3.fatto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brokenpip3.fatto.data.SettingsRepositoryImpl
import com.brokenpip3.fatto.data.TaskRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CalendarExportTriggerTest {
    @Test fun localSaveAndNotificationStyleCompletionRequestExportWithoutServerCredentials() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val directory = File(context.cacheDir, "calendar-repo-${System.nanoTime()}").apply { mkdirs() }
            var requests = 0
            val repository = TaskRepository(context, SettingsRepositoryImpl(context), directory, requestCalendarExport = { requests++ })
            repository.init()
            val before = requests
            val task = repository.addTask("Calendar task", null, emptyList(), null, null, "2026-10-30T00:00:00Z")
            assertTrue(requests > before)
            val added = requests
            repository.completeTask(task.uuid, sync = false)
            assertTrue(requests > added)
            directory.deleteRecursively()
            Unit
        }
}
