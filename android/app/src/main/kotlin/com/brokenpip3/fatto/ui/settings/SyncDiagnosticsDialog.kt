package com.brokenpip3.fatto.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.brokenpip3.fatto.data.SyncDiagnosticEvent
import com.brokenpip3.fatto.data.SyncDiagnosticsFormatter
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
    AlertDialog(
        modifier = Modifier.testTag("DiagnosticsView"),
        onDismissRequest = onDismiss,
        title = { Text("Sync diagnostics") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.65f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (events.isEmpty()) {
                    Text(
                        text = "No diagnostics yet",
                        modifier = Modifier.padding(vertical = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "Stored on this device. URLs and credentials are redacted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider()
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(events.reversed()) { rawEvent ->
                            DiagnosticEventCard(rawEvent)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onCopy, enabled = events.isNotEmpty()) { Text("Copy") }
                TextButton(onClick = onShare, enabled = events.isNotEmpty()) { Text("Share") }
            }
        },
        dismissButton = {
            Row {
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "${formatTimestamp(safeEvent.timestampEpochMillis)} · ${safeEvent.stage} · ${safeEvent.outcome}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(safeEvent.summary, style = MaterialTheme.typography.bodyMedium)
            safeEvent.serverOrigin?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            safeEvent.elapsedMillis?.let {
                Text(
                    "${it}ms · ${safeEvent.appVersion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatTimestamp(timestampEpochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestampEpochMillis))
