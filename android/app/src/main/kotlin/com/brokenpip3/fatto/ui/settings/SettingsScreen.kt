package com.brokenpip3.fatto.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.brokenpip3.fatto.ui.theme.NordicFrost
import com.brokenpip3.fatto.ui.theme.NordicMidnight
import com.brokenpip3.fatto.vm.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val syncUrl by viewModel.syncUrl.collectAsState()
    val clientId by viewModel.clientId.collectAsState()
    val encryptionSecret by viewModel.encryptionSecret.collectAsState()
    val showCompleted by viewModel.showCompleted.collectAsState()
    val showInternalTags by viewModel.showInternalTags.collectAsState()
    val showEmptyProjects by viewModel.showEmptyProjects.collectAsState()
    val tagsPerLine by viewModel.tagsPerLine.collectAsState()
    val dailyNotificationsEnabled by viewModel.dailyNotificationsEnabled.collectAsState()
    val notificationHour by viewModel.notificationHour.collectAsState()
    val includeDueToday by viewModel.includeDueToday.collectAsState()
    val includeScheduledToday by viewModel.includeScheduledToday.collectAsState()
    val includeOverdue by viewModel.includeOverdue.collectAsState()

    var secretVisible by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.headlineSmall) },
            )
        },
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
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // App Info Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Fatto",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Your TaskWarrior android companion",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Version ${com.brokenpip3.fatto.BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "Built on: ${com.brokenpip3.fatto.BuildConfig.BUILD_DATE}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = "Sync Configuration",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            TextField(
                value = syncUrl,
                onValueChange = viewModel::onUrlChange,
                label = { Text("Sync Server URL") },
                placeholder = { Text("http://example.com:8080") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
            )

            TextField(
                value = clientId,
                onValueChange = viewModel::onClientIdChange,
                label = { Text("Client ID (UUID)") },
                placeholder = { Text("00000000-0000-0000-0000-000000000000") },
                modifier = Modifier.fillMaxWidth(),
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
            )

            TextField(
                value = encryptionSecret,
                onValueChange = viewModel::onSecretChange,
                label = { Text("Encryption Secret") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (secretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { secretVisible = !secretVisible }) {
                        Icon(
                            imageVector = if (secretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (secretVisible) "Hide secret" else "Show secret",
                        )
                    }
                },
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        viewModel.save()
                        focusManager.clearFocus()
                        scope.launch {
                            snackbarHostState.showSnackbar("Settings saved")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                        ),
                ) {
                    Text("Save", color = Color.White)
                }

                OutlinedButton(
                    onClick = {
                        viewModel.clear()
                        focusManager.clearFocus()
                        scope.launch {
                            snackbarHostState.showSnackbar("Settings cleared")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                ) {
                    Text("Clear")
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    ),
            ) {
                Text(
                    text = "Note: Changes will be used for the next sync.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Notifications",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.onDailyNotificationsChange(!dailyNotificationsEnabled) }
                        .padding(vertical = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = dailyNotificationsEnabled,
                    onCheckedChange = { viewModel.onDailyNotificationsChange(it) },
                    colors =
                        CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                        ),
                )
                Text(
                    text = "Enable daily notifications",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            if (dailyNotificationsEnabled) {
                var expanded by remember { mutableStateOf(false) }
                val hours = (0..23).toList()

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextField(
                        value = String.format("%02d:00", notificationHour),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Daily notification time") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            ),
                        modifier =
                            Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        hours.forEach { hour ->
                            DropdownMenuItem(
                                text = { Text(String.format("%02d:00", hour)) },
                                onClick = {
                                    viewModel.onNotificationHourChange(hour)
                                    expanded = false
                                },
                            )
                        }
                    }
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.onIncludeDueTodayChange(!includeDueToday) }
                            .padding(vertical = 4.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = includeDueToday,
                        onCheckedChange = { viewModel.onIncludeDueTodayChange(it) },
                        colors =
                            CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                            ),
                    )
                    Text(
                        text = "Include tasks due today",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.onIncludeScheduledTodayChange(!includeScheduledToday) }
                            .padding(vertical = 4.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = includeScheduledToday,
                        onCheckedChange = { viewModel.onIncludeScheduledTodayChange(it) },
                        colors =
                            CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                            ),
                    )
                    Text(
                        text = "Include tasks scheduled today",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.onIncludeOverdueChange(!includeOverdue) }
                            .padding(vertical = 4.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = includeOverdue,
                        onCheckedChange = { viewModel.onIncludeOverdueChange(it) },
                        colors =
                            CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                            ),
                    )
                    Text(
                        text = "Include overdue tasks",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Visualization",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.onShowCompletedChange(!showCompleted) }
                        .padding(vertical = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = showCompleted,
                    onCheckedChange = { viewModel.onShowCompletedChange(it) },
                    colors =
                        CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                        ),
                )
                Text(
                    text = "Show completed tasks",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.onShowInternalTagsChange(!showInternalTags) }
                        .padding(vertical = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = showInternalTags,
                    onCheckedChange = { viewModel.onShowInternalTagsChange(it) },
                    colors =
                        CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                        ),
                )
                Text(
                    text = "Show internal tags",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.onShowEmptyProjectsChange(!showEmptyProjects) }
                        .padding(vertical = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = showEmptyProjects,
                    onCheckedChange = { viewModel.onShowEmptyProjectsChange(it) },
                    colors =
                        CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                        ),
                )
                Text(
                    text = "Show empty projects",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(
                    text = "Tags per line: $tagsPerLine",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Slider(
                    value = tagsPerLine.toFloat(),
                    onValueChange = { viewModel.onTagsPerLineChange(it.toInt()) },
                    valueRange = 2f..6f,
                    steps = 3,
                    colors =
                        SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                )
            }
        }
    }
}
