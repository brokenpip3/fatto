package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.SettingsRepository
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.TaskEditResult
import com.brokenpip3.fatto.data.model.toEditPatch
import com.brokenpip3.fatto.data.review.ReviewAction
import com.brokenpip3.fatto.data.review.ReviewSettings
import com.brokenpip3.fatto.data.review.ReviewTaskStore
import com.brokenpip3.fatto.vm.ReviewStage
import com.brokenpip3.fatto.vm.ReviewViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import uniffi.taskchampion_android.TaskStatus
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-10-17T12:00:00Z")

    @Before fun setup() = Dispatchers.setMain(dispatcher)

    @After fun teardown() = Dispatchers.resetMain()

    private fun task(id: String) =
        Task(
            id, id, TaskStatus.Pending, emptyList(), null, null, null, null, null, null,
            null, 0f, false, false, emptyList(), emptyMap(),
        )

    private fun model(
        limit: Int? = null,
        consent: Boolean = true,
        onEnd: (Instant) -> Unit = {},
    ): Pair<ReviewViewModel, ReviewTaskStore> {
        val settings = mockk<SettingsRepository>(relaxed = true)
        every { settings.reviewSettings } returns MutableStateFlow(ReviewSettings(sessionLimit = limit))
        every { settings.getReviewConsentAccepted() } returns consent
        val store = mockk<ReviewTaskStore>(relaxed = true)
        every { store.tasks } returns MutableStateFlow(listOf(task("a"), task("b"), task("c")))
        return ReviewViewModel(store, settings, clock = { now }, onProductiveSessionEnded = onEnd) to store
    }

    @Test fun `closing the consent notice leaves tasks unchanged`() =
        runTest(dispatcher) {
            val (vm, store) = model(consent = false)
            vm.open()
            assertEquals(ReviewStage.NOTICE, vm.state.value.stage)
            vm.start()
            assertEquals(ReviewStage.NOTICE, vm.state.value.stage)
            vm.finish()
            runCurrent()
            assertEquals(ReviewStage.CLOSED, vm.state.value.stage)
            coVerify(exactly = 0) { store.markReviewed(any()) }
            coVerify(exactly = 0) { store.saveReviewEdit(any()) }
            coVerify(exactly = 0) { store.complete(any()) }
            coVerify(exactly = 0) { store.delete(any()) }
        }

    @Test fun `edit-only session keeps the task and resets reminders exactly once when ended`() =
        runTest(dispatcher) {
            val endings = mutableListOf<Instant>()
            val (vm, store) = model(onEnd = endings::add)
            vm.open()
            vm.start()
            val original = vm.state.value.currentTask!!
            val patch = original.copy(description = "Changed").toEditPatch(original)
            assertEquals(TaskEditResult.Success, vm.saveEdit(patch))
            coVerify(exactly = 1) { store.saveReviewEdit(patch) }
            assertEquals(original.uuid, vm.state.value.currentTask?.uuid)
            assertEquals(0, vm.state.value.reviewedActions)
            assertTrue(endings.isEmpty())
            vm.finish()
            vm.finish()
            assertEquals(listOf(now), endings)
        }

    @Test fun `failed or unchanged edit does not reset reminders`() =
        runTest(dispatcher) {
            val endings = mutableListOf<Instant>()
            val (vm, store) = model(onEnd = endings::add)
            vm.open()
            vm.start()
            val original = vm.state.value.currentTask!!
            assertEquals(TaskEditResult.Success, vm.saveEdit(original.toEditPatch(original)))
            coEvery { store.saveReviewEdit(any()) } throws IllegalStateException("conflict")
            assertTrue(vm.saveEdit(original.copy(description = "Changed").toEditPatch(original)) is TaskEditResult.Failure)
            assertEquals(original.uuid, vm.state.value.currentTask?.uuid)
            vm.finish()
            assertEquals(0, vm.state.value.reviewedActions)
            assertTrue(endings.isEmpty())
        }

    @Test fun `skip-only session never resets reminders or writes review dates`() =
        runTest(dispatcher) {
            val endings = mutableListOf<Instant>()
            val (vm, store) = model(limit = 1, onEnd = endings::add)
            vm.open()
            vm.start()
            vm.perform(ReviewAction.SKIP)
            runCurrent()
            assertEquals(ReviewStage.SUMMARY, vm.state.value.stage)
            assertTrue(endings.isEmpty())
            coVerify(exactly = 0) { store.markReviewed(any()) }
        }

    @Test fun `skip counts against session limit and does not count as reviewed`() =
        runTest(dispatcher) {
            val (vm, _) = model(2)
            vm.open()
            vm.start()
            vm.perform(ReviewAction.SKIP)
            runCurrent()
            assertEquals("b", vm.state.value.currentTask?.uuid)
            assertEquals(0, vm.state.value.reviewed)
            vm.perform(ReviewAction.SKIP)
            runCurrent()
            assertEquals(ReviewStage.SUMMARY, vm.state.value.stage)
            assertEquals(2, vm.state.value.skipped)
        }

    @Test fun `failed local commit keeps current task for retry`() =
        runTest(dispatcher) {
            val (vm, store) = model()
            io.mockk.coEvery { store.markReviewed("a") } throws IllegalStateException("disk full")
            vm.open()
            vm.start()
            vm.perform(ReviewAction.REVIEWED)
            runCurrent()
            assertEquals("a", vm.state.value.currentTask?.uuid)
            assertEquals(0, vm.state.value.reviewed)
            assertTrue(vm.state.value.error!!.contains("disk full"))
        }

    @Test fun `successful local commit advances once despite repeated taps`() =
        runTest(dispatcher) {
            val (vm, _) = model()
            vm.open()
            vm.start()
            vm.perform(ReviewAction.REVIEWED)
            vm.perform(ReviewAction.REVIEWED)
            runCurrent()
            assertEquals("b", vm.state.value.currentTask?.uuid)
            assertEquals(1, vm.state.value.reviewed)
        }

    @Test fun `externally completed task is omitted without a review write`() =
        runTest(dispatcher) {
            val (vm, store) = model()
            vm.open()
            vm.start()
            (store.tasks as MutableStateFlow).value = listOf(task("a").copy(status = TaskStatus.Completed), task("b"))
            runCurrent()
            assertEquals("b", vm.state.value.currentTask?.uuid)
            assertEquals(0, vm.state.value.reviewed)
        }

    @Test fun `task changed between tap and dispatch is recorded as omitted`() =
        runTest(dispatcher) {
            val (vm, store) = model()
            vm.open()
            vm.start()
            vm.perform(ReviewAction.REVIEWED)
            (store.tasks as MutableStateFlow).value = listOf(task("a").copy(status = TaskStatus.Completed), task("b"))
            runCurrent()
            assertEquals("b", vm.state.value.currentTask?.uuid)
            assertEquals(1, vm.state.value.omitted)
            assertEquals(0, vm.state.value.reviewed)
        }

    @Test fun `reattaching after recreation preserves queue progress and replaces old observations`() =
        runTest(dispatcher) {
            val (vm, oldStore) = model(2)
            vm.open()
            vm.start()
            vm.perform(ReviewAction.SKIP)
            runCurrent()
            val preferences = mockk<SettingsRepository>(relaxed = true)
            every { preferences.reviewSettings } returns MutableStateFlow(ReviewSettings(sessionLimit = 2))
            val freshStore = mockk<ReviewTaskStore>(relaxed = true)
            every { freshStore.tasks } returns MutableStateFlow(listOf(task("b"), task("c")))
            vm.attach(freshStore, preferences) {}
            (oldStore.tasks as MutableStateFlow).value = emptyList()
            runCurrent()
            assertEquals("b", vm.state.value.currentTask?.uuid)
            assertEquals(2, vm.state.value.total)
            assertEquals(2, vm.state.value.position)
            assertEquals(1, vm.state.value.skipped)
        }
}
