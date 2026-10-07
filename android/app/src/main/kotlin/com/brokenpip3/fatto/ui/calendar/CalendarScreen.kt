package com.brokenpip3.fatto.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.ui.tasklist.TaskItem
import com.brokenpip3.fatto.ui.tasklist.completionConfirmationMessage
import com.brokenpip3.fatto.ui.theme.FattoMetrics
import com.brokenpip3.fatto.ui.theme.FattoSpacing
import com.brokenpip3.fatto.ui.theme.effectiveFontScale
import com.brokenpip3.fatto.vm.TaskViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: TaskViewModel,
    onTaskClick: (Task) -> Unit,
) {
    val tasksByDate by viewModel.tasksByDate.collectAsState()
    val allTasks by viewModel.allTasks.collectAsState()
    val firstDayOfWeekSetting by viewModel.firstDayOfWeek.collectAsState()
    val showPriorityBadge by viewModel.showPriorityBadge.collectAsState()
    val showUrgencyBar by viewModel.showUrgencyBar.collectAsState()
    val largeFontScale = effectiveFontScale() >= 1.5f
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var taskToComplete by remember { mutableStateOf<Task?>(null) }

    val daysInMonth = currentMonth.lengthOfMonth()
    // Convert Calendar constant (Sun=1, Mon=2) to DayOfWeek value (Mon=1, Sun=7)
    val startDayOfWeek =
        if (firstDayOfWeekSetting == Calendar.SUNDAY) DayOfWeek.SUNDAY.value else firstDayOfWeekSetting - 1
    val offset = (currentMonth.atDay(1).dayOfWeek.value - startDayOfWeek + 7) % 7

    val days =
        remember(currentMonth, firstDayOfWeekSetting) {
            val list = mutableListOf<LocalDate?>()
            for (i in 0 until offset) list.add(null)
            for (i in 1..daysInMonth) list.add(currentMonth.atDay(i))
            list
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendar", style = MaterialTheme.typography.headlineSmall) },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(vertical = FattoSpacing.large),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val gridMinimumWidth = FattoMetrics.minTouchTarget * 7 + FattoSpacing.large * 2
                val gridHorizontalPadding = if (maxWidth >= gridMinimumWidth) FattoSpacing.large else FattoSpacing.none

                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = FattoSpacing.large),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { currentMonth = currentMonth.minusMonths(1) },
                            modifier = Modifier.size(FattoMetrics.minTouchTarget),
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month")
                        }
                        Text(
                            text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                        )
                        IconButton(
                            onClick = { currentMonth = currentMonth.plusMonths(1) },
                            modifier = Modifier.size(FattoMetrics.minTouchTarget),
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next Month")
                        }
                    }

                    Spacer(modifier = Modifier.height(FattoSpacing.large))

                    // Weekday labels
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = gridHorizontalPadding),
                    ) {
                        val allWeekdays =
                            listOf(
                                "Monday" to "Mon",
                                "Tuesday" to "Tue",
                                "Wednesday" to "Wed",
                                "Thursday" to "Thu",
                                "Friday" to "Fri",
                                "Saturday" to "Sat",
                                "Sunday" to "Sun",
                            )
                        val weekdays =
                            if (firstDayOfWeekSetting == Calendar.MONDAY) {
                                allWeekdays
                            } else {
                                listOf(allWeekdays.last()) + allWeekdays.dropLast(1)
                            }
                        weekdays.forEach { (fullName, shortName) ->
                            Text(
                                text = if (largeFontScale) fullName.take(1) else shortName,
                                modifier = Modifier.weight(1f).semantics { contentDescription = fullName },
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(FattoSpacing.small))

                    // Grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = gridHorizontalPadding),
                        verticalArrangement = Arrangement.spacedBy(FattoSpacing.small),
                        horizontalArrangement = Arrangement.spacedBy(FattoSpacing.none),
                    ) {
                        items(days) { date ->
                            if (date != null) {
                                val hasTasks = tasksByDate.containsKey(date)
                                val isToday = date == LocalDate.now()
                                val isSelected = selectedDate == date

                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = FattoMetrics.minTouchTarget)
                                            .clickable(enabled = hasTasks) {
                                                if (hasTasks) selectedDate = date
                                            }
                                            .semantics {
                                                contentDescription =
                                                    buildString {
                                                        append(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")))
                                                        append(if (hasTasks) ", tasks available" else ", no tasks")
                                                        if (isToday) append(", today")
                                                    }
                                                selected = isSelected
                                            },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .widthIn(max = FattoMetrics.minTouchTarget)
                                                .fillMaxWidth()
                                                .height(FattoMetrics.minTouchTarget)
                                                .clip(CircleShape)
                                                .background(
                                                    when {
                                                        isToday || isSelected -> MaterialTheme.colorScheme.primaryContainer
                                                        hasTasks -> MaterialTheme.colorScheme.surfaceVariant
                                                        else -> MaterialTheme.colorScheme.surface
                                                    },
                                                ),
                                    )
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = date.dayOfMonth.toString(),
                                            fontWeight = if (hasTasks || isToday) FontWeight.SemiBold else FontWeight.Normal,
                                            color =
                                                if (isToday || isSelected) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                        )
                                        if (hasTasks) {
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .size(FattoSpacing.xSmall)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.primary),
                                            )
                                        }
                                    }
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxWidth().heightIn(min = FattoMetrics.minTouchTarget))
                            }
                        }
                    }
                }
            }
        }

        // Bottom Sheet
        if (selectedDate != null) {
            val tasksForDate = tasksByDate[selectedDate] ?: emptyList()
            val maxUrgency =
                tasksByDate.values.flatten().maxOfOrNull { it.urgency } ?: 0.0f
            ModalBottomSheet(
                onDismissRequest = { selectedDate = null },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = FattoSpacing.large)
                            .padding(bottom = FattoSpacing.xxLarge),
                ) {
                    Text(
                        text = "Tasks for $selectedDate",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = FattoSpacing.large),
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(FattoSpacing.small),
                    ) {
                        items(tasksForDate) { task ->
                            TaskItem(
                                task = task,
                                onClick = {
                                    onTaskClick(task)
                                    selectedDate = null
                                },
                                onComplete = {
                                    if (task.isBlocked) taskToComplete = task else viewModel.completeTask(task.uuid)
                                },
                                onDelete = { viewModel.deleteTask(task.uuid) },
                                maxUrgency = maxUrgency,
                                showPriorityBadge = showPriorityBadge,
                                showUrgencyBar = showUrgencyBar,
                            )
                        }
                    }
                }
            }
        }

        taskToComplete?.let { task ->
            AlertDialog(
                onDismissRequest = { taskToComplete = null },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                title = { Text("Complete Task", style = MaterialTheme.typography.headlineSmall) },
                text = { Text(completionConfirmationMessage(task, allTasks), style = MaterialTheme.typography.bodyLarge) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.completeTask(task.uuid)
                        taskToComplete = null
                    }) { Text("Confirm") }
                },
                dismissButton = {
                    TextButton(onClick = { taskToComplete = null }) { Text("Cancel") }
                },
            )
        }
    }
}
