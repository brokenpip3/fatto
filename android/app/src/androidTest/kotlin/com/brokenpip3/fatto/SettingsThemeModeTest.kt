package com.brokenpip3.fatto

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brokenpip3.fatto.data.SettingsRepositoryImpl
import com.brokenpip3.fatto.ui.theme.ThemeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsThemeModeTest {
    private lateinit var context: Context
    private lateinit var repository: SettingsRepositoryImpl

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteSharedPreferences("sync_settings")
        repository = SettingsRepositoryImpl(context)
    }

    @After
    fun tearDown() {
        context.deleteSharedPreferences("sync_settings")
    }

    @Test
    fun themeModeDefaultsToSystem() {
        assertEquals(ThemeMode.SYSTEM, repository.getThemeMode())
        assertEquals(ThemeMode.SYSTEM, repository.themeMode.value)
    }

    @Test
    fun setThemeModePersistsAndUpdatesStateFlow() {
        repository.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, repository.getThemeMode())
        assertEquals(ThemeMode.DARK, repository.themeMode.value)
    }
}
