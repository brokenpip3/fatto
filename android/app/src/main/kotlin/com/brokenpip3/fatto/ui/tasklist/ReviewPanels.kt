package com.brokenpip3.fatto.ui.tasklist

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.brokenpip3.fatto.data.review.ReviewSettings
import com.brokenpip3.fatto.ui.theme.FattoMetrics
import com.brokenpip3.fatto.ui.theme.FattoSpacing
import com.brokenpip3.fatto.ui.theme.FattoStroke
import com.brokenpip3.fatto.ui.theme.effectiveFontScale
import com.brokenpip3.fatto.vm.ReviewUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReviewNotice() {
    Column(verticalArrangement = Arrangement.spacedBy(FattoSpacing.large)) {
        Text("Before your first review", style = MaterialTheme.typography.titleMedium)
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.small) {
            FlowRow(
                Modifier.padding(FattoSpacing.large),
                horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small),
                verticalArrangement = Arrangement.spacedBy(FattoSpacing.small),
            ) {
                Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary)
                Text("reviewed", style = MaterialTheme.typography.titleLarge)
                Text("UDA key · date", style = MaterialTheme.typography.bodyMedium)
            }
        }
        val noticeStyle =
            MaterialTheme.typography.bodyLarge.let {
                it.copy(
                    fontSize = it.fontSize * 1.125f,
                    lineHeight =
                        it.lineHeight * 1.125f,
                )
            }
        Text(
            "Fatto writes the review time to this UDA key when you mark a task reviewed or save an edit during review.",
            style = noticeStyle,
        )
        Text("The saved timestamp syncs with your other Taskwarrior clients.", style = noticeStyle)
        Text("Skipping leaves this UDA unchanged. Existing review timestamps are reused.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun ReviewStartOverview(state: ReviewUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(FattoSpacing.large)) {
        if (state.total == 0) {
            Text("No tasks needing review", style = MaterialTheme.typography.titleMedium)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(FattoSpacing.large), verticalAlignment = Alignment.CenterVertically) {
                Text(state.total.toString(), style = MaterialTheme.typography.displayMedium)
                Text("tasks ready\nfor review", style = MaterialTheme.typography.bodyLarge)
            }
        }
        Text("make room for what matters", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(FattoSpacing.large), verticalArrangement = Arrangement.spacedBy(FattoSpacing.large)) {
                ReviewPolicyRow(
                    Icons.Default.Schedule,
                    "Never reviewed or over ${state.settings.intervalDays} days ago",
                    "Pending and waiting tasks",
                )
                ReviewPolicyRow(
                    Icons.Default.TaskAlt, "Never reviewed first, then oldest",
                    state.settings.sessionLimit?.let { "Up to $it tasks this session" } ?: "All eligible tasks",
                )
                ReviewPolicyRow(
                    Icons.Default.FilterList,
                    state.settings.project?.let { "Project: $it" } ?: "All projects",
                    state.settings.tag?.let { "Tag: $it" } ?: "All tags",
                )
            }
        }
        if (state.invalidDates > 0) {
            Text(
                "${state.invalidDates} tasks have an invalid reviewed date and are excluded. Their values have been preserved.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text("Each action saves immediately. Skipping does not mark a task reviewed.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReviewPolicyRow(
    icon: ImageVector,
    title: String,
    detail: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(FattoSpacing.large)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FattoSpacing.xSmall)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ReviewSwipeHints(settings: ReviewSettings) {
    // These are physical swipe directions. Keep their positions and arrows fixed in RTL layouts.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small)) {
            ReviewSwipeHint("Swipe left", settings.leftAction.label, Icons.AutoMirrored.Filled.ArrowBack, Modifier.weight(1f), true)
            ReviewSwipeHint("Swipe right", settings.rightAction.label, Icons.AutoMirrored.Filled.ArrowForward, Modifier.weight(1f), false)
        }
    }
}

@Composable
private fun ReviewSwipeHint(
    direction: String,
    action: String,
    icon: ImageVector,
    modifier: Modifier,
    left: Boolean,
) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.small) {
        Row(
            Modifier.padding(FattoSpacing.small), horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (left) Icon(icon, null)
            Column(Modifier.weight(1f)) {
                Text(direction, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(action, style = MaterialTheme.typography.bodyMedium)
            }
            if (!left) Icon(icon, null)
        }
    }
}

@Composable
internal fun ReviewSessionSummary(state: ReviewUiState) {
    val processed = state.reviewedActions + state.skipped
    val colors =
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.error,
        )
    val counts = listOf(state.reviewed, state.skipped, state.completed, state.deleted)
    Column(verticalArrangement = Arrangement.spacedBy(FattoSpacing.large)) {
        Text(if (processed == 0) "Session ended" else "Your review progress", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(FattoSpacing.large), verticalAlignment = Alignment.CenterVertically) {
            ReviewOutcomeRing(counts, colors)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FattoSpacing.xSmall)) {
                Text("${state.reviewedActions} reviewed", style = MaterialTheme.typography.headlineSmall)
                Text("$processed of ${state.total} tasks processed", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(
            "Reviewed includes marked, completed, and deleted tasks. Task changes save as you go.",
            style = MaterialTheme.typography.bodyMedium,
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (effectiveFontScale() >= FattoMetrics.largeFontScale || maxWidth < 320.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(FattoSpacing.small)) {
                    ReviewStat("Marked reviewed", state.reviewed, Icons.Default.TaskAlt, colors[0], "Reviewed", Modifier.fillMaxWidth())
                    ReviewStat("Skipped", state.skipped, Icons.Default.SkipNext, colors[1], "Skipped", Modifier.fillMaxWidth())
                    ReviewStat("Completed", state.completed, Icons.Default.Check, colors[2], "Completed", Modifier.fillMaxWidth())
                    ReviewStat("Deleted", state.deleted, Icons.Default.Delete, colors[3], "Deleted", Modifier.fillMaxWidth())
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(FattoSpacing.small)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small)) {
                        ReviewStat("Marked reviewed", state.reviewed, Icons.Default.TaskAlt, colors[0], "Reviewed", Modifier.weight(1f))
                        ReviewStat("Skipped", state.skipped, Icons.Default.SkipNext, colors[1], "Skipped", Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small)) {
                        ReviewStat("Completed", state.completed, Icons.Default.Check, colors[2], "Completed", Modifier.weight(1f))
                        ReviewStat("Deleted", state.deleted, Icons.Default.Delete, colors[3], "Deleted", Modifier.weight(1f))
                    }
                }
            }
        }
        val unvisited = (state.total - maxOf(state.position, processed + state.omitted)).coerceAtLeast(0)
        Text(
            "$unvisited tasks not reached this session. You can start another review whenever you’re ready.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (state.omitted > 0) {
            Text("${state.omitted} tasks changed elsewhere and were omitted.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ReviewOutcomeRing(
    counts: List<Int>,
    colors: List<Color>,
) {
    val total = counts.sum()
    val track = MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier.size(FattoMetrics.largeProgressRing + FattoSpacing.extraLarge)
            .semantics(mergeDescendants = true) { contentDescription = "$total tasks processed" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize().padding(FattoSpacing.small)) {
            val stroke = Stroke((FattoStroke.progress * 2).toPx())
            drawArc(track, -90f, 360f, false, style = stroke)
            var start = -90f
            if (total > 0) {
                counts.forEachIndexed { i, count ->
                    val sweep = count.toFloat() / total * 360f
                    drawArc(colors[i], start, sweep, false, style = stroke)
                    start += sweep
                }
            }
        }
        Text(total.toString(), style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun ReviewStat(
    label: String,
    count: Int,
    icon: ImageVector,
    accent: Color,
    name: String,
    modifier: Modifier,
) {
    Surface(modifier.testTag("ReviewStat$name"), color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(FattoSpacing.large), verticalArrangement = Arrangement.spacedBy(FattoSpacing.small)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(count.toString(), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, color = accent)
                Spacer(Modifier.size(FattoSpacing.xSmall))
                Icon(icon, null, tint = accent)
            }
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
