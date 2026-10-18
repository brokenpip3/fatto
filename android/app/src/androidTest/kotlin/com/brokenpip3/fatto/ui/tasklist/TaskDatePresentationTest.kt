package com.brokenpip3.fatto.ui.tasklist

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.brokenpip3.fatto.data.TaskDateTime
import com.brokenpip3.fatto.data.TaskDateValue
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.calendarDateModes
import com.brokenpip3.fatto.ui.common.LocalTaskTimeZone
import com.brokenpip3.fatto.ui.theme.NordicTheme
import org.junit.Rule
import org.junit.Test
import uniffi.taskchampion_android.DateDisplayMode
import uniffi.taskchampion_android.TaskStatus
import java.time.ZoneId

class TaskDatePresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun controlsUseEachFieldsModeAndDeviceClockPreference() {
        val raw = "2026-04-28T22:30:45Z"
        val zone = ZoneId.of("Europe/Rome")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expected =
            TaskDateTime.format(
                TaskDateValue(raw, DateDisplayMode.LOCAL_DATE_TIME), zone,
                DateFormat.is24HourFormat(context), context.resources.configuration.locales[0],
            )!!
        compose.setContent {
            NordicTheme {
                CompositionLocalProvider(LocalTaskTimeZone provides zone) {
                    TaskDateAndPriorityControls(
                        null, raw, raw, null, {}, {},
                        dateModes = calendarDateModes().copy(due = DateDisplayMode.LOCAL_DATE_TIME),
                    )
                }
            }
        }
        compose.onNodeWithText(expected).assertExists()
        compose.onNodeWithText("2026-04-28").assertExists()
    }

    @Test fun reviewDetailsHideOnlyKnownInternalModes() {
        val task =
            Task(
                "test", "Review metadata", TaskStatus.Pending, emptyList(), null, null, null, null, null, null, null,
                0f, false, false, emptyList(), mapOf("fatto_display_due" to "v1:local-datetime", "fatto_display_scheduled" to "v99:future"),
            )
        compose.setContent { NordicTheme { Column(Modifier.verticalScroll(rememberScrollState())) { ReviewTaskCard(task) } } }
        compose.onNodeWithText("More task details").performScrollTo().performClick()
        compose.onNodeWithText("fatto_display_due").assertDoesNotExist()
        compose.onNodeWithText("fatto_display_scheduled").assertExists()
        compose.onNodeWithText("v99:future").assertExists()
    }
}
