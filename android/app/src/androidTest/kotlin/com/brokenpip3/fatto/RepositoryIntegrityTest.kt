package com.brokenpip3.fatto

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brokenpip3.fatto.data.SettingsRepositoryImpl
import com.brokenpip3.fatto.data.TaskRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.taskchampion_android.TaskStatus
import java.io.File

@RunWith(AndroidJUnit4::class)
class RepositoryIntegrityTest {
    private lateinit var repository: TaskRepository
    private lateinit var settingsRepository: SettingsRepositoryImpl

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testDir = File(context.filesDir, "test_repos")
        testDir.deleteRecursively()
        testDir.mkdirs()

        settingsRepository = SettingsRepositoryImpl(context)
        repository = TaskRepository(context, settingsRepository, testDir)
    }

    @Test
    fun testRepositoryCrud() =
        runBlocking {
            repository.init()

            // Add
            val desc = "Integration test task"
            repository.addTask(desc, "ProjectA", listOf("tag1"), null, null, null)

            var tasks = repository.tasks.value
            assertEquals(1, tasks.size)
            assertEquals(desc, tasks[0].description)

            // Update
            val task = tasks[0]
            val updatedTask = task.copy(description = "Updated desc", project = "ProjectB")
            repository.updateTask(updatedTask)

            tasks = repository.tasks.value
            assertEquals("Updated desc", tasks[0].description)
            assertEquals("ProjectB", tasks[0].project)

            // Complete
            repository.completeTask(task.uuid)
            tasks = repository.tasks.value
            assertEquals(TaskStatus.COMPLETED, tasks[0].status)

            // Delete
            repository.deleteTask(task.uuid)
            tasks = repository.tasks.value
            assertEquals(TaskStatus.DELETED, tasks[0].status)
        }

    @Test
    fun testLargeDataVolume() =
        runBlocking {
            repository.init()
            val count = 100
            for (i in 1..count) {
                repository.addTask("Task $i", "Project", emptyList(), null, null, null)
            }

            val tasks = repository.tasks.value
            assertEquals(count, tasks.size)
        }

    @Test(expected = Exception::class)
    fun testInvalidDateFormatRejection() =
        runBlocking {
            repository.init()
            // Rust side should throw when parsing this date
            repository.addTask("Bad date task", null, emptyList(), null, "not-a-date", null)
        }

    @Test(expected = Exception::class)
    fun testInvalidTagFormatRejection() =
        runBlocking {
            repository.init()
            // Tags with spaces are invalid in TaskChampion
            repository.addTask("Bad tag task", null, listOf("tag with space"), null, null, null)
        }

    @Test
    fun testDependencyRoundtrip() =
        runBlocking {
            repository.init()
            repository.addTask("Dep A", null, emptyList(), null, null, null)
            repository.addTask("Dep B", null, emptyList(), null, null, null)

            val a = repository.tasks.value.find { it.description == "Dep A" }!!
            val b = repository.tasks.value.find { it.description == "Dep B" }!!

            repository.addDependencies(a.uuid, listOf(b.uuid))

            var tasks = repository.tasks.value
            var aAfter = tasks.find { it.uuid == a.uuid }!!
            var bAfter = tasks.find { it.uuid == b.uuid }!!
            assertEquals(listOf(b.uuid), aAfter.dependencies)
            assertTrue(aAfter.isBlocked)
            assertTrue(bAfter.isBlocking)

            repository.removeDependency(a.uuid, b.uuid)

            tasks = repository.tasks.value
            aAfter = tasks.find { it.uuid == a.uuid }!!
            bAfter = tasks.find { it.uuid == b.uuid }!!
            assertTrue(aAfter.dependencies.isEmpty())
            assertFalse(aAfter.isBlocked)
            assertFalse(bAfter.isBlocking)
        }

    @Test
    fun testAddDependenciesDedupes() =
        runBlocking {
            repository.init()
            repository.addTask("Dup A", null, emptyList(), null, null, null)
            repository.addTask("Dup B", null, emptyList(), null, null, null)

            val a = repository.tasks.value.find { it.description == "Dup A" }!!
            val b = repository.tasks.value.find { it.description == "Dup B" }!!

            repository.addDependencies(a.uuid, listOf(b.uuid, b.uuid))

            val aAfter = repository.tasks.value.find { it.uuid == a.uuid }!!
            assertEquals(1, aAfter.dependencies.size)
            assertEquals(listOf(b.uuid), aAfter.dependencies)
        }
}
