package com.brokenpip3.fatto

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.brokenpip3.fatto.data.SettingsRepositoryImpl
import com.brokenpip3.fatto.data.TaskRepository
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.ui.calendar.CalendarScreen
import com.brokenpip3.fatto.ui.projects.ProjectsScreen
import com.brokenpip3.fatto.ui.settings.SettingsScreen
import com.brokenpip3.fatto.ui.tags.TagsScreen
import com.brokenpip3.fatto.ui.tasklist.AddTaskDialog
import com.brokenpip3.fatto.ui.tasklist.TaskDetailBottomSheet
import com.brokenpip3.fatto.ui.tasklist.TaskListScreen
import com.brokenpip3.fatto.ui.theme.NordicTheme
import com.brokenpip3.fatto.vm.SettingsViewModel
import com.brokenpip3.fatto.vm.TaskViewModel
import com.brokenpip3.fatto.worker.DailyNotificationWorker
import com.brokenpip3.fatto.worker.SyncWorker
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { _: Boolean ->
            // Permission result handled
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settingsRepository = SettingsRepositoryImpl(applicationContext)
        val taskRepository = TaskRepository(applicationContext, settingsRepository)

        val taskViewModel = TaskViewModel(taskRepository)
        val settingsViewModel = SettingsViewModel(settingsRepository)

        scheduleSync()
        requestNotificationPermission()

        lifecycleScope.launch {
            settingsRepository.notificationHour.collect { hour ->
                scheduleDailyNotifications(hour)
            }
        }

        setContent {
            NordicTheme {
                val navController = rememberNavController()

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar(
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp,
                        ) {
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentDestination = navBackStackEntry?.destination
                            val items = listOf("tasks", "projects", "calendar", "tags", "settings")
                            items.forEach { screen ->
                                val selected = currentDestination?.hierarchy?.any { it.route == screen } == true
                                NavigationBarItem(
                                    icon = {
                                        val icon =
                                            when (screen) {
                                                "tasks" -> Icons.AutoMirrored.Filled.List
                                                "projects" -> Icons.Default.AccountTree
                                                "calendar" -> Icons.Default.DateRange
                                                "tags" -> Icons.Default.Tag
                                                "settings" -> Icons.Default.Settings
                                                else -> Icons.AutoMirrored.Filled.List
                                            }
                                        Icon(icon, contentDescription = null)
                                    },
                                    label = {
                                        Text(
                                            text = screen.replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Visible,
                                        )
                                    },
                                    selected = selected,
                                    alwaysShowLabel = true,
                                    onClick = {
                                        navController.navigate(screen) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    colors =
                                        NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        ),
                                )
                            }
                        }
                    },
                ) { innerPadding ->
                    NavHost(navController, startDestination = "tasks", Modifier.padding(innerPadding)) {
                        composable("tasks") {
                            var showAddTaskDialog by remember { mutableStateOf(false) }
                            var selectedTask by remember { mutableStateOf<Task?>(null) }
                            val availableTags by taskViewModel.availableTags.collectAsState()
                            val hierarchicalProjects by taskViewModel.hierarchicalProjects.collectAsState()
                            val activeProject by taskViewModel.activeProject.collectAsState()
                            val selectedTags by taskViewModel.selectedTags.collectAsState()
                            val showInternalTags by taskViewModel.showInternalTags.collectAsState()
                            val firstDayOfWeek by settingsViewModel.firstDayOfWeek.collectAsState()
                            val confirmActions by settingsViewModel.confirmActions.collectAsState()

                            TaskListScreen(
                                viewModel = taskViewModel,
                                onAddTaskClick = { showAddTaskDialog = true },
                                onTaskClick = { selectedTask = it },
                                confirmActions = confirmActions,
                            )

                            if (showAddTaskDialog) {
                                AddTaskDialog(
                                    availableProjects = hierarchicalProjects.map { it.fullName },
                                    availableTags = availableTags.toList(),
                                    initialProject = activeProject,
                                    initialTags = selectedTags.toList(),
                                    onDismiss = { showAddTaskDialog = false },
                                    onConfirm = { desc, proj, tgs, w, d, sch, st, p, deps ->
                                        taskViewModel.addTask(desc, proj, tgs, w, d, sch, st, p, deps)
                                        showAddTaskDialog = false
                                    },
                                    firstDayOfWeek = firstDayOfWeek,
                                )
                            }

                            selectedTask?.let { task ->
                                TaskDetailBottomSheet(
                                    task = task,
                                    onDismiss = { selectedTask = null },
                                    onSave = { updatedTask ->
                                        taskViewModel.updateTask(updatedTask)
                                    },
                                    availableProjects = hierarchicalProjects.map { it.fullName },
                                    showInternalTags = showInternalTags,
                                    firstDayOfWeek = firstDayOfWeek,
                                )
                            }
                        }
                        composable("projects") {
                            ProjectsScreen(
                                viewModel = taskViewModel,
                                onProjectSelected = { navController.navigate("tasks") },
                            )
                        }
                        composable("calendar") {
                            CalendarScreen(
                                viewModel = taskViewModel,
                                onTaskClick = { task ->
                                    taskViewModel.onSearchQueryChange("uuid:${task.uuid}")
                                    navController.navigate("tasks") {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                            )
                        }
                        composable("tags") {
                            TagsScreen(
                                viewModel = taskViewModel,
                                onTagSelected = { navController.navigate("tasks") },
                            )
                        }
                        composable("settings") {
                            SettingsScreen(viewModel = settingsViewModel)
                        }
                    }
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun scheduleDailyNotifications(hour: Int) {
        val now = LocalDateTime.now()
        var target = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
        if (now.isAfter(target)) {
            target = target.plusDays(1)
        }
        val delay = Duration.between(now, target).toMinutes()

        val dailyNotificationRequest =
            PeriodicWorkRequestBuilder<DailyNotificationWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "DailyNotificationWork",
            ExistingPeriodicWorkPolicy.UPDATE,
            dailyNotificationRequest,
        )
    }

    private fun scheduleSync() {
        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        val syncRequest =
            PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "SyncWork",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            syncRequest,
        )
    }
}
