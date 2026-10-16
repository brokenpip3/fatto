package com.brokenpip3.fatto

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brokenpip3.fatto.data.TaskDatabaseCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.taskchampion_android.ReplicaWrapper
import java.io.File
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class NativeSyncCancellationTest {
    @Test fun cancellationKeepsDatabaseLockedUntilNativeSyncReturns() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val directory = File(context.cacheDir, "recurrence-cancellation-${UUID.randomUUID()}").apply { mkdirs() }
            val replica = ReplicaWrapper.newOnDisk(directory.path)
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val secondEntered = AtomicBoolean(false)
            val server = ServerSocket(0)
            val serving =
                launch(Dispatchers.IO) {
                    server.accept().use { socket ->
                        socket.soTimeout = 10000
                        val reader = socket.getInputStream().bufferedReader()
                        while (!reader.readLine().isNullOrEmpty()) { }
                        entered.countDown()
                        check(release.await(10, TimeUnit.SECONDS))
                        socket.getOutputStream().write(
                            "HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(),
                        )
                        socket.getOutputStream().flush()
                    }
                }
            try {
                val first =
                    launch(Dispatchers.IO) {
                        TaskDatabaseCoordinator.withDatabase(directory.path) {
                            runCatching {
                                replica.sync("http://127.0.0.1:${server.localPort}", UUID.randomUUID().toString(), "private-test")
                            }
                        }
                    }
                assertTrue(entered.await(10, TimeUnit.SECONDS))
                first.cancel()
                val second =
                    async(Dispatchers.IO) {
                        TaskDatabaseCoordinator.withDatabase(File(directory, ".").path) {
                            secondEntered.set(true)
                            replica.allTaskData()
                        }
                    }
                Thread.sleep(100)
                assertFalse(secondEntered.get())
                assertFalse(first.isCompleted)
                release.countDown()
                first.join()
                second.await()
                assertTrue(secondEntered.get())
                serving.join()
            } finally {
                release.countDown()
                server.close()
                serving.cancelAndJoin()
                replica.destroy()
                directory.deleteRecursively()
            }
        }
}
