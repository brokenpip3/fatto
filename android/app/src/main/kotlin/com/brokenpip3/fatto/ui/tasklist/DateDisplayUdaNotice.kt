package com.brokenpip3.fatto.ui.tasklist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.DialogProperties
import com.brokenpip3.fatto.data.DateDisplayConsentStore
import com.brokenpip3.fatto.data.FileDateDisplayConsentStore
import com.brokenpip3.fatto.ui.theme.FattoSpacing

@Composable
fun rememberDateDisplayConsentStore(): DateDisplayConsentStore {
    val context = LocalContext.current.applicationContext
    return remember(context) { FileDateDisplayConsentStore(context.noBackupFilesDir) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateDisplayUdaNotice(
    onContinue: () -> Unit,
    onCancel: () -> Unit,
    error: String? = null,
) {
    val uriHandler = LocalUriHandler.current
    BasicAlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.fillMaxWidth().padding(horizontal = FattoSpacing.small),
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth().testTag("DateDisplayUdaNotice"),
        ) {
            Column(Modifier.padding(FattoSpacing.large), verticalArrangement = Arrangement.spacedBy(FattoSpacing.medium)) {
                Text("Task time display", style = MaterialTheme.typography.headlineSmall)
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(FattoSpacing.medium),
                ) {
                    Text(
                        "Saving a time adds a Taskwarrior User Defined Attribute (UDA), such as fatto_display_due. " +
                            "This key/value string lets Fatto remember and sync time display without changing " +
                            "Taskwarrior’s standard date behavior.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    TextButton(onClick = { uriHandler.openUri("https://taskwarrior.org/docs/udas/") }) { Text("Learn more") }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                }
                FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    TextButton(onClick = onContinue) { Text("Continue") }
                }
            }
        }
    }
}
