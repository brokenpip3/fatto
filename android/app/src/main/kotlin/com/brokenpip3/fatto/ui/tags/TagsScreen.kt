package com.brokenpip3.fatto.ui.tags

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.brokenpip3.fatto.ui.theme.FattoMetrics
import com.brokenpip3.fatto.ui.theme.FattoOpacity
import com.brokenpip3.fatto.ui.theme.FattoSpacing
import com.brokenpip3.fatto.ui.theme.FattoStroke
import com.brokenpip3.fatto.ui.theme.toNordicColor
import com.brokenpip3.fatto.vm.TaskViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    viewModel: TaskViewModel,
    onTagSelected: () -> Unit,
) {
    val tagCounts by viewModel.tagCounts.collectAsState()
    val selectedTags by viewModel.selectedTags.collectAsState()
    val tagsPerLine by viewModel.tagsPerLine.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tags", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    if (selectedTags.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearTags() }) {
                            Icon(Icons.Default.ClearAll, contentDescription = "Clear All Tags")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (tagCounts.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Tag,
                        contentDescription = null,
                        modifier = Modifier.size(FattoMetrics.icon),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(FattoSpacing.medium))
                    Text(
                        text = "No active tags",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(tagsPerLine),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = FattoSpacing.small),
                contentPadding = PaddingValues(vertical = FattoSpacing.large),
                verticalArrangement = Arrangement.spacedBy(FattoSpacing.small),
                horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small),
            ) {
                items(tagCounts.toList().sortedByDescending { it.second }) { (tag, count) ->
                    val isSelected = selectedTags.contains(tag)
                    val baseColor = tag.toNordicColor()

                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .semantics {
                                    selected = isSelected
                                    contentDescription = "$tag, $count tasks${if (isSelected) ", selected" else ""}"
                                }
                                .clickable {
                                    viewModel.toggleTag(tag)
                                    if (!isSelected) {
                                        onTagSelected()
                                    }
                                },
                        shape = MaterialTheme.shapes.medium,
                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    baseColor.copy(
                                        alpha = if (isSelected) FattoOpacity.selectedTint else FattoOpacity.tint,
                                    ),
                            ),
                        border =
                            BorderStroke(
                                width = if (isSelected) FattoStroke.selected else FattoStroke.subtle,
                                color = baseColor.copy(alpha = if (isSelected) 1f else 0.3f),
                            ),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = FattoMetrics.tagTileMinHeight)
                                    .padding(FattoSpacing.small),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = baseColor.copy(alpha = FattoOpacity.selectedTint),
                            ) {
                                Text(
                                    text = count.toString(),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Spacer(modifier = Modifier.height(FattoSpacing.xSmall))
                            Text(
                                text = tag,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}
