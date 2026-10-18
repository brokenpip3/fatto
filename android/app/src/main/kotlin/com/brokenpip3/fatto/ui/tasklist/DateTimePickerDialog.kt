package com.brokenpip3.fatto.ui.tasklist

import android.content.res.Configuration
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.DialogProperties
import com.brokenpip3.fatto.data.DateTimeUtils
import com.brokenpip3.fatto.data.TaskDateSelection
import com.brokenpip3.fatto.data.TaskDateTime
import com.brokenpip3.fatto.data.TaskDateValue
import com.brokenpip3.fatto.ui.theme.FattoFieldDefaults
import com.brokenpip3.fatto.ui.theme.FattoMetrics
import com.brokenpip3.fatto.ui.theme.FattoSpacing
import com.brokenpip3.fatto.ui.theme.effectiveFontScale
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Calendar
import java.util.Locale

/** Edits one explicit task-date presentation in a zone captured for this dialog opening. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(
    current: TaskDateValue,
    firstDayOfWeek: Int,
    onConfirm: (TaskDateValue) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    consentStore: com.brokenpip3.fatto.data.DateDisplayConsentStore = rememberDateDisplayConsentStore(),
) {
    val zoneId = rememberSaveable { java.time.ZoneId.systemDefault().id }
    val zone = java.time.ZoneId.of(zoneId)
    val selection = remember(current, zone) { TaskDateTime.decode(current, zone) }
    val currentDate = selection?.date
    val currentTime = selection?.time
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    // The picker throws on an initial selection outside its year range, and synced
    // tasks can carry dates beyond the default 1900-2100.
    val yearRange =
        remember(currentDate) {
            val default = DatePickerDefaults.YearRange
            val year = currentDate?.year ?: default.first
            minOf(default.first, year)..maxOf(default.last, year)
        }
    val datePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = currentDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            yearRange = yearRange,
        )
    val timePickerState =
        rememberTimePickerState(
            initialHour = currentTime?.hour ?: 9,
            initialMinute = currentTime?.minute ?: 0,
            is24Hour = DateFormat.is24HourFormat(LocalContext.current),
        )
    var clockEdited by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(timePickerState.hour, timePickerState.minute) {
        if (currentTime != null && (timePickerState.hour != currentTime.hour || timePickerState.minute != currentTime.minute)) {
            clockEdited = true
        }
    }
    var withTime by rememberSaveable { mutableStateOf(currentTime != null) }
    var showUdaNotice by rememberSaveable { mutableStateOf(false) }
    var noticeError by rememberSaveable { mutableStateOf<String?>(null) }

    val largeClock = effectiveFontScale() >= FattoMetrics.largeFontScale
    var clockValid by remember { mutableStateOf(true) }
    val selectedDate = datePickerState.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
    val selectedTime =
        if (withTime) {
            if (!clockEdited &&
                currentTime != null &&
                currentTime.hour == timePickerState.hour &&
                currentTime.minute == timePickerState.minute
            ) {
                currentTime
            } else {
                LocalTime.of(timePickerState.hour, timePickerState.minute)
            }
        } else {
            null
        }
    val resolved = selectedDate?.let { TaskDateTime.resolve(TaskDateSelection(it, selectedTime), zone, current) }
    val offset =
        if (withTime &&
            clockValid
        ) {
            resolved?.getOrNull()?.timestamp?.let { DateTimeUtils.parseToInstant(it)?.atZone(zone)?.offset }
        } else {
            null
        }

    val calendarWidth = FattoMetrics.minTouchTarget * 7 + FattoSpacing.medium * 2
    // Material's DatePickerDialog forces a 360dp surface even on smaller windows.
    // Reuse its DatePicker inside an adaptive Material dialog so dates/actions stay reachable.
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth().imePadding(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = FattoSpacing.small),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column {
                val currentConfig = LocalConfiguration.current
                val config = Configuration(currentConfig)
                val targetLocale = if (firstDayOfWeek == Calendar.SUNDAY) Locale.US else Locale.UK
                config.setLocale(targetLocale)

                Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    CompositionLocalProvider(LocalConfiguration provides config) {
                        // Preserve Material's seven 48dp day cells on narrow devices. Allow
                        // panning instead of clipping the first/last day's touch region.
                        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                            DatePicker(state = datePickerState, modifier = Modifier.width(calendarWidth))
                        }
                    }
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = withTime,
                                    role = Role.Switch,
                                    onValueChange = { requested ->
                                        if (requested && !consentStore.isAccepted()) {
                                            noticeError = null
                                            showUdaNotice = true
                                        } else {
                                            withTime = requested
                                        }
                                    },
                                ).padding(horizontal = FattoSpacing.extraLarge),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Set time", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = withTime, onCheckedChange = null)
                    }
                    Text(
                        text = zoneId + (offset?.let { " ($it)" } ?: ""),
                        modifier = Modifier.padding(horizontal = FattoSpacing.extraLarge),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(FattoSpacing.medium))
                    }
                    AnimatedVisibility(visible = withTime) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = FattoSpacing.small),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (largeClock) {
                                AccessibleClockInput(timePickerState, onValidityChange = { clockValid = it })
                            } else {
                                TimeInput(state = timePickerState, modifier = Modifier.testTag("TaskDateTimeInput"))
                            }
                        }
                    }
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(FattoSpacing.medium),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onClear) { Text("Clear") }
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("TaskDatePickerCancel")) { Text("Cancel") }
                    TextButton(enabled = !withTime || clockValid, onClick = {
                        if (resolved == null) onDismiss() else resolved.onSuccess(onConfirm).onFailure { error = it.message }
                    }) { Text("OK") }
                }
            }
        }
    }
    if (showUdaNotice) {
        DateDisplayUdaNotice(
            onContinue = {
                if (consentStore.accept()) {
                    withTime = true
                    showUdaNotice = false
                    noticeError = null
                } else {
                    noticeError = "Could not save this preference. Please try again."
                }
            },
            onCancel = {
                showUdaNotice = false
                noticeError = null
            },
            error = noticeError,
        )
    }
}

/** Material's fixed clock cells clip scaled text. Stacked fields retain the user's full font size. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccessibleClockInput(
    state: TimePickerState,
    onValidityChange: (Boolean) -> Unit,
) {
    var hour by rememberSaveable { mutableStateOf((if (state.is24hour) state.hour else (state.hour + 11) % 12 + 1).toString()) }
    var minute by rememberSaveable { mutableStateOf(state.minute.toString().padStart(2, '0')) }
    val hourRange = if (state.is24hour) 0..23 else 1..12
    val validHour = hour.toIntOrNull()?.takeIf { it in hourRange }
    val validMinute = minute.toIntOrNull()?.takeIf { it in 0..59 }
    val keyboard = LocalSoftwareKeyboardController.current
    SideEffect { onValidityChange(validHour != null && validMinute != null) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = FattoSpacing.large).testTag("TaskDateTimeInput"),
        verticalArrangement = Arrangement.spacedBy(FattoSpacing.medium),
    ) {
        OutlinedTextField(
            value = hour,
            onValueChange = { input ->
                if (input.length <= 2 && input.all(Char::isDigit)) {
                    hour = input
                    input.toIntOrNull()?.takeIf { it in hourRange }?.let {
                        state.hour = if (state.is24hour) it else it % 12 + if (state.hour >= 12) 12 else 0
                    }
                }
            },
            label = { Text(if (state.is24hour) "Hour (0–23)" else "Hour (1–12)") },
            modifier = Modifier.fillMaxWidth().heightIn(min = FattoMetrics.minTouchTarget),
            textStyle = MaterialTheme.typography.bodyLarge, colors = FattoFieldDefaults.outlinedColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            singleLine = true, isError = validHour == null,
        )
        OutlinedTextField(
            value = minute,
            onValueChange = { input ->
                if (input.length <= 2 && input.all(Char::isDigit)) {
                    minute = input
                    input.toIntOrNull()?.takeIf { it in 0..59 }?.let { state.minute = it }
                }
            },
            label = { Text("Minute (0–59)") },
            modifier = Modifier.fillMaxWidth().heightIn(min = FattoMetrics.minTouchTarget),
            textStyle = MaterialTheme.typography.bodyLarge, colors = FattoFieldDefaults.outlinedColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
            singleLine = true, isError = validMinute == null,
        )
        if (!state.is24hour) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(FattoSpacing.medium)) {
                FilterChip(selected = state.hour < 12, onClick = { state.hour %= 12 }, label = { Text("AM") })
                FilterChip(selected = state.hour >= 12, onClick = { state.hour = state.hour % 12 + 12 }, label = { Text("PM") })
            }
        }
    }
}
