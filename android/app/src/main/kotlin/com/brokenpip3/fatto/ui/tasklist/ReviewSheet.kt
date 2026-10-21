package com.brokenpip3.fatto.ui.tasklist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.data.review.ReviewAction
import com.brokenpip3.fatto.ui.theme.FattoMetrics
import com.brokenpip3.fatto.ui.theme.FattoSpacing
import com.brokenpip3.fatto.ui.theme.effectiveFontScale
import com.brokenpip3.fatto.vm.ReviewStage
import com.brokenpip3.fatto.vm.ReviewUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewSheet(
    state: ReviewUiState,
    onAction: (ReviewAction) -> Unit,
    onAcceptNotice: () -> Unit,
    onStart: () -> Unit,
    onEdit: (Task) -> Unit,
    onSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Material hides its sheet before invoking onDismissRequest. Give the summary its own
    // dialog and animation state so ending a session cannot leave the summary off screen.
    key(state.stage == ReviewStage.SUMMARY) {
        ReviewSheetContent(state, onAction, onAcceptNotice, onStart, onEdit, onSettings, onDismiss)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewSheetContent(
    state: ReviewUiState,
    onAction: (ReviewAction) -> Unit,
    onAcceptNotice: () -> Unit,
    onStart: () -> Unit,
    onEdit: (Task) -> Unit,
    onSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val fontScale = effectiveFontScale()
    val latestState by rememberUpdatedState(state)
    var confirmEnd by remember { mutableStateOf(false) }
    val dismiss by rememberUpdatedState(onDismiss)
    val requestDismiss: () -> Unit = {
        if (!latestState.busy) {
            if (latestState.stage == ReviewStage.ACTIVE) confirmEnd = true else dismiss()
        }
    }
    val sheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { target ->
            if (target == SheetValue.Hidden && latestState.stage == ReviewStage.ACTIVE) {
                false
            } else {
                target != SheetValue.Hidden || !latestState.busy
            }
        })
    val detailScroll = key(state.stage, state.currentTask?.uuid) { rememberScrollState() }
    var confirmDelete by remember(state.currentTask?.uuid) { mutableStateOf(false) }
    val dispatch: (ReviewAction) -> Unit = { action -> if (action == ReviewAction.DELETE) confirmDelete = true else onAction(action) }
    ModalBottomSheet(
        onDismissRequest = { if (latestState.stage != ReviewStage.ACTIVE) requestDismiss() },
        sheetGesturesEnabled = state.stage != ReviewStage.ACTIVE,
        dragHandle = { if (state.stage != ReviewStage.ACTIVE) BottomSheetDefaults.DragHandle() },
        properties =
            ModalBottomSheetProperties(
                shouldDismissOnBackPress = state.stage != ReviewStage.ACTIVE && !state.busy,
                shouldDismissOnClickOutside = state.stage != ReviewStage.ACTIVE && !state.busy,
            ),
        modifier = Modifier.testTag("ReviewSheet"),
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.extraLarge,
    ) {
        val active = state.stage == ReviewStage.ACTIVE
        BoxWithConstraints(Modifier.fillMaxWidth().then(if (active) Modifier.fillMaxHeight(0.94f) else Modifier)) {
            val stackActions = fontScale >= FattoMetrics.largeFontScale || maxWidth < 320.dp
            val scrollWholeSheet = maxHeight < if (fontScale >= FattoMetrics.largeFontScale) 640.dp else 400.dp
            val wholeScroll = rememberScrollState()
            val contentSize = if (active) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
            Column(contentSize.then(if (scrollWholeSheet) Modifier.verticalScroll(wholeScroll) else Modifier)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = FattoSpacing.extraLarge), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (state.stage) {
                            ReviewStage.NOTICE -> "Review tracking"
                            ReviewStage.SUMMARY -> "Session summary"
                            else -> "Review"
                        },
                        Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall,
                    )
                    if (state.stage == ReviewStage.ACTIVE) {
                        TextButton(onClick = requestDismiss, enabled = !state.busy) { Text("End review") }
                    } else {
                        IconButton(onClick = requestDismiss, enabled = !state.busy) { Icon(Icons.Default.Close, "Close review") }
                    }
                }
                Column(
                    (if (scrollWholeSheet) Modifier else Modifier.weight(1f, fill = active).verticalScroll(detailScroll))
                        .fillMaxWidth().padding(FattoSpacing.extraLarge),
                    verticalArrangement = Arrangement.spacedBy(FattoSpacing.large),
                ) {
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    when (state.stage) {
                        ReviewStage.NOTICE -> ReviewNotice()
                        ReviewStage.START -> ReviewStartOverview(state)
                        ReviewStage.ACTIVE -> {
                            Text(
                                "Task ${state.position} of ${state.total} · ${state.reviewedActions} reviewed",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            LinearProgressIndicator(progress = {
                                if (state.total ==
                                    0
                                ) {
                                    0f
                                } else {
                                    (state.position - 1).toFloat() / state.total
                                }
                            }, modifier = Modifier.fillMaxWidth())
                            state.currentTask?.let { task -> key(task.uuid) { ReviewSwipeCard(task, state, dispatch) } }
                        }
                        ReviewStage.SUMMARY -> ReviewSessionSummary(state)
                        ReviewStage.CLOSED -> Unit
                    }
                }
                HorizontalDivider()
                Column(
                    Modifier.fillMaxWidth().padding(FattoSpacing.extraLarge),
                    verticalArrangement = Arrangement.spacedBy(FattoSpacing.small),
                ) {
                    when (state.stage) {
                        ReviewStage.ACTIVE -> {
                            Button(
                                onClick = { dispatch(ReviewAction.REVIEWED) }, enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = FattoMetrics.largeTouchTarget),
                            ) {
                                Icon(Icons.Default.TaskAlt, null)
                                Spacer(Modifier.size(FattoSpacing.small))
                                Text("Mark reviewed")
                            }
                            if (stackActions) {
                                ReviewButton("Skip", Icons.Default.SkipNext, !state.busy, Modifier.fillMaxWidth()) {
                                    dispatch(ReviewAction.SKIP)
                                }
                                ReviewButton("Edit", Icons.Default.Edit, !state.busy, Modifier.fillMaxWidth()) {
                                    state.currentTask?.let(onEdit)
                                }
                                ReviewButton("Complete", Icons.Default.Check, !state.busy, Modifier.fillMaxWidth()) {
                                    dispatch(ReviewAction.COMPLETE)
                                }
                                ReviewButton("Delete", Icons.Default.Delete, !state.busy, Modifier.fillMaxWidth(), true) {
                                    dispatch(ReviewAction.DELETE)
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small)) {
                                    ReviewButton("Skip", Icons.Default.SkipNext, !state.busy, Modifier.weight(1f)) {
                                        dispatch(ReviewAction.SKIP)
                                    }
                                    ReviewButton("Edit", Icons.Default.Edit, !state.busy, Modifier.weight(1f)) {
                                        state.currentTask?.let(onEdit)
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small)) {
                                    ReviewButton("Complete", Icons.Default.Check, !state.busy, Modifier.weight(1f)) {
                                        dispatch(ReviewAction.COMPLETE)
                                    }
                                    ReviewButton("Delete", Icons.Default.Delete, !state.busy, Modifier.weight(1f), true) {
                                        dispatch(ReviewAction.DELETE)
                                    }
                                }
                            }
                        }
                        ReviewStage.NOTICE -> {
                            Button(
                                onClick = onAcceptNotice,
                                enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = FattoMetrics.largeTouchTarget),
                            ) {
                                Text("Continue")
                            }
                            TextButton(onClick = requestDismiss, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                                Text("Cancel")
                            }
                        }
                        ReviewStage.START -> {
                            Button(
                                onClick = onStart,
                                enabled = state.total > 0 && !state.busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = FattoMetrics.largeTouchTarget),
                            ) {
                                Icon(Icons.Default.TaskAlt, null)
                                Spacer(Modifier.size(FattoSpacing.small))
                                Text("Start review")
                            }
                            TextButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text("Review settings") }
                        }
                        else ->
                            Button(
                                onClick = requestDismiss,
                                modifier = Modifier.fillMaxWidth().heightIn(min = FattoMetrics.largeTouchTarget),
                            ) {
                                Text("Done")
                            }
                    }
                }
            }
        }
    }
    if (confirmEnd && state.stage == ReviewStage.ACTIVE) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End this review?", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    "Your changes are already saved.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FattoSpacing.small)) {
                    Button(onClick = {
                        confirmEnd = false
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = FattoMetrics.largeTouchTarget)) { Text("Keep reviewing") }
                    TextButton(onClick = {
                        confirmEnd = false
                        onDismiss()
                    }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("End review") }
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = {
                confirmDelete = false
            }, title = { Text("Delete task?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("Delete “${state.currentTask?.description.orEmpty()}”? This change will sync to your other clients.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onAction(ReviewAction.DELETE)
                }) { Text("Delete task") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

@Composable
private fun ReviewButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    modifier: Modifier,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick, enabled = enabled,
        modifier = modifier.heightIn(min = FattoMetrics.largeTouchTarget), shape = MaterialTheme.shapes.medium,
        colors =
            if (danger) {
                ButtonDefaults.filledTonalButtonColors(contentColor = MaterialTheme.colorScheme.error)
            } else {
                ButtonDefaults.filledTonalButtonColors()
            },
    ) {
        Icon(icon, null)
        Spacer(Modifier.size(FattoSpacing.small))
        Text(label)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewSwipeCard(
    task: Task,
    ui: ReviewUiState,
    onAction: (ReviewAction) -> Unit,
) {
    val contentDirection = LocalLayoutDirection.current
    // Preferences name physical directions, independently of the task text’s reading direction.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        var dispatched by remember { mutableStateOf(false) }
        val dispatch by rememberUpdatedState(onAction)
        val busy by rememberUpdatedState(ui.busy)
        val settings by rememberUpdatedState(ui.settings)
        val state =
            rememberSwipeToDismissBoxState(confirmValueChange = { target ->
                if (target != SwipeToDismissBoxValue.Settled && !busy && !dispatched) {
                    dispatched = true
                    val right = target == SwipeToDismissBoxValue.StartToEnd
                    dispatch(if (right) settings.rightAction else settings.leftAction)
                }
                false
            })
        LaunchedEffect(state.dismissDirection) { if (state.dismissDirection == SwipeToDismissBoxValue.Settled) dispatched = false }
        SwipeToDismissBox(
            state = state, backgroundContent = { Box(Modifier.fillMaxWidth()) },
            enableDismissFromStartToEnd = !ui.busy, enableDismissFromEndToStart = !ui.busy,
            modifier = Modifier.testTag("ReviewSwipeCard"),
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides contentDirection) { ReviewTaskCard(task) }
        }
    }
}
