package com.brokenpip3.fatto.ui.tasklist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.brokenpip3.fatto.data.DateTimeUtils
import com.brokenpip3.fatto.data.model.RecurrenceRule
import com.brokenpip3.fatto.ui.theme.FattoFieldDefaults
import com.brokenpip3.fatto.ui.theme.FattoMetrics
import com.brokenpip3.fatto.ui.theme.FattoSpacing
import java.time.Instant

private val presets =
    linkedMapOf(
        "daily" to "Daily", "weekdays" to "Weekdays", "weekly" to "Weekly", "monthly" to "Monthly", "yearly" to "Yearly",
    )
private val customInterval = Regex("([0-9]+)(days|weeks|months|years)")

internal fun recurrenceLabel(interval: String?): String =
    when {
        interval == null -> "Repeat"
        interval in presets -> presets.getValue(interval)
        else -> customRecurrenceLabel(interval)
    }

private fun customRecurrenceLabel(interval: String): String {
    val match = customInterval.matchEntire(interval) ?: return interval.ifBlank { "Custom" }
    val number = match.groupValues[1]
    val unit = if (number == "1") match.groupValues[2].dropLast(1) else match.groupValues[2]
    return "Every $number $unit"
}

@Composable
internal fun RepeatControl(
    interval: String?,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val label = recurrenceLabel(interval)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier.widthIn(min = FattoMetrics.minTouchTarget).semantics(mergeDescendants = true) {
                contentDescription = if (interval == null) "Repeat" else "Repeat, $label"
            },
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(FattoMetrics.minTouchTarget).testTag("RepeatControl")) {
            Icon(
                Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(FattoMetrics.dateControlIcon),
                tint = if (interval == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            label, style = MaterialTheme.typography.bodyMedium,
            color =
                if (interval ==
                    null
                ) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
        )
    }
}

/** Edits a local draft; Apply does not mutate the task or series. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurrenceFields(
    rule: RecurrenceRule?,
    due: String?,
    onRuleChange: (RecurrenceRule?) -> Unit,
    error: String?,
    enabled: Boolean = true,
    allowNone: Boolean = true,
) {
    var showSettings by remember { mutableStateOf(false) }
    RepeatControl(rule?.interval, { showSettings = true }, enabled)
    if (showSettings && enabled) {
        RecurrenceSettingsSheet(rule, due, error, allowNone, { showSettings = false }) {
            onRuleChange(it)
            showSettings = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurrenceSettingsSheet(
    initial: RecurrenceRule?,
    due: String?,
    initialError: String?,
    allowNone: Boolean,
    onDismiss: () -> Unit,
    onApply: (RecurrenceRule?) -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    var expanded by remember { mutableStateOf(false) }
    var unitsExpanded by remember { mutableStateOf(false) }
    var untilPicker by remember { mutableStateOf(false) }
    val match = draft?.interval?.let { customInterval.matchEntire(it) }
    var number by remember { mutableStateOf(initial?.interval?.let { customInterval.matchEntire(it)?.groupValues?.get(1) }.orEmpty()) }
    var unit by remember { mutableStateOf(initial?.interval?.let { customInterval.matchEntire(it)?.groupValues?.get(2) } ?: "days") }
    val isCustom = draft != null && draft?.interval !in presets
    val unsupported = isCustom && match == null && draft?.interval == initial?.interval && !draft?.interval.isNullOrBlank()
    val validation = draft?.creationError(due)
    val message = validation ?: if (draft == initial) initialError else null
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(FattoSpacing.large),
            verticalArrangement = Arrangement.spacedBy(FattoSpacing.medium),
        ) {
            Text("Repeat", style = MaterialTheme.typography.headlineSmall)
            Box {
                TextButton(onClick = { expanded = true }, modifier = Modifier.heightIn(min = FattoMetrics.minTouchTarget)) {
                    Text("Repeat: ${draft?.let { presets[it.interval] ?: "Custom" } ?: "None"}")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    if (allowNone) {
                        DropdownMenuItem(text = { Text("None") }, onClick = {
                            draft = null
                            expanded = false
                        })
                    }
                    presets.forEach { (interval, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            draft = RecurrenceRule(interval, draft?.until)
                            expanded = false
                        })
                    }
                    DropdownMenuItem(text = { Text("Custom") }, onClick = {
                        draft = RecurrenceRule(number + unit, draft?.until)
                        expanded = false
                    })
                }
            }
            if (isCustom) {
                if (unsupported) {
                    Text("Imported rule: ${draft?.interval}", style = MaterialTheme.typography.bodyMedium)
                    Text("Choose a supported repeat rule to change these settings.", style = MaterialTheme.typography.bodyMedium)
                }
                TextField(
                    value = number,
                    onValueChange = {
                        number = it
                        draft = RecurrenceRule(number + unit, draft?.until)
                    },
                    label = { Text("Repeat every") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("RecurrenceIntervalInput"),
                    colors = FattoFieldDefaults.filledColors(),
                )
                Box {
                    TextButton(onClick = { unitsExpanded = true }) { Text(unit.replaceFirstChar { it.uppercase() }) }
                    DropdownMenu(expanded = unitsExpanded, onDismissRequest = { unitsExpanded = false }) {
                        listOf("days", "weeks", "months", "years").forEach { value ->
                            DropdownMenuItem(text = { Text(value.replaceFirstChar { it.uppercase() }) }, onClick = {
                                unit = value
                                draft = RecurrenceRule(number + unit, draft?.until)
                                unitsExpanded = false
                            })
                        }
                    }
                }
            }
            if (draft != null) {
                DatePickerIconButton("Until", draft?.until, Icons.Default.Event, { untilPicker = true })
                if (draft?.until != null) TextButton(onClick = { draft = draft?.copy(until = null) }) { Text("Clear Until") }
                Text("Future occurrences appear after Taskwarrior generates and syncs them.", style = MaterialTheme.typography.bodyMedium)
                if (draft?.until != null) {
                    Text("After this date, Taskwarrior deletes unfinished occurrences.", style = MaterialTheme.typography.bodySmall)
                }
                if (message != null) {
                    Text(
                        if (due == null) "Set a due date to repeat this task" else message,
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Button(onClick = { onApply(draft) }, enabled = draft == null || validation == null || (!allowNone && draft == initial)) {
                Text("Apply")
            }
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    }
    if (untilPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = DateTimeUtils.parseToInstant(draft?.until)?.toEpochMilli())
        DatePickerDialog(onDismissRequest = { untilPicker = false }, confirmButton = {
            TextButton(onClick = {
                draft = draft?.copy(until = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).toString() })
                untilPicker = false
            }) { Text("OK") }
        }, dismissButton = { TextButton(onClick = { untilPicker = false }) { Text("Cancel") } }) { DatePicker(state) }
    }
}
