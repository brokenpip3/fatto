package com.brokenpip3.fatto.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.brokenpip3.fatto.data.calendar.AndroidCalendarGateway
import com.brokenpip3.fatto.data.calendar.CalendarExportController
import com.brokenpip3.fatto.data.calendar.CalendarExportRuntime
import com.brokenpip3.fatto.data.calendar.CalendarExportScheduler
import com.brokenpip3.fatto.data.calendar.CalendarTarget
import com.brokenpip3.fatto.data.calendar.ExportConfig
import com.brokenpip3.fatto.data.calendar.ExportStatus
import com.brokenpip3.fatto.ui.theme.FattoMetrics
import com.brokenpip3.fatto.ui.theme.FattoSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CalendarExportSettingsScreen() {
    val context = LocalContext.current
    val store = remember { CalendarExportRuntime.store(context) }
    var config by remember { mutableStateOf(store.readConfig()) }
    val status by store.status.collectAsState()
    val scope = rememberCoroutineScope()
    val controller = remember { CalendarExportController(store) { CalendarExportScheduler.request(context) } }
    var targets by remember { mutableStateOf<List<CalendarTarget>?>(null) }
    var proposed by remember { mutableStateOf<CalendarTarget?>(null) }
    var disabling by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun action(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { block() }
                config = store.readConfig()
                error = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Calendar operation failed. Check calendar access and try again."
            } finally {
                busy = false
            }
        }
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted[Manifest.permission.READ_CALENDAR] == true &&
                granted[Manifest.permission.WRITE_CALENDAR] == true
            ) {
                action { targets = AndroidCalendarGateway(context).listTargets() }
            } else {
                error = "Calendar permission is required for export. Your tasks remain available."
            }
        }
    val choose: () -> Unit = { permission.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(FattoSpacing.large),
        verticalArrangement = Arrangement.spacedBy(FattoSpacing.medium),
    ) {
        CalendarExportSettings(
            config, status, choose, {
                disabling = true
            }, { value -> action { controller.setRespectWait(value) } }, choose,
            { CalendarExportScheduler.request(context) }, busy,
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
    targets?.let { calendars ->
        AlertDialog(onDismissRequest = { targets = null }, title = { Text("Choose calendar") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (calendars.isEmpty()) Text("No writable calendars are available. Add a calendar account or local calendar first.")
                calendars.forEach { calendar ->
                    TextButton(onClick = {
                        proposed = calendar
                        targets = null
                    }) {
                        Text("${calendar.displayName}\n${calendar.accountName}")
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { targets = null }) { Text("Cancel") } })
    }
    proposed?.let { calendar ->
        val movingCalendar = config.target != null && config.target != calendar
        AlertDialog(
            onDismissRequest = { proposed = null }, title = {
                Text(
                    if (movingCalendar) {
                        "Move calendar export?"
                    } else {
                        "Enable calendar sync?"
                    },
                )
            },
            text = {
                Text(
                    "Scheduled tasks sync one way from Fatto to this calendar, " +
                        "replacing calendar edits and restoring deleted events" +
                        if (movingCalendar) {
                            "\n\nFatto events will be removed from the previous calendar when it becomes available"
                        } else {
                            ""
                        },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    proposed = null
                    action { controller.enable(calendar) }
                }) { Text("Enable") }
            },
            dismissButton = { TextButton(onClick = { proposed = null }) { Text("Cancel") } },
        )
    }
    if (disabling) {
        AlertDialog(
            onDismissRequest = { disabling = false }, title = { Text("Disable calendar export?") },
            text = { Text("Keep existing exported events, or remove Fatto's events from the calendar?") },
            confirmButton = {
                TextButton(onClick = {
                    disabling = false
                    action { controller.disable(true) }
                }) { Text("Remove events") }
            },
            dismissButton = {
                TextButton(onClick = {
                    disabling = false
                    action { controller.disable(false) }
                }) { Text("Keep events") }
            },
        )
    }
}

@Composable
fun CalendarExportSettings(
    config: ExportConfig,
    status: ExportStatus,
    onEnable: () -> Unit,
    onDisable: (Boolean) -> Unit,
    onRespectWaitChange: (Boolean) -> Unit,
    onChooseTarget: () -> Unit,
    onExportNow: () -> Unit,
    busy: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FattoSpacing.medium)) {
        Text("Calendar export", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        CalendarExportToggle("Export scheduled tasks", config.enabled, !busy, "CalendarExportEnabled") {
            if (it) onEnable() else onDisable(false)
        }
        Text(
            "Scheduled tasks appear as all-day events or 30-minute timed markers. Due dates are not exported.",
            style = MaterialTheme.typography.bodyMedium,
        )
        CalendarExportToggle("Respect wait date", config.respectWait, !busy, "CalendarRespectWait", onRespectWaitChange)
        Text(
            "When enabled, tasks appear after their wait date passes. Background publication may be delayed.",
            style = MaterialTheme.typography.bodyMedium,
        )
        HorizontalDivider()
        config.target?.let { Text("${it.displayName} · ${it.accountName}", style = MaterialTheme.typography.bodyMedium) }
        Button(onChooseTarget, enabled = !busy) { Text(if (config.target == null) "Choose calendar" else "Change calendar") }
        Text("Local export: ${status.phase.name.lowercase().replace('_', ' ')}", style = MaterialTheme.typography.bodyMedium)
        status.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        status.lastSuccessMillis?.let {
            val timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
            Text(
                "Last local export: $timestamp",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            "Your calendar account handles cloud sync separately. Calendar edits do not update Fatto.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onExportNow, enabled = config.enabled && !busy) { Text("Export now") }
    }
}

@Composable
private fun CalendarExportToggle(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    tag: String,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = FattoMetrics.minTouchTarget)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}
