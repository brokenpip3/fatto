package com.brokenpip3.fatto

import com.brokenpip3.fatto.data.TaskDatabaseCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class TaskDatabaseCoordinatorTest {
    @Test
    fun cancelledCallerKeepsLockUntilItsSynchronousWorkEnds() =
        runBlocking {
            val directory = File(System.getProperty("java.io.tmpdir"), "fatto-coordinator-${java.util.UUID.randomUUID()}")
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            val secondEntered = AtomicBoolean(false)
            val cancelled = AtomicBoolean(false)
            val first =
                launch(Dispatchers.Default) {
                    try {
                        TaskDatabaseCoordinator.withDatabase(directory.path) {
                            started.countDown()
                            check(release.await(5, TimeUnit.SECONDS))
                        }
                    } catch (e: CancellationException) {
                        cancelled.set(true)
                        throw e
                    }
                }
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS))
                first.cancel()
                val second =
                    async(Dispatchers.Default) {
                        TaskDatabaseCoordinator.withDatabase(File(directory, "../${directory.name}").path) {
                            secondEntered.set(true)
                        }
                    }
                Thread.sleep(100)
                assertFalse(secondEntered.get())
                assertFalse(first.isCompleted)
                release.countDown()
                first.join()
                second.await()
                assertTrue(cancelled.get())
                assertTrue(secondEntered.get())
            } finally {
                release.countDown()
                first.cancelAndJoin()
            }
        }

    @Test
    fun cancelledLockWaiterNeverRunsAndDoesNotPreventRetry() =
        runBlocking {
            val path = File(System.getProperty("java.io.tmpdir"), "fatto-waiter-${java.util.UUID.randomUUID()}").path
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            val entered = AtomicBoolean(false)
            val first =
                launch(Dispatchers.Default) {
                    TaskDatabaseCoordinator.withDatabase(path) {
                        started.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                    }
                }
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS))
                val waiter = launch(Dispatchers.Default) { TaskDatabaseCoordinator.withDatabase(path) { entered.set(true) } }
                waiter.cancelAndJoin()
                assertFalse(entered.get())
                release.countDown()
                first.join()
                TaskDatabaseCoordinator.withDatabase(path) { entered.set(true) }
                assertTrue(entered.get())
            } finally {
                release.countDown()
                first.cancelAndJoin()
            }
        }
}
