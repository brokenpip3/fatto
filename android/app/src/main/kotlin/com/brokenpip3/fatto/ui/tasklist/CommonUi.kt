package com.brokenpip3.fatto.ui.tasklist

import android.content.res.Configuration
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.brokenpip3.fatto.data.DateTimeUtils
import com.brokenpip3.fatto.ui.theme.toNordicColor
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Calendar
import java.util.Locale

enum class DatePickerType { DUE, WAIT, SCHEDULED }

@Composable
fun PriorityIconButton(
    priority: String?,
    onPriorityChange: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(
                    imageVector = Icons.Outlined.Flag,
                    contentDescription = "Set Priority",
                    tint =
                        if (priority != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        },
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("High") },
                    onClick = {
                        onPriorityChange("H")
                        expanded = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("Medium") },
                    onClick = {
                        onPriorityChange("M")
                        expanded = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("Low") },
                    onClick = {
                        onPriorityChange("L")
                        expanded = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("None") },
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
            style = MaterialTheme.typography.labelSmall,
            color =
                if (priority != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
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
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint =
                    if (date != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = 0.6f,
                        )
                    },
            )
        }
        Text(
            text = DateTimeUtils.formatLocalDateTime(date) ?: label,
            style = MaterialTheme.typography.labelSmall,
            color = if (date != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun TagChip(
    tag: String,
    onRemove: (() -> Unit)? = null,
) {
    Surface(
        color = tag.toNordicColor().copy(alpha = 0.1f),
        shape = RoundedCornerShape(4.dp),
        modifier =
            if (onRemove != null) {
                Modifier.clickable { onRemove() }
            } else {
                Modifier
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = tag,
                style = MaterialTheme.typography.labelSmall,
                color = tag.toNordicColor(),
                fontWeight = FontWeight.Bold,
            )
            if (onRemove != null) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove $tag",
                    tint = tag.toNordicColor(),
                    modifier = Modifier.padding(start = 4.dp).size(12.dp),
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
                    .clickable { onToggle() }
                    .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (count != null && count > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = count.toString(),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        )
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
