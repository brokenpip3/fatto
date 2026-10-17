package com.brokenpip3.fatto

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.brokenpip3.fatto.data.SettingsRepositoryImpl
import com.brokenpip3.fatto.data.ShareIntentParser
import com.brokenpip3.fatto.data.SyncDiagnosticsRepositoryImpl
import com.brokenpip3.fatto.data.TaskRepository
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.notification.NotificationNavigation
import com.brokenpip3.fatto.notification.ReviewReminderScheduler
import com.brokenpip3.fatto.ui.calendar.CalendarScreen
import com.brokenpip3.fatto.ui.projects.ProjectsScreen
import com.brokenpip3.fatto.ui.settings.SettingsScreen
import com.brokenpip3.fatto.ui.tags.TagsScreen
import com.brokenpip3.fatto.ui.tasklist.AddTaskDialog
import com.brokenpip3.fatto.ui.tasklist.RecurrenceTemplateSheet
import com.brokenpip3.fatto.ui.tasklist.ReviewSheet
import com.brokenpip3.fatto.ui.tasklist.SeriesEditorActions
import com.brokenpip3.fatto.ui.tasklist.TaskDetailBottomSheet
import com.brokenpip3.fatto.ui.tasklist.TaskListScreen
import com.brokenpip3.fatto.ui.theme.FattoElevation
import com.brokenpip3.fatto.ui.theme.FattoMetrics
import com.brokenpip3.fatto.ui.theme.NordicTheme
import com.brokenpip3.fatto.ui.theme.ThemeMode
import com.brokenpip3.fatto.ui.theme.effectiveFontScale
import com.brokenpip3.fatto.vm.ReviewStage
import com.brokenpip3.fatto.vm.ReviewViewModel
import com.brokenpip3.fatto.vm.SettingsViewModel
import com.brokenpip3.fatto.vm.TaskViewModel
import com.brokenpip3.fatto.worker.DailyNotificationWorker
import com.brokenpip3.fatto.worker.SyncWorker
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private var notificationTaskUuid by mutableStateOf<String?>(null)
    private var pendingShareDescription by mutableStateOf<String?>(null)
    private var notificationOpenReview by mutableStateOf(false)

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
        val reviewScheduler = ReviewReminderScheduler(applicationContext, settingsRepository)
        val reviewViewModel =
            ViewModelProvider(
                this,
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return ReviewViewModel(
                            taskRepository, settingsRepository,
                            onProductiveSessionEnded = reviewScheduler::onProductiveSessionEnded,
                        ) as T
                    }
                },
            )[ReviewViewModel::class.java]
        lifecycleScope.launch {
            reviewViewModel.attach(taskRepository, settingsRepository, reviewScheduler::onProductiveSessionEnded)
        }
        lifecycleScope.launch { settingsRepository.reviewSettings.collect { reviewScheduler.reconcile() } }

        val taskViewModel = TaskViewModel(taskRepository)
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) taskViewModel.reloadTasks()
            },
        )
        val diagnosticsRepository = SyncDiagnosticsRepositoryImpl(applicationContext)
        val settingsViewModel =
            SettingsViewModel(
                repository = settingsRepository,
                syncAction = { taskRepository.sync() },
                diagnosticsRepository = diagnosticsRepository,
            )
        notificationTaskUuid = intent.getStringExtra(NotificationNavigation.EXTRA_TASK_UUID)
        notificationOpenReview = intent.getBooleanExtra(NotificationNavigation.EXTRA_OPEN_REVIEW, false)
        intent.removeExtra(NotificationNavigation.EXTRA_OPEN_REVIEW)
        pendingShareDescription = ShareIntentParser.descriptionFrom(intent.getStringExtra(Intent.EXTRA_TEXT))

        scheduleSync()
        requestNotificationPermission()

        lifecycleScope.launch {
            settingsRepository.notificationHour.collect { hour ->
                scheduleDailyNotifications(hour)
            }
        }

        setContent {
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val fontSizePercent by settingsViewModel.fontSizePercent.collectAsState()
            val systemDarkTheme = isSystemInDarkTheme()
            val darkTheme =
                when (themeMode) {
                    ThemeMode.SYSTEM -> systemDarkTheme
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }

            SideEffect {
                val systemBarStyle =
                    if (darkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    }
                enableEdgeToEdge(
                    statusBarStyle = systemBarStyle,
                    navigationBarStyle = systemBarStyle,
                )
            }

            NordicTheme(darkTheme = darkTheme, fontSizePercent = fontSizePercent) {
                val compactNavigation = effectiveFontScale() >= 1.5f
                val navController = rememberNavController()
                val taskUuid = notificationTaskUuid
                LaunchedEffect(notificationOpenReview) {
                    if (notificationOpenReview) {
                        navController.navigate("tasks") { launchSingleTop = true }
                        reviewViewModel.open()
                        notificationOpenReview = false
                    }
                }

                LaunchedEffect(taskUuid) {
                    taskUuid?.let { uuid ->
                        taskViewModel.clearFilters()
                        taskViewModel.onSearchQueryChange(NotificationNavigation.taskSearchQuery(uuid))
                        navController.navigate("tasks") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                        notificationTaskUuid = null
                    }
                }

                LaunchedEffect(pendingShareDescription) {
                    if (pendingShareDescription != null) {
                        navController.navigate("tasks") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }

                Scaffold(
                    modifier =
                        Modifier
                            .background(MaterialTheme.colorScheme.surface)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                            .testTag("AppRoot"),
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = FattoElevation.none,
                        ) {
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentDestination = navBackStackEntry?.destination
                            val items = listOf("tasks", "projects", "calendar", "tags", "settings")
                            items.forEach { screen ->
                                val selected = currentDestination?.hierarchy?.any { it.route?.substringBefore("?") == screen } == true
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
                                        Icon(
                                            icon,
                                            contentDescription = screen.replaceFirstChar { it.uppercase() },
                                            modifier = Modifier.size(FattoMetrics.icon),
                                        )
                                    },
                                    label = {
                                        if (!compactNavigation) {
                                            Text(
                                                text = screen.replaceFirstChar { it.uppercase() },
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    },
                                    selected = selected,
                                    alwaysShowLabel = !compactNavigation,
                                    onClick = {
                                        navController.navigate(screen) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = screen != "tasks"
                                        }
                                    },
                                    colors =
                                        NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        ),
                                )
                            }
                        }
                    },
                ) { innerPadding ->
                    NavHost(
                        navController, startDestination = "tasks",
                        Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
                    ) {
                        composable("tasks") {
                            var showAddTaskDialog by remember { mutableStateOf(false) }
                            var selectedTask by remember { mutableStateOf<Task?>(null) }
                            var editingReview by remember { mutableStateOf(false) }
                            val reviewState by reviewViewModel.state.collectAsState()
                            var selectedSeries by remember { mutableStateOf<Task?>(null) }
                            var dialogInitialDescription by remember { mutableStateOf<String?>(null) }
                            val availableTags by taskViewModel.availableTags.collectAsState()
                            val selectableProjects by taskViewModel.selectableProjects.collectAsState()
                            val allTasks by taskViewModel.allTasks.collectAsState()
                            val activeProject by taskViewModel.activeProject.collectAsState()
                            val selectedTags by taskViewModel.selectedTags.collectAsState()
                            val defaultProjectEnabled by settingsViewModel.defaultProjectEnabled.collectAsState()
                            val defaultProject by settingsViewModel.defaultProject.collectAsState()
                            val showInternalTags by taskViewModel.showInternalTags.collectAsState()
                            val firstDayOfWeek by settingsViewModel.firstDayOfWeek.collectAsState()
                            val confirmActions by settingsViewModel.confirmActions.collectAsState()
                            val showCompleted by settingsViewModel.showCompleted.collectAsState()
                            val swipeStartToEndAction by settingsViewModel.swipeStartToEndAction.collectAsState()
                            val swipeEndToStartAction by settingsViewModel.swipeEndToStartAction.collectAsState()

                            LaunchedEffect(pendingShareDescription) {
                                val shared = pendingShareDescription
                                if (shared != null) {
                                    dialogInitialDescription = shared
                                    pendingShareDescription = null
                                    showAddTaskDialog = true
                                }
                            }

                            TaskListScreen(
                                viewModel = taskViewModel,
                                onAddTaskClick = {
                                    dialogInitialDescription = null
                                    showAddTaskDialog = true
                                },
                                onTaskClick = {
                                    editingReview = false
                                    selectedTask = it
                                },
                                onReviewClick = reviewViewModel::open,
                                onSeriesClick = { selectedSeries = it },
                                onManageContexts = {
                                    navController.navigate("settings?section=contexts") {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                    }
                                },
                                confirmActions = confirmActions,
                                swipeStartToEndAction = swipeStartToEndAction,
                                swipeEndToStartAction = swipeEndToStartAction,
                            )

                            if (reviewState.stage != ReviewStage.CLOSED && !(editingReview && selectedTask != null)) {
                                ReviewSheet(
                                    reviewState, reviewViewModel::perform, reviewViewModel::acceptNotice,
                                    reviewViewModel::start, onEdit = {
                                        editingReview = true
                                        selectedTask = it
                                    },
                                    onSettings = { navController.navigate("settings?section=review") { launchSingleTop = true } },
                                    onDismiss = reviewViewModel::finish,
                                )
                            }

                            if (showAddTaskDialog) {
                                val initialProject =
                                    activeProject ?: defaultProject.takeIf { defaultProjectEnabled }
                                AddTaskDialog(
                                    availableProjects = selectableProjects,
                                    availableTags = availableTags.toList(),
                                    initialProject = initialProject,
                                    initialTags = selectedTags.toList(),
                                    initialDescription = dialogInitialDescription ?: "",
                                    onDismiss = { showAddTaskDialog = false },
                                    onConfirm = { desc, proj, tgs, w, d, sch, st, p, deps, openEditor ->
                                        taskViewModel.addTask(desc, proj, tgs, w, d, sch, st, p, deps, openEditor)
                                        showAddTaskDialog = false
                                    },
                                    firstDayOfWeek = firstDayOfWeek,
                                    onCreate = { props, rule, openEditor -> taskViewModel.createTask(props, rule, openEditor) },
                                )
                            }

                            selectedSeries?.let { series ->
                                RecurrenceTemplateSheet(
                                    series, allTasks, onDismiss = { selectedSeries = null },
                                    onSave = { patch, rule, replace -> taskViewModel.saveSeries(patch, rule, replace) },
                                    onStop = { taskViewModel.stopSeries(series.uuid) },
                                    showInternalTags = showInternalTags,
                                )
                            }

                            selectedTask?.let { task ->
                                TaskDetailBottomSheet(
                                    task = task,
                                    onDismiss = { selectedTask = null },
                                    seriesActions =
                                        SeriesEditorActions(
                                            save = { patch, rule, replace -> taskViewModel.saveSeries(patch, rule, replace) },
                                            stop = { uuid -> taskViewModel.stopSeries(uuid) },
                                        ),
                                    autoWaiting = taskViewModel.autoWaiting.collectAsState().value,
                                    onSave = { updatedTask ->
                                        if (editingReview) reviewViewModel.saveEdit(updatedTask) else taskViewModel.saveTask(updatedTask)
                                    },
                                    availableProjects = selectableProjects,
                                    availableTags = availableTags.toList(),
                                    showInternalTags = showInternalTags,
                                    firstDayOfWeek = firstDayOfWeek,
                                    onAddAnnotation = { uuid, desc ->
                                        if (editingReview) {
                                            reviewViewModel.addAnnotation(uuid, desc)
                                        } else {
                                            taskViewModel.addAnnotation(uuid, desc)
                                        }
                                    },
                                    onRemoveAnnotation = { uuid, entry ->
                                        if (editingReview) {
                                            reviewViewModel.removeAnnotation(uuid, entry)
                                        } else {
                                            taskViewModel.removeAnnotation(uuid, entry)
                                        }
                                    },
                                    allTasks = allTasks,
                                    recurrenceDiagnostics = taskViewModel.recurrenceDiagnostics.collectAsState().value,
                                    showCompleted = showCompleted,
                                    onAddDependencies = { uuid, deps ->
                                        if (editingReview) {
                                            reviewViewModel.addDependencies(task.uuid, uuid, deps)
                                        } else {
                                            taskViewModel.addDependencies(uuid, deps)
                                        }
                                    },
                                    onRemoveDependency = { uuid, dep ->
                                        if (editingReview) {
                                            reviewViewModel.removeDependency(task.uuid, uuid, dep)
                                        } else {
                                            taskViewModel.removeDependency(uuid, dep)
                                        }
                                    },
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
                        composable(
                            route = "settings?section={section}",
                            arguments =
                                listOf(
                                    navArgument("section") {
                                        type = NavType.StringType
                                        nullable = true
                                    },
                                ),
                        ) { entry ->
                            val availableTags by taskViewModel.availableTags.collectAsState()
                            val selectableProjects by taskViewModel.selectableProjects.collectAsState()

                            SettingsScreen(
                                viewModel = settingsViewModel,
                                openContexts = entry.arguments?.getString("section") == "contexts",
                                openReview = entry.arguments?.getString("section") == "review",
                                availableProjects = selectableProjects,
                                availableTags = availableTags,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationTaskUuid = intent.getStringExtra(NotificationNavigation.EXTRA_TASK_UUID)
        notificationOpenReview = intent.getBooleanExtra(NotificationNavigation.EXTRA_OPEN_REVIEW, false)
        intent.removeExtra(NotificationNavigation.EXTRA_OPEN_REVIEW)
        pendingShareDescription = ShareIntentParser.descriptionFrom(intent.getStringExtra(Intent.EXTRA_TEXT))
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
