package com.brokenpip3.fatto.ui.tasklist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.brokenpip3.fatto.data.TaskSwipeAction
import com.brokenpip3.fatto.data.model.INTERNAL_TAGS
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.model.TaskContext
import com.brokenpip3.fatto.ui.theme.FattoElevation
import com.brokenpip3.fatto.ui.theme.FattoFieldDefaults
import com.brokenpip3.fatto.ui.theme.FattoMetrics
import com.brokenpip3.fatto.ui.theme.FattoOpacity
import com.brokenpip3.fatto.ui.theme.FattoSpacing
import com.brokenpip3.fatto.ui.theme.FattoStroke
import com.brokenpip3.fatto.ui.theme.effectiveFontScale
import com.brokenpip3.fatto.ui.theme.toNordicColor
import com.brokenpip3.fatto.vm.SortDirection
import com.brokenpip3.fatto.vm.SortOrder
import com.brokenpip3.fatto.vm.TaskViewModel
import uniffi.taskchampion_android.TaskStatus

private const val PULL_TO_REFRESH_THRESHOLD = 100f

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Suppress("detekt.LongMethod")
@Composable
fun TaskListScreen(
    viewModel: TaskViewModel,
    onTaskClick: (Task) -> Unit,
    onAddTaskClick: () -> Unit,
    onManageContexts: () -> Unit,
    confirmActions: Boolean,
    swipeStartToEndAction: TaskSwipeAction,
    swipeEndToStartAction: TaskSwipeAction,
) {
    val largeFontScale = effectiveFontScale() >= 1.5f
    val tasks by viewModel.activeTasks.collectAsState()
    val allTasks by viewModel.allTasks.collectAsState()
    val waitingTasks by viewModel.waitingTasks.collectAsState()
    val completedTasks by viewModel.completedTasks.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedTags by viewModel.selectedTags.collectAsState()
    val activeProject by viewModel.activeProject.collectAsState()
    val availableTags by viewModel.availableTags.collectAsState()
    val showInternalTags by viewModel.showInternalTags.collectAsState()
    val currentSortOrder by viewModel.sortOrder.collectAsState()
    val currentSortDirection by viewModel.sortDirection.collectAsState()
    val showOnlyActiveTasks by viewModel.showOnlyActiveTasks.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncStatusMessage by viewModel.syncStatusMessage.collectAsState()
    val showPriorityBadge by viewModel.showPriorityBadge.collectAsState()
    val showUrgencyBar by viewModel.showUrgencyBar.collectAsState()
    val maxUrgency by viewModel.maxUrgency.collectAsState()
    val contexts by viewModel.taskContexts.collectAsState()
    val activeContextId by viewModel.activeTaskContextId.collectAsState()
    val activeContext by viewModel.activeTaskContext.collectAsState()
    val activeContextError by viewModel.activeTaskContextError.collectAsState()
    val availableProjects by viewModel.hierarchicalProjects.collectAsState()

    var showFilters by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }
    var filterBuilderPurpose by remember { mutableStateOf<TaskFilterBuilderPurpose?>(null) }
    var showCompleted by remember { mutableStateOf(false) }
    var showWaiting by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    ScrollToTopOnListChange(
        lazyListState, currentSortOrder, currentSortDirection, searchQuery,
        selectedTags, activeProject, activeContextId, showOnlyActiveTasks,
    )
    var pullAccumulator by remember { mutableFloatStateOf(0f) }

    val pullToRefreshConnection =
        remember {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (available.y < 0f ||
                        lazyListState.firstVisibleItemIndex != 0 ||
                        lazyListState.firstVisibleItemScrollOffset != 0
                    ) {
                        pullAccumulator = 0f
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (source == NestedScrollSource.UserInput &&
                        lazyListState.firstVisibleItemIndex == 0 &&
                        lazyListState.firstVisibleItemScrollOffset == 0 &&
                        available.y > 0f
                    ) {
                        pullAccumulator += available.y
                        if (pullAccumulator >= PULL_TO_REFRESH_THRESHOLD) {
                            pullAccumulator = 0f
                            viewModel.sync()
                        }
                        return Offset(0f, available.y)
                    }
                    return Offset.Zero
                }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity,
                ): Velocity {
                    pullAccumulator = 0f
                    return Velocity.Zero
                }
            }
        }

    var taskToComplete by remember { mutableStateOf<Task?>(null) }
    var taskToDelete by remember { mutableStateOf<Task?>(null) }

    fun requestComplete(task: Task) {
        val currentTask = allTasks.firstOrNull { it.uuid == task.uuid } ?: task
        if (confirmActions ||
            currentTask.isBlocked ||
            unresolvedDependencyUuids(currentTask, allTasks).isNotEmpty()
        ) {
            taskToComplete = currentTask
        } else {
            viewModel.completeTask(currentTask.uuid)
        }
    }

    fun requestRestore(task: Task) {
        viewModel.restoreTask(task.uuid)
    }

    fun requestDelete(task: Task) {
        if (confirmActions) {
            taskToDelete = task
        } else {
            viewModel.deleteTask(task.uuid)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.taskCreatedEvent.collect { event ->
            if (event.openEditor) {
                onTaskClick(event.task)
            } else {
                val result = snackbarHostState.showSnackbar(message = "Task created", actionLabel = "Edit")
                if (result == SnackbarResult.ActionPerformed) {
                    onTaskClick(event.task)
                }
            }
        }
    }

    var textFieldValue by remember { mutableStateOf(TextFieldValue(searchQuery)) }

    LaunchedEffect(searchQuery) {
        if (textFieldValue.text != searchQuery) {
            textFieldValue =
                TextFieldValue(
                    text = searchQuery,
                    selection = TextRange(searchQuery.length),
                )
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    action =
                        data.visuals.actionLabel?.let { label ->
                            {
                                TextButton(onClick = { data.performAction() }) {
                                    Text(label, color = MaterialTheme.colorScheme.inversePrimary)
                                }
                            }
                        },
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionContentColor = MaterialTheme.colorScheme.inversePrimary,
                ) {
                    Text(
                        text = data.visuals.message,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTaskClick,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Task")
            }
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = FattoElevation.none,
            ) {
                Column(modifier = Modifier.padding(FattoSpacing.large)) {
                    TasksTitle(visible = largeFontScale)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TasksTitle(
                            visible = !largeFontScale,
                            modifier = Modifier.weight(1f),
                        )

                        Box {
                            ContextSelectorMenu(
                                contexts = contexts,
                                activeContextId = activeContextId,
                                expanded = showContextMenu,
                                onExpandedChange = { showContextMenu = it },
                                onContextSelected = viewModel::setActiveTaskContextId,
                                onCreateContext = { filterBuilderPurpose = TaskFilterBuilderPurpose.CONTEXT },
                                onManageContexts = onManageContexts,
                            )
                        }
                        IconButton(
                            onClick = { showFilters = !showFilters },
                            modifier = Modifier.semantics { stateDescription = if (showFilters) "Filters shown" else "Filters hidden" },
                        ) {
                            Icon(Icons.Default.FilterList, contentDescription = "Toggle Filters")
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                shape = MaterialTheme.shapes.medium,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(order.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }) },
                                        onClick = {
                                            viewModel.setSortOrder(order)
                                            showSortMenu = false
                                        },
                                        trailingIcon = {
                                            if (currentSortOrder == order) {
                                                val isAsc = currentSortDirection == SortDirection.ASCENDING
                                                Icon(
                                                    imageVector = if (isAsc) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                                                    contentDescription = if (isAsc) "Ascending" else "Descending",
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { viewModel.toggleShowOnlyActiveTasks() }) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription =
                                    if (showOnlyActiveTasks) {
                                        "Show all tasks"
                                    } else {
                                        "Show only active tasks"
                                    },
                                tint =
                                    if (showOnlyActiveTasks) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                        }
                        Box(
                            modifier = Modifier.size(FattoMetrics.minTouchTarget),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier =
                                        Modifier
                                            .size(FattoMetrics.icon)
                                            .semantics { contentDescription = "Syncing" },
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                IconButton(onClick = { viewModel.sync() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Sync")
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible =
                            showFilters ||
                                searchQuery.isNotEmpty() ||
                                selectedTags.isNotEmpty() ||
                                activeProject != null ||
                                activeContext != null,
                    ) {
                        Column(
                            modifier = Modifier.padding(top = FattoSpacing.large),
                            verticalArrangement = Arrangement.spacedBy(FattoSpacing.small),
                        ) {
                            val hasClearableFilters = searchQuery.isNotEmpty() || selectedTags.isNotEmpty() || activeProject != null
                            FilterSectionActions(
                                largeFontScale = largeFontScale,
                                hasClearableFilters = hasClearableFilters,
                                onBuildFilter = { filterBuilderPurpose = TaskFilterBuilderPurpose.FILTER },
                                onClearFilters = viewModel::clearFilters,
                            )

                            OutlinedTextField(
                                value = textFieldValue,
                                onValueChange = {
                                    textFieldValue = it
                                    viewModel.onSearchQueryChange(it.text)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Search tasks...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                singleLine = true,
                                colors = FattoFieldDefaults.outlinedColors(),
                                trailingIcon = {
                                    if (textFieldValue.text.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Clear search",
                                                modifier = Modifier.size(FattoMetrics.inlineIcon),
                                            )
                                        }
                                    }
                                },
                            )

                            activeContext?.let { context ->
                                ContextChip(context = context, onClear = { viewModel.setActiveTaskContextId(null) })
                            }

                            if (activeProject != null) {
                                Surface(
                                    onClick = { viewModel.setActiveProject(null) },
                                    modifier = Modifier.heightIn(min = FattoMetrics.minTouchTarget).semantics { selected = true },
                                    color = activeProject!!.toNordicColor().copy(alpha = FattoOpacity.tint),
                                    shape = MaterialTheme.shapes.small,
                                    border = BorderStroke(FattoStroke.subtle, activeProject!!.toNordicColor()),
                                ) {
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = FattoSpacing.medium),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small),
                                    ) {
                                        Text(
                                            text = "Project: $activeProject",
                                            modifier =
                                                Modifier
                                                    .weight(1f)
                                                    .padding(vertical = FattoSpacing.small),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = null,
                                            modifier = Modifier.size(FattoMetrics.inlineIcon),
                                        )
                                    }
                                }
                            }

                            if (availableTags.isNotEmpty()) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small),
                                    contentPadding = PaddingValues(bottom = FattoSpacing.small),
                                ) {
                                    items(availableTags.toList()) { tag ->
                                        Surface(
                                            onClick = { viewModel.toggleTag(tag) },
                                            modifier =
                                                Modifier
                                                    .heightIn(min = FattoMetrics.minTouchTarget)
                                                    .semantics { selected = selectedTags.contains(tag) },
                                            color =
                                                if (selectedTags.contains(tag)) {
                                                    tag.toNordicColor().copy(alpha = FattoOpacity.selectedTint)
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceContainerLow
                                                },
                                            shape = MaterialTheme.shapes.small,
                                            border =
                                                BorderStroke(
                                                    width = FattoStroke.subtle,
                                                    color =
                                                        if (selectedTags.contains(tag)) {
                                                            tag.toNordicColor()
                                                        } else {
                                                            MaterialTheme.colorScheme.outlineVariant
                                                        },
                                                ),
                                        ) {
                                            Text(
                                                text = tag,
                                                modifier =
                                                    Modifier.padding(
                                                        horizontal = FattoSpacing.medium,
                                                        vertical = FattoSpacing.small,
                                                    ),
                                                style = MaterialTheme.typography.labelMedium,
                                                color =
                                                    if (selectedTags.contains(tag)) {
                                                        MaterialTheme.colorScheme.onSurface
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = syncStatusMessage != null,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = FattoSpacing.large, vertical = FattoSpacing.small),
                ) {
                    Text(
                        text = syncStatusMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(FattoSpacing.small),
                    )
                }
            }

            ContextErrorBanner(
                contextName = activeContext?.name.orEmpty(),
                error = activeContextError,
            )

            LazyColumn(
                state = lazyListState,
                modifier =
                    Modifier
                        .weight(1f)
                        .testTag("TaskList")
                        .nestedScroll(pullToRefreshConnection),
                contentPadding =
                    PaddingValues(
                        start = FattoSpacing.large,
                        top = FattoSpacing.small,
                        end = FattoSpacing.large,
                        bottom = FattoMetrics.taskListBottomInset,
                    ),
                verticalArrangement = Arrangement.spacedBy(FattoSpacing.small),
            ) {
                items(tasks, key = { it.uuid }) { task ->
                    TaskListRow(
                        task = task,
                        swipeStartToEndAction = swipeStartToEndAction,
                        swipeEndToStartAction = swipeEndToStartAction,
                        onClick = { onTaskClick(task) },
                        onComplete = { requestComplete(task) },
                        onRestore = { requestRestore(task) },
                        onDelete = { requestDelete(task) },
                        onEdit = { onTaskClick(task) },
                        onStartStop = { viewModel.toggleTaskActive(task) },
                        showInternalTags = showInternalTags,
                        maxUrgency = maxUrgency,
                        showPriorityBadge = showPriorityBadge,
                        showUrgencyBar = showUrgencyBar,
                    )
                }

                if (waitingTasks.isNotEmpty()) {
                    item {
                        CollapsibleHeader(
                            title = "Waiting",
                            count = waitingTasks.size,
                            isExpanded = showWaiting,
                            onClick = { showWaiting = !showWaiting },
                        )
                    }

                    if (showWaiting) {
                        items(waitingTasks, key = { it.uuid }) { task ->
                            TaskListRow(
                                task = task,
                                swipeStartToEndAction = swipeStartToEndAction,
                                swipeEndToStartAction = swipeEndToStartAction,
                                onClick = { onTaskClick(task) },
                                onComplete = { requestComplete(task) },
                                onRestore = { requestRestore(task) },
                                onDelete = { requestDelete(task) },
                                onEdit = { onTaskClick(task) },
                                onStartStop = { viewModel.toggleTaskActive(task) },
                                showInternalTags = showInternalTags,
                                maxUrgency = maxUrgency,
                                showPriorityBadge = showPriorityBadge,
                                showUrgencyBar = showUrgencyBar,
                            )
                        }
                    }
                }

                if (completedTasks.isNotEmpty()) {
                    item {
                        CollapsibleHeader(
                            title = "Completed",
                            count = completedTasks.size,
                            isExpanded = showCompleted,
                            onClick = { showCompleted = !showCompleted },
                        )
                    }

                    if (showCompleted) {
                        items(completedTasks, key = { it.uuid }) { task ->
                            TaskListRow(
                                task = task,
                                swipeStartToEndAction = swipeStartToEndAction,
                                swipeEndToStartAction = swipeEndToStartAction,
                                onClick = { onTaskClick(task) },
                                onComplete = { requestComplete(task) },
                                onRestore = { requestRestore(task) },
                                onDelete = { requestDelete(task) },
                                onEdit = { onTaskClick(task) },
                                onStartStop = { viewModel.toggleTaskActive(task) },
                                showInternalTags = showInternalTags,
                                maxUrgency = maxUrgency,
                                showPriorityBadge = showPriorityBadge,
                                showUrgencyBar = showUrgencyBar,
                            )
                        }
                    }
                }
            }

            if (taskToComplete != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { taskToComplete = null },
                    shape = MaterialTheme.shapes.extraLarge,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    title = { Text("Complete Task", style = MaterialTheme.typography.headlineSmall) },
                    text = {
                        val task = taskToComplete!!
                        val dependencyUuids = unresolvedDependencyUuids(task, allTasks)
                        Column {
                            Text(completionConfirmationMessage(task, allTasks))
                            if (dependencyUuids.isNotEmpty()) {
                                TextButton(onClick = {
                                    viewModel.showTasksWithUuids(dependencyUuids)
                                    taskToComplete = null
                                }) {
                                    Text("View blocking tasks")
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                taskToComplete?.let { viewModel.completeTask(it.uuid) }
                                taskToComplete = null
                            },
                        ) {
                            Text("Confirm")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { taskToComplete = null }) {
                            Text("Cancel")
                        }
                    },
                )
            }

            filterBuilderPurpose?.let { purpose ->
                TaskFilterBuilderSheet(
                    initialState =
                        TaskFilterState(
                            descriptionQuery = searchQuery,
                            project = activeProject,
                            tags = selectedTags,
                        ),
                    availableProjects = availableProjects.map { it.fullName },
                    availableTags = availableTags,
                    contextName = "",
                    purpose = purpose,
                    onDismiss = { filterBuilderPurpose = null },
                    onApply = { filter ->
                        viewModel.onSearchQueryChange(filter.descriptionQuery)
                        viewModel.clearProject()
                        filter.project?.let { viewModel.setActiveProject(it) }
                        viewModel.clearTags()
                        filter.tags.forEach { viewModel.toggleTag(it) }
                        filterBuilderPurpose = null
                    },
                    onSaveContext = { name, filter ->
                        val context = filter.toContext(name)
                        viewModel.clearFilters()
                        viewModel.saveTaskContext(context)
                        viewModel.setActiveTaskContextId(context.id)
                        filterBuilderPurpose = null
                    },
                )
            }

            if (taskToDelete != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { taskToDelete = null },
                    shape = MaterialTheme.shapes.extraLarge,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    title = { Text("Delete Task", style = MaterialTheme.typography.headlineSmall) },
                    text = { Text("Are you sure you want to delete this task?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                taskToDelete?.let { viewModel.deleteTask(it.uuid) }
                                taskToDelete = null
                            },
                        ) {
                            Text("Confirm")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { taskToDelete = null }) {
                            Text("Cancel")
                        }
                    },
                )
            }
        }
    }
}

@Suppress("detekt.LongParameterList")
@Composable
private fun TaskListRow(
    task: Task,
    swipeStartToEndAction: TaskSwipeAction,
    swipeEndToStartAction: TaskSwipeAction,
    onClick: () -> Unit,
    onComplete: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onStartStop: () -> Unit,
    showInternalTags: Boolean,
    maxUrgency: Float,
    showPriorityBadge: Boolean,
    showUrgencyBar: Boolean,
) {
    SwipeableTaskRow(
        task = task,
        startToEndAction = swipeStartToEndAction,
        endToStartAction = swipeEndToStartAction,
        onComplete = onComplete,
        onDelete = onDelete,
        onEdit = onEdit,
        onStartStop = onStartStop,
    ) {
        TaskItem(
            task = task,
            onClick = onClick,
            onComplete = onComplete,
            onDelete = onDelete,
            onRestore = if (task.status == TaskStatus.COMPLETED) onRestore else null,
            showInternalTags = showInternalTags,
            maxUrgency = maxUrgency,
            showPriorityBadge = showPriorityBadge,
            showUrgencyBar = showUrgencyBar,
        )
    }
}

@Composable
private fun ContextErrorBanner(
    contextName: String,
    error: String?,
) {
    AnimatedVisibility(visible = error != null) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FattoSpacing.large, vertical = FattoSpacing.small),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                text = "Context \"$contextName\" has an invalid filter: ${error.orEmpty()}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(FattoSpacing.medium),
            )
        }
    }
}

@Composable
private fun ContextChip(
    context: TaskContext,
    onClear: () -> Unit,
) {
    Surface(
        onClick = onClear,
        modifier =
            Modifier
                .semantics { contentDescription = "Clear context" }
                .heightIn(min = FattoMetrics.minTouchTarget)
                .fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FattoSpacing.medium, vertical = FattoSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small),
        ) {
            Text(
                "Context: ${context.name}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Icon(
                Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(FattoMetrics.inlineIcon),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
fun CollapsibleHeader(
    title: String,
    count: Int,
    isExpanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = FattoMetrics.minTouchTarget)
                .clickable { onClick() }
                .semantics { stateDescription = if (isExpanded) "Expanded" else "Collapsed" }
                .padding(vertical = FattoSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$title ($count)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskItem(
    task: Task,
    onClick: () -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    showInternalTags: Boolean = true,
    maxUrgency: Float = 0.0f,
    showPriorityBadge: Boolean = false,
    showUrgencyBar: Boolean = false,
    onRestore: (() -> Unit)? = null,
) {
    val maxDescriptionLines = if (effectiveFontScale() >= FattoMetrics.largeFontScale) 4 else 2
    Card(
        modifier =
            Modifier.fillMaxWidth().semantics {
                contentDescription = task.description
            },
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (task.start != null) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
            ),
        border =
            if (task.start != null) {
                BorderStroke(
                    width = FattoStroke.selected,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                )
            } else {
                null
            },
        elevation = CardDefaults.cardElevation(defaultElevation = FattoElevation.card),
    ) {
        Column {
            Row(
                modifier = Modifier.height(IntrinsicSize.Min).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(FattoStroke.projectStripe)
                            .fillMaxHeight()
                            .background(task.project?.toNordicColor() ?: MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                )

                Row(
                    modifier =
                        Modifier
                            .padding(FattoSpacing.medium)
                            .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (task.start != null) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Active",
                                    modifier = Modifier.size(FattoMetrics.inlineIcon),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.width(FattoSpacing.xSmall))
                            }
                            Text(
                                text = task.description,
                                style = MaterialTheme.typography.titleSmall,
                                textDecoration = if (task.status == TaskStatus.COMPLETED) TextDecoration.LineThrough else null,
                                maxLines = maxDescriptionLines,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (showPriorityBadge && task.priority != null) {
                                PriorityBadge(priority = task.priority)
                            }
                        }

                        if (
                            task.isBlocked ||
                            task.project != null ||
                            task.due != null ||
                            task.tags.isNotEmpty() ||
                            task.scheduled != null
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small),
                                verticalArrangement = Arrangement.spacedBy(FattoSpacing.xSmall),
                                modifier = Modifier.padding(top = FattoSpacing.xSmall),
                            ) {
                                if (task.isBlocked) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        shape = MaterialTheme.shapes.small,
                                    ) {
                                        Text(
                                            text = "Blocked",
                                            modifier =
                                                Modifier.padding(
                                                    horizontal = FattoSpacing.small,
                                                    vertical = FattoSpacing.xSmall,
                                                ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                    }
                                }
                                task.project?.let { proj ->
                                    Text(
                                        text = proj,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = proj.toNordicColor(),
                                        fontWeight = FontWeight.Bold,
                                    )
                                }

                                task.due?.let {
                                    Text(
                                        text = "Due: ${com.brokenpip3.fatto.data.DateTimeUtils.formatLocalDate(it)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }

                                task.scheduled?.let {
                                    Text(
                                        text = "Sch: ${com.brokenpip3.fatto.data.DateTimeUtils.formatLocalDate(it)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }

                                task.userTags.filter { showInternalTags || !INTERNAL_TAGS.contains(it.uppercase()) }.forEach { tag ->
                                    TagChip(tag = tag)
                                }
                            }
                        }
                    }

                    if (task.status == TaskStatus.PENDING) {
                        IconButton(
                            onClick = onComplete,
                            modifier = Modifier.size(FattoMetrics.minTouchTarget).testTag("TaskCompleteAction"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Complete",
                                modifier = Modifier.size(FattoMetrics.smallIcon),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else if (task.status == TaskStatus.COMPLETED && onRestore != null) {
                        IconButton(onClick = onRestore, modifier = Modifier.size(FattoMetrics.minTouchTarget)) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = "Restore",
                                modifier = Modifier.size(FattoMetrics.smallIcon),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(FattoMetrics.minTouchTarget).testTag("TaskDeleteAction"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete task",
                            modifier = Modifier.size(FattoMetrics.smallIcon),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (showUrgencyBar) {
                UrgencyBar(urgency = task.urgency, maxUrgency = maxUrgency)
            }
        }
    }
}

@Composable
private fun FilterSectionActions(
    largeFontScale: Boolean,
    hasClearableFilters: Boolean,
    onBuildFilter: () -> Unit,
    onClearFilters: () -> Unit,
) {
    val label = @Composable {
        Text(
            "Filters",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (largeFontScale) {
        Column(verticalArrangement = Arrangement.spacedBy(FattoSpacing.xSmall)) {
            label()
            BuildFilterAction(modifier = Modifier.fillMaxWidth(), onClick = onBuildFilter)
            if (hasClearableFilters) {
                ClearFilterAction(modifier = Modifier.fillMaxWidth(), onClick = onClearFilters)
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            label()
            Row(
                horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BuildFilterAction(modifier = Modifier, onClick = onBuildFilter)
                if (hasClearableFilters) {
                    ClearFilterAction(modifier = Modifier, onClick = onClearFilters)
                }
            }
        }
    }
}

@Composable
private fun BuildFilterAction(
    modifier: Modifier,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = FattoSpacing.small, vertical = FattoSpacing.small),
        modifier = modifier.heightIn(min = FattoMetrics.minTouchTarget),
    ) {
        Text("Build filter", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ClearFilterAction(
    modifier: Modifier,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = FattoSpacing.small, vertical = FattoSpacing.small),
        modifier = modifier.heightIn(min = FattoMetrics.minTouchTarget),
    ) {
        Text(
            "Clear All",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun TasksTitle(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (visible) {
        Text(
            text = "Tasks",
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            modifier = modifier,
        )
    }
}

@Composable
fun PriorityBadge(priority: String) {
    val (bg, fg) =
        when (priority) {
            "H" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
            "M" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
            "L" -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
            else -> return
        }
    Surface(
        color = bg,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.padding(start = FattoSpacing.xSmall),
    ) {
        Text(
            text = priority,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = FattoSpacing.small, vertical = FattoSpacing.xSmall),
        )
    }
}

@Composable
fun UrgencyBar(
    urgency: Float,
    maxUrgency: Float,
) {
    val ratio = if (maxUrgency > 0f) (maxOf(0f, urgency) / maxUrgency).coerceIn(0f, 1f) else 0f
    val barColor =
        when {
            ratio > 0.66f -> MaterialTheme.colorScheme.error
            ratio > 0.33f -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.outline
        }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(FattoStroke.urgency)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = FattoOpacity.track)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(ratio)
                    .fillMaxHeight()
                    .background(barColor),
        )
    }
}

// When sort/filter state changes, the list is reordered/filtered in place and
// the scroll position would otherwise be retained. Jump back to the top so
// the first task of the new ordering is visible.
@Composable
private fun ScrollToTopOnListChange(
    lazyListState: LazyListState,
    vararg keys: Any?,
) {
    LaunchedEffect(*keys) {
        lazyListState.scrollToItem(0)
    }
}
