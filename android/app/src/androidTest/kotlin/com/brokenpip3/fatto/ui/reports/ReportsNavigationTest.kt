package com.brokenpip3.fatto.ui.reports

import android.Manifest
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.brokenpip3.fatto.MainActivity
import com.brokenpip3.fatto.data.SettingsRepositoryImpl
import com.brokenpip3.fatto.notification.NotificationNavigation
import com.brokenpip3.fatto.ui.captureLayoutScreenshot
import org.junit.After
import org.junit.Rule
import org.junit.Test

class ReportsNavigationTest {
    @get:Rule(order = 0)
    val permissions = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    @Test fun reportsSurviveRecreationAndNotificationStillNavigatesToTasks() {
        rule.onNodeWithTag("Navigation_reports").performClick().assertIsSelected()
        rule.onNodeWithTag("ReportCard_HISTORY").performScrollTo().performClick()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithContentDescription("Back to reports").assertIsDisplayed()
        rule.onNodeWithTag("Navigation_tasks").performClick()
        rule.onNodeWithTag("Navigation_reports").performClick()
        rule.onNodeWithContentDescription("Back to reports").assertIsDisplayed()
        rule.runOnUiThread {
            rule.activity.startActivity(
                Intent(rule.activity.intent).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(
                    NotificationNavigation.EXTRA_TASK_UUID,
                    "00000000-0000-0000-0000-000000000001",
                ),
            )
        }
        rule.onNodeWithTag("Navigation_tasks").assertIsSelected()
    }

    private var originalFont: Int? = null
    private var originalSystemFont: Float? = null

    @After fun restoreScaling() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        originalFont?.let { SettingsRepositoryImpl(context).setFontSizePercent(it) }
        originalSystemFont?.let { changeSystemFont(it) }
    }

    private fun changeSystemFont(scale: Float) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("settings put system font_scale $scale")).use {
            it.readBytes()
        }
        rule.waitUntil(10_000) { rule.activity.resources.configuration.fontScale == scale }
    }

    @Test fun allSixNavigationTargetsRemainUsableAtCombinedFontScaling() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = SettingsRepositoryImpl(context)
        originalFont = settings.fontSizePercent.value
        originalSystemFont = rule.activity.resources.configuration.fontScale
        settings.setFontSizePercent(150)
        changeSystemFont(2f)
        rule.activityRule.scenario.recreate()
        for (destination in listOf("tasks", "projects", "calendar", "tags", "reports", "settings")) {
            rule.onNodeWithTag("Navigation_$destination").assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
        rule.onNodeWithTag("Navigation_reports").performClick().assertIsSelected()
        captureLayoutScreenshot("reports-navigation-combined-large")
    }
}
