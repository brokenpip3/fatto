package com.brokenpip3.fatto.ui.tasklist

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.brokenpip3.fatto.data.TaskDateValue
import com.brokenpip3.fatto.ui.theme.NordicTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import uniffi.taskchampion_android.DateDisplayMode
import java.util.Calendar

class TaskDatePickerTest {
    @get:Rule val dateDisplayConsent = com.brokenpip3.fatto.DateDisplayConsentTestRule()

    @get:Rule val compose = createComposeRule()

    @Test fun unmarkedMidnightOpensWithoutTimeAndCancelDoesNotCommit() {
        var saved = 0
        var dismissed = 0
        compose.setContent {
            NordicTheme {
                DateTimePickerDialog(
                    TaskDateValue("2030-01-02T00:00:00Z", DateDisplayMode.CALENDAR_DATE), Calendar.MONDAY,
                    onConfirm = { saved++ }, onClear = { saved++ }, onDismiss = { dismissed++ },
                )
            }
        }
        compose.onNodeWithText("Set time").performScrollTo().assertIsOff()
        compose.onNodeWithText("Set time").performClick().assertIsOn()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, saved)
        assertEquals(1, dismissed)
    }

    @Test fun timedValueReopensEnabledAndUnchangedConfirmationPreservesSeconds() {
        val current = TaskDateValue("2030-01-02T00:00:45Z", DateDisplayMode.LOCAL_DATE_TIME)
        var saved: TaskDateValue? = null
        compose.setContent {
            NordicTheme {
                DateTimePickerDialog(current, Calendar.MONDAY, onConfirm = { saved = it }, onClear = {}, onDismiss = {})
            }
        }
        compose.onNodeWithText("Set time").performScrollTo().assertIsOn()
        compose.onNodeWithText("OK").performClick()
        assertEquals(current, saved)
    }
}
