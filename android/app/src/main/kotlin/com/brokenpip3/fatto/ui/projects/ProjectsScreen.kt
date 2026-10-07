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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.brokenpip3.fatto.ui.theme.FattoElevation
import com.brokenpip3.fatto.ui.theme.FattoMetrics
import com.brokenpip3.fatto.ui.theme.FattoOpacity
import com.brokenpip3.fatto.ui.theme.FattoSpacing
import com.brokenpip3.fatto.ui.theme.FattoStroke
import com.brokenpip3.fatto.ui.theme.effectiveFontScale
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
                        Icon(
                            imageVector = Icons.Default.AccountTree,
                            contentDescription = null,
                            modifier = Modifier.size(FattoMetrics.icon),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(FattoSpacing.medium))
                        Text(
                            text = "No subprojects here",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (path != null) {
                            Spacer(modifier = Modifier.height(FattoSpacing.large))
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
                    contentPadding = PaddingValues(FattoSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(FattoSpacing.small),
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
                            Spacer(modifier = Modifier.height(FattoSpacing.small))
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
                .padding(horizontal = FattoSpacing.large, vertical = FattoSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FattoSpacing.xSmall),
    ) {
        items(breadcrumbs.size) { index ->
            val crumb = breadcrumbs[index]
            val isLast = index == breadcrumbs.size - 1

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .heightIn(min = FattoMetrics.minTouchTarget)
                            .widthIn(min = FattoMetrics.minTouchTarget)
                            .clickable { onCrumbClick(crumb.fullPath) }
                            .semantics { selected = isLast }
                            .padding(horizontal = FattoSpacing.small),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = crumb.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!isLast) {
                    Text(
                        text = "/",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = FattoSpacing.xSmall),
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
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = FattoElevation.card),
    ) {
        val fontScale = effectiveFontScale()
        val ringSize =
            if (fontScale >= FattoMetrics.largeFontScale) {
                FattoMetrics.largeProgressRing * (fontScale / 2f).coerceAtLeast(1f)
            } else {
                FattoMetrics.progressRing
            }

        Row(
            modifier = Modifier.fillMaxWidth().padding(FattoSpacing.large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${node.count} pending tasks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ProjectProgressRing(progress = progress, color = color, size = ringSize)
            Spacer(modifier = Modifier.width(FattoSpacing.small))

            // Reserve the navigation slot so progress rings align across cards.
            Box(
                modifier = Modifier.width(FattoMetrics.icon).testTag("ProjectNavigationSlot"),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (hasSubprojects) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(FattoMetrics.smallIcon),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectProgressRing(
    progress: Float,
    color: androidx.compose.ui.graphics.Color,
    size: androidx.compose.ui.unit.Dp,
) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            color = color,
            strokeWidth = FattoStroke.progress,
            trackColor = color.copy(alpha = FattoOpacity.track),
        )
        Text(
            text = "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
