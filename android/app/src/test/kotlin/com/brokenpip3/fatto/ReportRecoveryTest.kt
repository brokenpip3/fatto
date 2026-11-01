package com.brokenpip3.fatto

import android.content.Context
import com.brokenpip3.fatto.data.SettingsRepository
import com.brokenpip3.fatto.data.SyncCredentials
import com.brokenpip3.fatto.data.Syncer
import com.brokenpip3.fatto.data.TaskRepository
import com.brokenpip3.fatto.data.model.TaskContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import uniffi.taskchampion_android.ReplicaWrapper
import java.io.File

class ReportRecoveryTest {
    private lateinit var repository: TaskRepository
    private val replica = mockk<ReplicaWrapper>(relaxed = true)

    @Before fun setup() {
        val context = mockk<Context>(relaxed = true)
        val directory = File(System.getProperty("java.io.tmpdir"), "report-recovery-${java.util.UUID.randomUUID()}")
        every { context.filesDir } returns directory
        val delegate = mockk<SettingsRepository>(relaxed = true)
        val settings =
            object : SettingsRepository by delegate {
                override val showCompleted = MutableStateFlow(false)
                override val showInternalTags = MutableStateFlow(false)
                override val showEmptyProjects = MutableStateFlow(false)
                override val tagsPerLine = MutableStateFlow(3)
                override val firstDayOfWeek = MutableStateFlow(2)
                override val hideBlockedTasksWaiting = MutableStateFlow(false)
                override val showWaitingTasks = MutableStateFlow(true)
                override val autoWaiting = MutableStateFlow(false)
                override val sortOrder = MutableStateFlow("DATE_CREATED")
                override val sortDirection = MutableStateFlow("")
                override val showPriorityBadge = MutableStateFlow(true)
                override val showUrgencyBar = MutableStateFlow(true)
                override val taskContexts = MutableStateFlow(emptyList<TaskContext>())
                override val activeTaskContextId = MutableStateFlow<String?>(null)
            }
        every { delegate.hasCredentials() } returns true
        every { delegate.getCredentials() } returns SyncCredentials("https://example.invalid", "client", "secret")
        every { delegate.getS3Credentials() } returns null
        mockkObject(ReplicaWrapper.Companion)
        mockkObject(Syncer)
        every { ReplicaWrapper.newOnDisk(any()) } returns replica
        every { replica.allTaskData() } returns emptyList()
        every { replica.recurrenceDiagnostics() } returns emptyList()
        every { replica.addAnnotation(any(), any()) } returns uniffi.taskchampion_android.Annotation("2026-10-30T00:00:00Z", "note")
        every { Syncer.sync(any(), any()) } returns true
        repository = TaskRepository(context, settings, directory, requestCalendarExport = {})
    }

    @After fun cleanup() = unmockkAll()

    @Test fun initialNativeOpenFailurePublishesRetryableLoadError() =
        runBlocking {
            every { ReplicaWrapper.newOnDisk(any()) } throws IllegalStateException("cannot open replica")
            assertTrue(runCatching { repository.init() }.isFailure)
            assertNull(repository.reportSourceState.value.tasks)
            assertNotNull(repository.reportSourceState.value.error)
        }

    @Test fun manualTransportFailurePreservesUsableLocalSnapshotWithoutLocalReadError() =
        runBlocking {
            repository.init()
            every { Syncer.sync(any(), any()) } throws IllegalStateException("offline")
            assertTrue(runCatching { repository.sync() }.isFailure)
            assertNotNull(repository.reportSourceState.value.tasks)
            assertNull(repository.reportSourceState.value.error)
            assertNotNull(repository.reportSourceState.value.syncError)
        }

    @Test fun successfulManualSyncClearsReactiveTransportFailure() =
        runBlocking {
            repository.init()
            every { Syncer.sync(any(), any()) } throws IllegalStateException("offline")
            repository.addAnnotation("task", "note")
            assertNotNull(repository.reportSourceState.value.syncError)
            every { Syncer.sync(any(), any()) } returns true
            repository.sync()
            assertNull(repository.syncTransportError.value)
            assertNull(repository.reportSourceState.value.syncError)
            assertEquals(0, repository.reportSourceState.value.tasks!!.size)
        }
}
