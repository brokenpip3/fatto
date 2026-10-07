package com.brokenpip3.fatto.ui.tasklist

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = dateControlArrangement,
        verticalArrangement = Arrangement.spacedBy(FattoSpacing.small),
        maxItemsInEachRow = 4,
    ) {
        PriorityIconButton(priority = priority, onPriorityChange = onPriorityChange)
        DatePickerIconButton(
            label = "Due",
            date = due,
            icon = Icons.Default.Event,
            onClick = { onDateClick(DatePickerType.DUE) },
        )
        DatePickerIconButton(
            label = "Sch",
            date = scheduled,
            icon = Icons.Default.Schedule,
            onClick = { onDateClick(DatePickerType.SCHEDULED) },
        )
        DatePickerIconButton(
            label = "Wait",
            date = wait,
            icon = Icons.Default.CalendarMonth,
            onClick = { onDateClick(DatePickerType.WAIT) },
        )
    }
}

@Composable
fun TaskTagActions(
    onAdd: () -> Unit,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    addActionModifier: Modifier = Modifier,
    addContentDescription: String = "Add tag",
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small),
    ) {
        TextButton(
            onClick = onAdd,
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
        IconButton(onClick = onSelect, modifier = Modifier.testTag("SelectTagsButton")) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Select tags")
        }
    }
}

@Composable
fun PriorityIconButton(
    priority: String?,
    onPriorityChange: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.widthIn(min = FattoMetrics.dateControlMinWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            IconButton(onClick = { expanded = true }, modifier = Modifier.size(FattoMetrics.dateControlMinWidth)) {
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
                expanded = expanded,
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
) {
    val formattedDate = DateTimeUtils.formatLocalDate(date)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier.widthIn(min = FattoMetrics.dateControlMinWidth).semantics(mergeDescendants = true) {
                contentDescription = if (formattedDate == null) label else "$label, $formattedDate"
            },
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(FattoMetrics.dateControlMinWidth)) {
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
