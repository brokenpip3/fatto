package com.brokenpip3.fatto

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brokenpip3.fatto.data.SettingsRepository
import com.brokenpip3.fatto.data.SettingsRepositoryImpl
import com.brokenpip3.fatto.data.TaskDatabaseCoordinator
import com.brokenpip3.fatto.data.TaskRepository
import com.brokenpip3.fatto.data.model.calendarDateModes
import com.brokenpip3.fatto.data.reports.ProjectScope
import com.brokenpip3.fatto.data.reports.ReportGrouping
import com.brokenpip3.fatto.data.reports.RepositoryReportTaskSource
import com.brokenpip3.fatto.data.reports.calculateHistory
import com.brokenpip3.fatto.data.reports.calculateStats
import com.brokenpip3.fatto.data.reports.mapReportTasks
import com.brokenpip3.fatto.data.reports.monthRange
import com.brokenpip3.fatto.data.reports.reportBuckets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.taskchampion_android.ReplicaWrapper
import uniffi.taskchampion_android.TaskAddProps
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class ReportsIntegrationTest {
    @Test fun completedDeletedAndRestoredTasksUpdateReportsWithoutWritingOnRead() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val directory = File(context.cacheDir, "reports-${System.nanoTime()}").apply { mkdirs() }
            val settings =
                object : SettingsRepository by SettingsRepositoryImpl(context) {
                    override fun hasCredentials(): Boolean = false
                }
            val repository = TaskRepository(context, settings, directory, requestCalendarExport = {})
            try {
                val source = RepositoryReportTaskSource(repository)
                assertNull(source.state.value.tasks)
                repository.init()
                assertEquals(0, source.state.value.tasks!!.size)
                val task = repository.addTask("Reports integration", null, emptyList(), null, null, null)
                repository.completeTask(task.uuid, sync = false)
                assertNotNull(source.state.value.tasks!!.single().end)
                val before = source.state.value.revision
                val records = mapReportTasks(source.state.value.tasks!!)
                assertEquals(1, calculateStats(records, ProjectScope.All, Instant.now()).completed)
                val end = records.single().end!!
                val zone = ZoneOffset.UTC
                val buckets = reportBuckets(monthRange(YearMonth.from(end.atZone(zone)), zone), ReportGrouping.WEEK, zone, DayOfWeek.MONDAY)
                assertEquals(1, calculateHistory(records, ProjectScope.All, buckets).totals.completed)
                assertEquals(before, source.state.value.revision)
                repository.restoreTask(task.uuid)
                assertEquals(0, calculateHistory(mapReportTasks(source.state.value.tasks!!), ProjectScope.All, buckets).totals.completed)
                repository.deleteTask(task.uuid)
                assertNotNull(source.state.value.tasks!!.single().end)
                assertEquals(1, calculateHistory(mapReportTasks(source.state.value.tasks!!), ProjectScope.All, buckets).totals.deleted)
            } finally {
                directory.deleteRecursively()
            }
        }

    @Test fun externalReplicaWritesBecomeVisibleAfterRepositoryRefresh() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val directory = File(context.cacheDir, "reports-external-${System.nanoTime()}").apply { mkdirs() }
            val settings =
                object : SettingsRepository by SettingsRepositoryImpl(context) {
                    override fun hasCredentials(): Boolean = false
                }
            val repository = TaskRepository(context, settings, directory, requestCalendarExport = {})
            try {
                repository.init()
                val source = RepositoryReportTaskSource(repository)
                val path = File(directory, "taskchampion").absolutePath
                TaskDatabaseCoordinator.withDatabase(path) {
                    val writer = ReplicaWrapper.newOnDisk(path)
                    try {
                        writer.addTask(
                            TaskAddProps(
                                description = "From another replica writer", project = "Work", tags = emptyList(),
                                due = null, wait = null, scheduled = null, start = null, priority = null,
                                dependencies = emptyList(), dateModes = calendarDateModes(),
                            ),
                        )
                    } finally {
                        writer.destroy()
                    }
                }
                assertEquals(0, source.state.value.tasks!!.size)
                repository.loadTasks()
                assertEquals(1, calculateStats(mapReportTasks(source.state.value.tasks!!), ProjectScope.All, Instant.now()).pending)
            } finally {
                directory.deleteRecursively()
            }
        }
}
