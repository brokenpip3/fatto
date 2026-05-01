package com.brokenpip3.fatto.ui.projects

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brokenpip3.fatto.ui.theme.toNordicColor
import com.brokenpip3.fatto.vm.Breadcrumb
import com.brokenpip3.fatto.vm.ProjectNode
import com.brokenpip3.fatto.vm.TaskViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    viewModel: TaskViewModel,
    onProjectSelected: () -> Unit,
) {
    val projectNodes by viewModel.filteredProjectNodes.collectAsState()
    val allProjectNodes by viewModel.hierarchicalProjects.collectAsState()
    val activeProject by viewModel.activeProject.collectAsState()
    val currentPath by viewModel.currentProjectPath.collectAsState()
    val breadcrumbs by viewModel.breadcrumbs.collectAsState()

    BackHandler(enabled = currentPath != null) {
        viewModel.navigateUp()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Projects", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    if (activeProject != null) {
                        IconButton(onClick = { viewModel.clearProject() }) {
                            Icon(Icons.Default.ClearAll, contentDescription = "Clear Project Filter")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            // Breadcrumb Bar
            BreadcrumbBar(
                breadcrumbs = breadcrumbs,
                onCrumbClick = { viewModel.navigateToProject(it) },
            )

            if (projectNodes.isEmpty()) {
                val path = currentPath
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No subprojects here",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (path != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = {
                                viewModel.setActiveProject(path)
                                onProjectSelected()
                            }) {
                                Text("View tasks in ${path.split('.').last()}")
                            }
                        }
                    }
                }
            } else {
                val path = currentPath
                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(projectNodes) { node ->
                        val hasSubprojects =
                            allProjectNodes.any {
                                it.fullName.startsWith("${node.fullName}.") && it.level == node.level + 1
                            }

                        ProjectCard(
                            node = node,
                            hasSubprojects = hasSubprojects,
                            onClick = {
                                if (hasSubprojects) {
                                    viewModel.navigateToProject(node.fullName)
                                } else {
                                    viewModel.setActiveProject(node.fullName)
                                    onProjectSelected()
                                }
                            },
                        )
                    }

                    if (path != null) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    viewModel.setActiveProject(path)
                                    onProjectSelected()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("View all tasks in ${path.split('.').last()}")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BreadcrumbBar(
    breadcrumbs: List<Breadcrumb>,
    onCrumbClick: (String?) -> Unit,
) {
    LazyRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(breadcrumbs.size) { index ->
            val crumb = breadcrumbs[index]
            val isLast = index == breadcrumbs.size - 1

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = crumb.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.clickable { onCrumbClick(crumb.fullPath) }.padding(4.dp),
                )
                if (!isLast) {
                    Text(
                        text = "/",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectCard(
    node: ProjectNode,
    hasSubprojects: Boolean,
    onClick: () -> Unit,
) {
    val progress = if (node.totalCount > 0) node.completedCount.toFloat() / node.totalCount else 0f
    val color = node.fullName.toNordicColor()

    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${node.count} pending tasks",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(48.dp),
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = color,
                    strokeWidth = 4.dp,
                    trackColor = color.copy(alpha = 0.1f),
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                )
            }

            // Fixed width container for chevron to keep circles aligned
            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (hasSubprojects) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}
