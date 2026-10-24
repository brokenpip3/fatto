package com.brokenpip3.fatto.ui.tasklist

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import com.brokenpip3.fatto.DateDisplayConsentTestRule
import com.brokenpip3.fatto.data.TaskDateValue
import com.brokenpip3.fatto.ui.theme.NordicTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uniffi.taskchampion_android.DateDisplayMode
import java.io.File
import java.util.Calendar

class DateDisplayUdaNoticeTest {
    @get:Rule val compose = createComposeRule()

    @get:Rule val consent = DateDisplayConsentTestRule(accepted = false)
    private var writes = 0
    private val marker: File
        get() =
            File(
                InstrumentationRegistry.getInstrumentation().targetContext.noBackupFilesDir,
                "date-display-uda-notice-v1.accepted",
            )

    private fun showPicker() {
        compose.setContent {
            NordicTheme {
                DateTimePickerDialog(
                    TaskDateValue("2030-01-02T00:00:00Z", DateDisplayMode.CALENDAR_DATE), Calendar.MONDAY,
                    onConfirm = { writes++ }, onClear = { writes++ }, onDismiss = {},
                )
            }
        }
    }

    private fun enable() = compose.onNodeWithText("Set time").performSemanticsAction(SemanticsActions.OnClick) { it() }

    @Test fun cancelAndBackDeclineOnlyThisAttemptWithoutWrites() {
        showPicker()
        enable()
        compose.onNodeWithText("Task time display").assertIsDisplayed()
        compose.onNode(hasText("Cancel") and hasAnyAncestor(hasTestTag("DateDisplayUdaNotice"))).performClick()
        compose.onNodeWithText("Set time").assertIsOff()
        assertFalse(marker.exists())
        enable()
        compose.onNodeWithText("Task time display").assertIsDisplayed()
        assertTrue(
            InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK,
            ),
        )
        compose.waitUntil(5000) { compose.onAllNodes(hasText("Task time display")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Set time").assertIsOff()
        assertFalse(marker.exists())
        assertEquals(0, writes)
    }

    @Test fun continueRemembersAcceptanceWithoutSavingAndDoesNotAskAgain() {
        showPicker()
        enable()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Set time").assertIsOn()
        assertTrue(marker.isFile)
        assertEquals(0, writes)
        compose.onNodeWithText("Set time").performSemanticsAction(SemanticsActions.OnClick) { it() }.assertIsOff()
        enable()
        compose.onNodeWithText("Task time display").assertDoesNotExist()
        compose.onNodeWithTag("TaskDatePickerCancel").performClick()
        assertEquals(0, writes)
    }

    @Test fun documentationLinkDoesNotAcceptOrSave() {
        var opened: String? = null
        compose.setContent {
            CompositionLocalProvider(
                LocalUriHandler provides
                    object : UriHandler {
                        override fun openUri(uri: String) {
                            opened = uri
                        }
                    },
            ) {
                NordicTheme {
                    DateTimePickerDialog(
                        TaskDateValue("2030-01-02T00:00:00Z", DateDisplayMode.CALENDAR_DATE), Calendar.MONDAY,
                        onConfirm = { writes++ }, onClear = { writes++ }, onDismiss = {},
                    )
                }
            }
        }
        enable()
        compose.onNodeWithText("Learn more").performScrollTo().performClick()
        assertEquals("https://taskwarrior.org/docs/udas/", opened)
        compose.onNodeWithText("Task time display").assertExists()
        assertFalse(marker.exists())
        assertEquals(0, writes)
    }

    @Test fun pendingNoticeRestoresWithoutAccepting() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            NordicTheme {
                DateTimePickerDialog(
                    TaskDateValue("2030-01-02T00:00:00Z", DateDisplayMode.CALENDAR_DATE), Calendar.MONDAY,
                    onConfirm = { writes++ }, onClear = { writes++ }, onDismiss = {},
                )
            }
        }
        enable()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Task time display").assertIsDisplayed()
        compose.onNode(hasText("Cancel") and hasAnyAncestor(hasTestTag("DateDisplayUdaNotice"))).performClick()
        compose.onNodeWithText("Set time").assertIsOff()
        assertFalse(marker.exists())
        assertEquals(0, writes)
    }

    @Test fun failedAcknowledgementLeavesTimeOffAndAllowsRetry() {
        showPicker()
        marker.mkdir()
        enable()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Could not save this preference. Please try again.").assertExists()
        compose.onNodeWithText("Task time display").assertExists()
        assertFalse(marker.isFile)
        marker.delete()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Set time").assertIsOn()
        assertEquals(0, writes)
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @Test
    fun outsideTapDeclinesWithoutChangingThePickerOrTask() {
        showPicker()
        enable()
        compose.onNodeWithText("Task time display").assertIsDisplayed()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val focused = arrayOfNulls<android.view.View>(1)
        compose.waitUntil(5000) {
            instrumentation.runOnMainSync {
                focused[0] = android.view.inspector.WindowInspector.getGlobalWindowViews().firstOrNull { it.hasWindowFocus() }
            }
            focused[0] != null
        }
        val bounds = android.graphics.Rect()
        instrumentation.runOnMainSync {
            val view = checkNotNull(focused[0])
            val position = IntArray(2)
            view.getLocationOnScreen(position)
            bounds.set(position[0], position[1], position[0] + view.width, position[1] + view.height)
        }
        val touchSlop = android.view.ViewConfiguration.get(instrumentation.targetContext).scaledWindowTouchSlop
        val outsideY = bounds.top - touchSlop * 2 - 1
        assertTrue("Notice leaves an outside touch area: $bounds", outsideY > 24)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("input tap ${bounds.centerX()} $outsideY"),
        ).use { it.readBytes() }
        compose.waitUntil(5000) { compose.onAllNodes(hasText("Task time display")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Set time").assertIsOff()
        assertFalse(marker.exists())
        assertEquals(0, writes)
    }
}
