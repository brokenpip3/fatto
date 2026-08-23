package com.brokenpip3.fatto

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.brokenpip3.fatto.data.NextTasksSelector
import com.brokenpip3.fatto.data.SettingsRepositoryImpl
import com.brokenpip3.fatto.data.TaskRepository
import com.brokenpip3.fatto.widget.TaskListWidgetReceiver
import com.brokenpip3.fatto.worker.TaskListWorker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WidgetIntegrationTest {
    private lateinit var repository: TaskRepository
    private lateinit var settingsRepository: SettingsRepositoryImpl

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testDir = File(context.filesDir, "test_widget_repos")
        testDir.deleteRecursively()
        testDir.mkdirs()
        settingsRepository = SettingsRepositoryImpl(context)
        repository = TaskRepository(context, settingsRepository, testDir)
    }

    @Test
    fun testWidgetReceiverIsRegistered() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info =
            context.packageManager.getReceiverInfo(
                ComponentName(context, TaskListWidgetReceiver::class.java),
                PackageManager.GET_META_DATA,
            )
        assertNotNull(info)
    }

    @Test
    fun testNextTasksSelectorAgainstRepository() =
        runBlocking {
            repository.init()
            repository.addTask("Overdue task", "ProjectA", emptyList(), null, "2000-01-01T00:00:00Z", null)
            repository.addTask("Due soon", "ProjectA", emptyList(), null, "2099-01-01T00:00:00Z", null)
            repository.addTask("No due date", "ProjectA", emptyList(), null, null, null)
            repository.addTask("Completed", "ProjectA", emptyList(), null, "2099-01-01T00:00:00Z", null)
            repository.completeTask(repository.tasks.value.first { it.description == "Completed" }.uuid)

            val result = NextTasksSelector.nextTasks(repository.tasks.value, 8)

            assertEquals(listOf("Overdue task", "Due soon"), result.map { it.description })
        }

    @Test
    fun testTaskListWorkerCompletes() =
        runBlocking {
            repository.init()
            repository.addTask("Widget task", null, emptyList(), null, "2099-01-01T00:00:00Z", null)

            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val worker = TestListenableWorkerBuilder<TaskListWorker>(context).build()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
        }
}
