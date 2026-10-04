package com.brokenpip3.fatto.ui.tasklist

import android.content.res.Configuration
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.brokenpip3.fatto.data.DateTimeUtils
import com.brokenpip3.fatto.ui.theme.FattoMetrics
import com.brokenpip3.fatto.ui.theme.FattoOpacity
import com.brokenpip3.fatto.ui.theme.FattoSpacing
import com.brokenpip3.fatto.ui.theme.FattoStroke
import com.brokenpip3.fatto.ui.theme.effectiveFontScale
import com.brokenpip3.fatto.ui.theme.toNordicColor
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Calendar
import java.util.Locale

enum class DatePickerType { DUE, WAIT, SCHEDULED }

/** Reserve a minimum gap while spreading each measured row across the form. */
private val dateControlArrangement =
    object : Arrangement.Horizontal {
        override val spacing = FattoSpacing.small

        override fun Density.arrange(
            totalSize: Int,
            sizes: IntArray,
            layoutDirection: LayoutDirection,
            outPositions: IntArray,
        ) {
            with(Arrangement.SpaceBetween) { arrange(totalSize, sizes, layoutDirection, outPositions) }
        }
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskDateAndPriorityControls(
    priority: String?,
    due: String?,
    scheduled: String?,
    wait: String?,
    onPriorityChange: (String?) -> Unit,
    onDateClick: (DatePickerType) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingControl: (@Composable () -> Unit)? = null,
) {
    val compact = trailingControl != null
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FattoSpacing.small)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (compact) Arrangement.SpaceBetween else dateControlArrangement,
            verticalArrangement = Arrangement.spacedBy(FattoSpacing.small),
            maxItemsInEachRow = if (trailingControl == null) 4 else 5,
        ) {
            PriorityIconButton(priority = priority, onPriorityChange = onPriorityChange, enabled = enabled, compact = compact)
            DatePickerIconButton(
                label = "Due",
                date = due,
                icon = Icons.Default.Event,
                enabled = enabled,
                compact = compact,
                onClick = { onDateClick(DatePickerType.DUE) },
            )
            DatePickerIconButton(
                label = "Sch",
                date = scheduled,
                icon = Icons.Default.Schedule,
                enabled = enabled,
                compact = compact,
                onClick = { onDateClick(DatePickerType.SCHEDULED) },
            )
            DatePickerIconButton(
                label = "Wait",
                date = wait,
                icon = Icons.Default.CalendarMonth,
                enabled = enabled,
                compact = compact,
                onClick = { onDateClick(DatePickerType.WAIT) },
            )
            trailingControl?.invoke()
        }
    }
}

@Composable
fun TaskTagActions(
    onAdd: () -> Unit,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    addActionModifier: Modifier = Modifier,
    addContentDescription: String = "Add tag",
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small),
    ) {
        TextButton(
            onClick = onAdd,
            enabled = enabled,
            modifier =
                addActionModifier.heightIn(min = FattoMetrics.minTouchTarget).semantics {
                    contentDescription = addContentDescription
                },
        ) {
            if (effectiveFontScale() >= FattoMetrics.largeFontScale) {
                Icon(Icons.Default.Add, contentDescription = null)
            } else {
                Text("Add", style = MaterialTheme.typography.labelLarge)
            }
        }
        IconButton(onClick = onSelect, enabled = enabled, modifier = Modifier.testTag("SelectTagsButton")) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Select tags")
        }
    }
}

@Composable
fun PriorityIconButton(
    priority: String?,
    onPriorityChange: (String?) -> Unit,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val controlWidth = if (compact) FattoMetrics.minTouchTarget else FattoMetrics.dateControlMinWidth
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.widthIn(min = controlWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            IconButton(onClick = {
                expanded = true
            }, enabled = enabled, modifier = Modifier.size(controlWidth)) {
                Icon(
                    imageVector = Icons.Outlined.Flag,
                    contentDescription = "Set Priority",
                    modifier = Modifier.size(FattoMetrics.dateControlIcon),
                    tint =
                        if (priority != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
            DropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("High") },
                    modifier = Modifier.semantics { selected = priority == "H" },
                    trailingIcon = {
                        if (priority == "H") {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FattoMetrics.inlineIcon),
                            )
                        }
                    },
                    onClick = {
                        onPriorityChange("H")
                        expanded = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("Medium") },
                    modifier = Modifier.semantics { selected = priority == "M" },
                    trailingIcon = {
                        if (priority == "M") {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FattoMetrics.inlineIcon),
                            )
                        }
                    },
                    onClick = {
                        onPriorityChange("M")
                        expanded = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("Low") },
                    modifier = Modifier.semantics { selected = priority == "L" },
                    trailingIcon = {
                        if (priority == "L") {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FattoMetrics.inlineIcon),
                            )
                        }
                    },
                    onClick = {
                        onPriorityChange("L")
                        expanded = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("None") },
                    modifier = Modifier.semantics { selected = priority == null },
                    trailingIcon = {
                        if (priority == null) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FattoMetrics.inlineIcon),
                            )
                        }
                    },
                    onClick = {
                        onPriorityChange(null)
                        expanded = false
                    },
                )
            }
        }
        Text(
            text =
                when (priority) {
                    "H" -> "High"
                    "M" -> "Med"
                    "L" -> "Low"
                    else -> "Priority"
                },
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (priority != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

@Composable
fun DatePickerIconButton(
    label: String,
    date: String?,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val controlWidth = if (compact) FattoMetrics.minTouchTarget else FattoMetrics.dateControlMinWidth
    val formattedDate = DateTimeUtils.formatLocalDateTime(date)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier.widthIn(min = controlWidth).semantics(mergeDescendants = true) {
                contentDescription = if (formattedDate == null) label else "$label, $formattedDate"
            },
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(controlWidth)) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(FattoMetrics.dateControlIcon),
                tint =
                    if (date != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
        Text(
            text = formattedDate ?: label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (date != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SuggestionChip(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.heightIn(min = FattoMetrics.minTouchTarget).widthIn(min = FattoMetrics.minTouchTarget),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = FattoSpacing.small, vertical = FattoSpacing.xSmall),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun TagChip(
    tag: String,
    onRemove: (() -> Unit)? = null,
) {
    Surface(
        color = tag.toNordicColor().copy(alpha = FattoOpacity.tint),
        border = BorderStroke(FattoStroke.subtle, tag.toNordicColor()),
        shape = MaterialTheme.shapes.small,
        modifier =
            if (onRemove != null) {
                Modifier
                    .heightIn(min = FattoMetrics.minTouchTarget)
                    .widthIn(min = FattoMetrics.minTouchTarget)
                    .clickable { onRemove() }
            } else {
                Modifier
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = FattoSpacing.small, vertical = FattoSpacing.xSmall),
        ) {
            Text(
                text = tag,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            if (onRemove != null) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove $tag",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = FattoSpacing.small).size(FattoMetrics.inlineIcon),
                )
            }
        }
    }
}

@Composable
fun AccordionSection(
    title: String,
    icon: ImageVector,
    count: Int?,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {}
                    .heightIn(min = FattoMetrics.minTouchTarget)
                    .clickable { onToggle() }
                    .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                    .padding(vertical = FattoSpacing.large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(FattoMetrics.inlineIcon),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(FattoSpacing.small))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (count != null && count > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = count.toString(),
                        modifier = Modifier.padding(horizontal = FattoSpacing.small, vertical = FattoSpacing.xSmall),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Icon(
                imageVector =
                    if (expanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                contentDescription = null,
                modifier = Modifier.size(FattoMetrics.icon),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(
            thickness = FattoStroke.subtle,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(top = FattoSpacing.small, bottom = FattoSpacing.xSmall),
                verticalArrangement = Arrangement.spacedBy(FattoSpacing.small),
                content = content,
            )
        }
    }
}

/**
 * Date picker with an optional time of day. Without a time the stored value is a plain
 * date (what Taskwarrior treats as the default for `due:2026-04-28`).
 *
 * [current] is the RFC-3339 value being edited, used to preselect the date and time.
 * [onConfirm] receives the new RFC-3339 value.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(
    current: String?,
    firstDayOfWeek: Int,
    onConfirm: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val currentDate = remember(current) { DateTimeUtils.parseToLocalDate(current) }
    val currentTime = remember(current) { DateTimeUtils.parseToLocalTime(current) }

    val datePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = currentDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
    val timePickerState =
        rememberTimePickerState(
            initialHour = currentTime?.hour ?: 9,
            initialMinute = currentTime?.minute ?: 0,
            is24Hour = DateFormat.is24HourFormat(LocalContext.current),
        )
    var withTime by remember { mutableStateOf(currentTime != null) }

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = datePickerState.selectedDateMillis
                if (millis == null) {
                    onDismiss()
                } else {
                    val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    val time = if (withTime) LocalTime.of(timePickerState.hour, timePickerState.minute) else null
                    onConfirm(DateTimeUtils.toStoredTimestamp(date, time))
                }
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onClear) {
                Text("Clear")
            }
        },
    ) {
        val currentConfig = LocalConfiguration.current
        val config = Configuration(currentConfig)
        val targetLocale = if (firstDayOfWeek == Calendar.SUNDAY) Locale.US else Locale.UK
        config.setLocale(targetLocale)

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            CompositionLocalProvider(LocalConfiguration provides config) {
                DatePicker(state = datePickerState)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Set time", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = withTime, onCheckedChange = { withTime = it })
            }
            AnimatedVisibility(visible = withTime) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    TimeInput(state = timePickerState)
                }
            }
        }
    }
}
