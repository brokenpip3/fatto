package com.brokenpip3.fatto.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import com.brokenpip3.fatto.data.SyncDiagnosticEvent
import com.brokenpip3.fatto.data.SyncDiagnosticsFormatter
import com.brokenpip3.fatto.ui.theme.FattoMetrics
import com.brokenpip3.fatto.ui.theme.FattoSpacing
import com.brokenpip3.fatto.ui.theme.effectiveFontScale
import java.text.DateFormat
import java.util.Date

@Composable
internal fun SyncDiagnosticsDialog(
    events: List<SyncDiagnosticEvent>,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit,
) {
    val compactActions =
        LocalConfiguration.current.screenWidthDp < FattoMetrics.compactLayoutWidth.value ||
            effectiveFontScale() >= FattoMetrics.largeFontScale
    val maxActionsPerRow = if (compactActions) 1 else 2

    AlertDialog(
        modifier = Modifier.testTag("DiagnosticsView"),
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        onDismissRequest = onDismiss,
        title = { Text("Sync diagnostics", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(FattoSpacing.small),
            ) {
                if (events.isEmpty()) {
                    Text(
                        text = "No diagnostics yet",
                        modifier = Modifier.padding(vertical = FattoSpacing.large),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    Text(
                        text = "Stored on this device. URLs and credentials are redacted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    HorizontalDivider()
                    LazyColumn(
                        modifier = Modifier.heightIn(max = FattoMetrics.formBodyMaxHeight),
                        verticalArrangement = Arrangement.spacedBy(FattoSpacing.small),
                    ) {
                        items(events.reversed()) { rawEvent ->
                            DiagnosticEventCard(rawEvent)
                        }
                    }
                }
            }
        },
        confirmButton = {
            FlowRow(
                maxItemsInEachRow = maxActionsPerRow,
                horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small),
                verticalArrangement = Arrangement.spacedBy(FattoSpacing.small),
            ) {
                TextButton(onClick = onCopy, enabled = events.isNotEmpty()) { Text("Copy") }
                TextButton(onClick = onShare, enabled = events.isNotEmpty()) { Text("Share") }
            }
        },
        dismissButton = {
            FlowRow(
                maxItemsInEachRow = maxActionsPerRow,
                horizontalArrangement = Arrangement.spacedBy(FattoSpacing.small),
                verticalArrangement = Arrangement.spacedBy(FattoSpacing.small),
            ) {
                TextButton(
                    onClick = onClear,
                    enabled = events.isNotEmpty(),
                    modifier = Modifier.testTag("ClearDiagnosticsButton"),
                ) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

@Composable
private fun DiagnosticEventCard(event: SyncDiagnosticEvent) {
    val safeEvent = SyncDiagnosticsFormatter.sanitizeEvent(event)
    Card(
        modifier = Modifier.fillMaxWidth().testTag("DiagnosticEvent"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(FattoSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(FattoSpacing.xSmall),
        ) {
            Text(
                text = "${formatTimestamp(safeEvent.timestampEpochMillis)} · ${safeEvent.stage} · ${safeEvent.outcome}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(safeEvent.summary, style = MaterialTheme.typography.bodyMedium)
            safeEvent.serverOrigin?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            }
            safeEvent.elapsedMillis?.let {
                Text(
                    "${it}ms · ${safeEvent.appVersion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun formatTimestamp(timestampEpochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestampEpochMillis))
