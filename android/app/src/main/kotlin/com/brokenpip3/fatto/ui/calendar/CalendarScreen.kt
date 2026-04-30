package com.brokenpip3.fatto.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.ui.tasklist.TaskItem
import com.brokenpip3.fatto.vm.TaskViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
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
    val firstDayOfWeekSetting by viewModel.firstDayOfWeek.collectAsState()
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

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
                    .padding(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month")
                }
                Text(
                    text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Month")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Weekday labels
            Row(modifier = Modifier.fillMaxWidth()) {
                val allLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                val weekdays =
                    if (firstDayOfWeekSetting == Calendar.MONDAY) {
                        allLabels
                    } else {
                        listOf("Sun") + allLabels.dropLast(1)
                    }
                weekdays.forEach { day ->
                    Text(
                        text = day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(days) { date ->
                    if (date != null) {
                        val hasTasks = tasksByDate.containsKey(date)
                        val isToday = date == LocalDate.now()

                        Box(
                            modifier =
                                Modifier
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .background(
                                        if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    )
                                    .clickable {
                                        if (hasTasks) selectedDate = date
                                    },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    color =
                                        if (isToday) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                )
                                if (hasTasks) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(4.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                    )
                                }
                            }
                        }
                    } else {
                        Box(modifier = Modifier.aspectRatio(1f))
                    }
                }
            }
        }

        // Bottom Sheet
        if (selectedDate != null) {
            val tasksForDate = tasksByDate[selectedDate] ?: emptyList()
            ModalBottomSheet(
                onDismissRequest = { selectedDate = null },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 32.dp),
                ) {
                    Text(
                        text = "Tasks for $selectedDate",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(tasksForDate) { task ->
                            TaskItem(
                                task = task,
                                onClick = {
                                    onTaskClick(task)
                                    selectedDate = null
                                },
                                onComplete = { viewModel.completeTask(task.uuid) },
                                onDelete = { viewModel.deleteTask(task.uuid) },
                            )
                        }
                    }
                }
            }
        }
    }
}
