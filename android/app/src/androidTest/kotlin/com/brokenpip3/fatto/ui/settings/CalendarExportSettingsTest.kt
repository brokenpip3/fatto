package com.brokenpip3.fatto.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brokenpip3.fatto.data.calendar.ExportConfig
import com.brokenpip3.fatto.data.calendar.ExportStatus
import com.brokenpip3.fatto.ui.assertTextFits
import com.brokenpip3.fatto.ui.theme.NordicTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarExportSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun respectsWaitByDefaultAndDeliversToggleAction() {
        var selection: Boolean? = null
        compose.setContent {
            NordicTheme {
                CalendarExportSettings(ExportConfig("test", false, true, null, 0), ExportStatus(), {}, {}, { selection = it }, {}, {})
            }
        }
        compose.onNodeWithTag("CalendarRespectWait").assertTextContains("Respect wait date").assertIsOn().performClick()
        compose.runOnIdle { assertEquals(false, selection) }
    }

    private fun scaledLayout(dark: Boolean) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                NordicTheme(darkTheme = dark, fontSizePercent = 150) {
                    Box(Modifier.width(240.dp).verticalScroll(rememberScrollState())) {
                        CalendarExportSettings(ExportConfig("test", false, true, null, 0), ExportStatus(), {}, {}, {}, {}, {})
                    }
                }
            }
        }
        assertTextFits(compose.onNodeWithText("Export scheduled tasks"))
        assertTextFits(compose.onNodeWithText("Respect wait date"))
    }

    @Test fun narrowLightLayoutRespectsCombinedFontScaling() {
        scaledLayout(false)
    }

    @Test fun narrowDarkLayoutRespectsCombinedFontScaling() {
        scaledLayout(true)
    }
}
