package com.brokenpip3.fatto.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Coordinates every wrapper using the same database, including background workers. */
object TaskDatabaseCoordinator {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withDatabase(
        path: String,
        block: suspend () -> T,
    ): T =
        withContext(Dispatchers.IO) {
            val caller = currentCoroutineContext()
            val mutex = locks.getOrPut(File(path).canonicalPath) { Mutex() }
            mutex.lock()
            val result =
                try {
                    caller.ensureActive()
                    // UniFFI calls are synchronous: cancellation cannot release this lock
                    // until the native operation and its refresh/sync sequence finish.
                    withContext(NonCancellable) { block() }
                } finally {
                    mutex.unlock()
                }
            caller.ensureActive()
            result
        }
}
