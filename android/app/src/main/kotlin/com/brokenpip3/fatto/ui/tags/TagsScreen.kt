package com.brokenpip3.fatto.ui.tags

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                Text(
                    text = "No active tags",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(tagsPerLine),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(tagCounts.toList().sortedByDescending { it.second }) { (tag, count) ->
                    val isSelected = selectedTags.contains(tag)
                    val baseColor = tag.toNordicColor()

                    Card(
                        modifier =
                            Modifier
                                .aspectRatio(1f)
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.toggleTag(tag)
                                    if (!isSelected) {
                                        onTagSelected()
                                    }
                                },
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = baseColor.copy(alpha = if (isSelected) 0.2f else 0.05f),
                            ),
                        border =
                            BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = baseColor.copy(alpha = if (isSelected) 1f else 0.3f),
                            ),
                    ) {
                        Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                            // Task Count Badge
                            Surface(
                                modifier = Modifier.align(Alignment.TopEnd),
                                shape = CircleShape,
                                color = baseColor.copy(alpha = 0.1f),
                            ) {
                                Text(
                                    text = count.toString(),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = baseColor,
                                )
                            }

                            // Tag Name (Auto-resizing)
                            var fontSize by remember(tag) { mutableStateOf(22.sp) }
                            Text(
                                text = tag,
                                modifier = Modifier.align(Alignment.Center),
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = fontSize),
                                fontWeight = FontWeight.SemiBold,
                                color = baseColor,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                onTextLayout = { textLayoutResult ->
                                    if (textLayoutResult.hasVisualOverflow && fontSize > 10.sp) {
                                        fontSize = (fontSize.value - 1).sp
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
