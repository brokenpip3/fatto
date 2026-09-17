package com.brokenpip3.fatto

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brokenpip3.fatto.data.SettingsRepositoryImpl
import com.brokenpip3.fatto.data.TaskSwipeAction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsSwipeActionTest {
    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteSharedPreferences("sync_settings")
    }

    @After
    fun tearDown() {
        context.deleteSharedPreferences("sync_settings")
    }

    @Test
    fun swipeActionsDefaultToNoneAndPersistIndependently() {
        val first = SettingsRepositoryImpl(context)

        assertEquals(TaskSwipeAction.NONE, first.getSwipeStartToEndAction())
        assertEquals(TaskSwipeAction.NONE, first.getSwipeEndToStartAction())
        first.setSwipeStartToEndAction(TaskSwipeAction.COMPLETE)
        first.setSwipeEndToStartAction(TaskSwipeAction.DELETE)

        val restored = SettingsRepositoryImpl(context)

        assertEquals(TaskSwipeAction.COMPLETE, restored.getSwipeStartToEndAction())
        assertEquals(TaskSwipeAction.DELETE, restored.getSwipeEndToStartAction())
    }
}
