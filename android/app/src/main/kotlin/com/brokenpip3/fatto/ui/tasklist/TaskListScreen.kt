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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.brokenpip3.fatto.data.model.INTERNAL_TAGS
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.ui.theme.NordicFrost
import com.brokenpip3.fatto.ui.theme.NordicMidnight
import com.brokenpip3.fatto.ui.theme.toNordicColor
import com.brokenpip3.fatto.vm.SortOrder
import com.brokenpip3.fatto.vm.TaskViewModel
import uniffi.taskchampion_android.TaskStatus

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskListScreen(
    viewModel: TaskViewModel,
    onTaskClick: (Task) -> Unit,
    onAddTaskClick: () -> Unit,
) {
    val tasks by viewModel.activeTasks.collectAsState()
    val completedTasks by viewModel.completedTasks.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedTags by viewModel.selectedTags.collectAsState()
    val activeProject by viewModel.activeProject.collectAsState()
    val availableTags by viewModel.availableTags.collectAsState()
    val showInternalTags by viewModel.showInternalTags.collectAsState()
    val currentSortOrder by viewModel.sortOrder.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    var showFilters by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showCompleted by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
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
                    containerColor = NordicMidnight,
                    contentColor = NordicFrost,
                    actionContentColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = data.visuals.message,
                        color = NordicFrost,
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
                tonalElevation = 2.dp,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Tasks",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )

                        IconButton(onClick = { showFilters = !showFilters }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Toggle Filters")
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                            ) {
                                SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(order.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }) },
                                        onClick = {
                                            viewModel.setSortOrder(order)
                                            showSortMenu = false
                                        },
                                        leadingIcon = {
                                            if (currentSortOrder == order) {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp).padding(4.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            IconButton(onClick = { viewModel.sync() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Sync")
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = showFilters || searchQuery.isNotEmpty() || selectedTags.isNotEmpty() || activeProject != null,
                    ) {
                        Column(
                            modifier = Modifier.padding(top = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Filters",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (searchQuery.isNotEmpty() || selectedTags.isNotEmpty() || activeProject != null) {
                                    TextButton(
                                        onClick = { viewModel.clearFilters() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp),
                                    ) {
                                        Text(
                                            "Clear All",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = textFieldValue,
                                onValueChange = {
                                    textFieldValue = it
                                    viewModel.onSearchQueryChange(it.text)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Search tasks...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                colors =
                                    TextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    ),
                                trailingIcon = {
                                    if (textFieldValue.text.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                },
                            )

                            AnimatedVisibility(visible = textFieldValue.text.isEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "Tip:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                    SuggestionChip(
                                        onClick = {
                                            val prefix = if (textFieldValue.text.isEmpty() || textFieldValue.text.endsWith(" ")) "" else " "
                                            viewModel.onSearchQueryChange("${textFieldValue.text}${prefix}project:")
                                        },
                                        label = "project:xxx",
                                    )
                                    SuggestionChip(
                                        onClick = {
                                            val prefix = if (textFieldValue.text.isEmpty() || textFieldValue.text.endsWith(" ")) "" else " "
                                            viewModel.onSearchQueryChange("${textFieldValue.text}${prefix}tags:")
                                        },
                                        label = "tags:xxx,yyy",
                                    )
                                }
                            }

                            if (activeProject != null) {
                                Surface(
                                    onClick = { viewModel.setActiveProject(null) },
                                    color = activeProject!!.toNordicColor().copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, activeProject!!.toNordicColor()),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "Project: $activeProject",
                                            modifier =
                                                Modifier
                                                    .weight(1f)
                                                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = activeProject!!.toNordicColor(),
                                        )
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp).padding(4.dp),
                                        )
                                    }
                                }
                            }

                            if (availableTags.isNotEmpty()) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(bottom = 8.dp),
                                ) {
                                    items(availableTags.toList()) { tag ->
                                        Surface(
                                            onClick = { viewModel.toggleTag(tag) },
                                            color =
                                                if (selectedTags.contains(tag)) {
                                                    tag.toNordicColor().copy(alpha = 0.2f)
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                },
                                            shape = RoundedCornerShape(16.dp),
                                            border =
                                                BorderStroke(
                                                    width = 1.dp,
                                                    color =
                                                        if (selectedTags.contains(tag)) {
                                                            tag.toNordicColor()
                                                        } else {
                                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                                        },
                                                ),
                                        ) {
                                            Text(
                                                text = tag,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color =
                                                    if (selectedTags.contains(tag)) {
                                                        tag.toNordicColor()
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

            LazyColumn(
                modifier = Modifier.weight(1f).testTag("TaskList"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(tasks, key = { it.uuid }) { task ->
                    TaskItem(
                        task = task,
                        onClick = { onTaskClick(task) },
                        onComplete = { viewModel.completeTask(task.uuid) },
                        onDelete = { viewModel.deleteTask(task.uuid) },
                        showInternalTags = showInternalTags,
                    )
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
                            TaskItem(
                                task = task,
                                onClick = { onTaskClick(task) },
                                onComplete = { },
                                onDelete = { viewModel.deleteTask(task.uuid) },
                                showInternalTags = showInternalTags,
                            )
                        }
                    }
                }
            }
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
                .clickable { onClick() }
                .padding(vertical = 8.dp),
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
) {
    Card(
        modifier =
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                contentDescription = "TaskItem"
            },
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(task.project?.toNordicColor() ?: MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            )

            Row(
                modifier =
                    Modifier
                        .padding(12.dp)
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
                                modifier = Modifier.size(16.dp).padding(end = 4.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            text = task.description,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (task.project != null || task.due != null || task.tags.isNotEmpty() || task.scheduled != null) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
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
                                    text = "Due: ${it.take(10)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                )
                            }

                            task.scheduled?.let {
                                Text(
                                    text = "Sch: ${it.take(10)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                )
                            }

                            task.userTags.filter { showInternalTags || !INTERNAL_TAGS.contains(it.uppercase()) }.forEach { tag ->
                                TagChip(tag = tag)
                            }
                        }
                    }
                }

                if (task.status == TaskStatus.PENDING) {
                    IconButton(onClick = onComplete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Complete",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        )
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "DeleteTask",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}
