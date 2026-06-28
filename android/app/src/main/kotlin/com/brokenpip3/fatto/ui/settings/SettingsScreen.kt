package com.brokenpip3.fatto.ui.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.brokenpip3.fatto.data.SyncType
import com.brokenpip3.fatto.data.TaskrcImportPreview
import com.brokenpip3.fatto.data.TaskrcImportResultType
import com.brokenpip3.fatto.data.model.TaskContext
import com.brokenpip3.fatto.ui.tasklist.TaskFilterBuilderPurpose
import com.brokenpip3.fatto.ui.tasklist.TaskFilterBuilderSheet
import com.brokenpip3.fatto.ui.tasklist.TaskFilterState
import com.brokenpip3.fatto.ui.theme.ThemeMode
import com.brokenpip3.fatto.vm.SettingsViewModel
import kotlinx.coroutines.launch

private enum class SettingsTab(
    val label: String,
    val tag: String,
) {
    SYNC("Sync", "SettingsTabSync"),
    TASKRC("Taskrc", "SettingsTabTaskrc"),
    DISPLAY("Display", "SettingsTabDisplay"),
    NOTIFICATIONS("Notifications", "SettingsTabNotifications"),
    ABOUT("About", "SettingsTabAbout"),
}

private data class SyncSettingsSectionState(
    val syncType: SyncType,
    val syncUrl: String,
    val clientId: String,
    val encryptionSecret: String,
    val secretVisible: Boolean,
    val s3Bucket: String,
    val s3Region: String,
    val s3EndpointUrl: String,
    val s3AccessKeyId: String,
    val s3SecretAccessKey: String,
    val s3SecretVisible: Boolean,
)

private data class SyncSettingsSectionActions(
    val onSyncTypeChange: (SyncType) -> Unit,
    val onSecretVisibleChange: (Boolean) -> Unit,
    val onSyncUrlChange: (String) -> Unit,
    val onClientIdChange: (String) -> Unit,
    val onSecretChange: (String) -> Unit,
    val onS3BucketChange: (String) -> Unit,
    val onS3RegionChange: (String) -> Unit,
    val onS3EndpointUrlChange: (String) -> Unit,
    val onS3AccessKeyIdChange: (String) -> Unit,
    val onS3SecretAccessKeyChange: (String) -> Unit,
    val onS3SecretVisibleChange: (Boolean) -> Unit,
    val onSave: () -> Unit,
    val onClear: () -> Unit,
)

private data class ContextSettingsSectionState(
    val taskrcImportText: String,
    val taskrcImportPreview: TaskrcImportPreview?,
    val taskContexts: List<TaskContext>,
    val activeTaskContextId: String?,
)

private data class ContextSettingsSectionActions(
    val onTaskrcImportTextChange: (String) -> Unit,
    val onPreviewTaskrcImport: () -> Unit,
    val onApplyTaskrcImport: () -> Unit,
    val onUseContext: (String) -> Unit,
    val onEditContext: (TaskContext) -> Unit,
    val onDeleteContext: (String) -> Unit,
)

private data class DisplaySettingsSectionState(
    val showCompleted: Boolean,
    val showInternalTags: Boolean,
    val showEmptyProjects: Boolean,
    val showWaitingTasks: Boolean,
    val showPriorityBadge: Boolean,
    val showUrgencyBar: Boolean,
    val hideBlockedTasksWaiting: Boolean,
    val tagsPerLine: Int,
)

private data class DisplaySettingsSectionActions(
    val onShowCompletedChange: (Boolean) -> Unit,
    val onShowInternalTagsChange: (Boolean) -> Unit,
    val onShowEmptyProjectsChange: (Boolean) -> Unit,
    val onShowWaitingTasksChange: (Boolean) -> Unit,
    val onShowPriorityBadgeChange: (Boolean) -> Unit,
    val onShowUrgencyBarChange: (Boolean) -> Unit,
    val onHideBlockedTasksWaitingChange: (Boolean) -> Unit,
    val onTagsPerLineChange: (Int) -> Unit,
)

private data class NotificationSettingsSectionState(
    val dailyNotificationsEnabled: Boolean,
    val notificationHour: Int,
    val includeDueToday: Boolean,
    val includeScheduledToday: Boolean,
    val includeOverdue: Boolean,
)

private data class NotificationSettingsSectionActions(
    val onDailyNotificationsChange: (Boolean) -> Unit,
    val onNotificationHourChange: (Int) -> Unit,
    val onIncludeDueTodayChange: (Boolean) -> Unit,
    val onIncludeScheduledTodayChange: (Boolean) -> Unit,
    val onIncludeOverdueChange: (Boolean) -> Unit,
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    availableProjects: List<String>,
    availableTags: Set<String>,
) {
    val syncType by viewModel.syncType.collectAsState()
    val syncUrl by viewModel.syncUrl.collectAsState()
    val clientId by viewModel.clientId.collectAsState()
    val encryptionSecret by viewModel.encryptionSecret.collectAsState()
    val s3Bucket by viewModel.s3Bucket.collectAsState()
    val s3Region by viewModel.s3Region.collectAsState()
    val s3EndpointUrl by viewModel.s3EndpointUrl.collectAsState()
    val s3AccessKeyId by viewModel.s3AccessKeyId.collectAsState()
    val s3SecretAccessKey by viewModel.s3SecretAccessKey.collectAsState()
    val showCompleted by viewModel.showCompleted.collectAsState()
    val showInternalTags by viewModel.showInternalTags.collectAsState()
    val showEmptyProjects by viewModel.showEmptyProjects.collectAsState()
    val tagsPerLine by viewModel.tagsPerLine.collectAsState()
    val dailyNotificationsEnabled by viewModel.dailyNotificationsEnabled.collectAsState()
    val notificationHour by viewModel.notificationHour.collectAsState()
    val includeDueToday by viewModel.includeDueToday.collectAsState()
    val includeScheduledToday by viewModel.includeScheduledToday.collectAsState()
    val includeOverdue by viewModel.includeOverdue.collectAsState()
    val firstDayOfWeek by viewModel.firstDayOfWeek.collectAsState()
    val confirmActions by viewModel.confirmActions.collectAsState()
    val hideBlockedTasksWaiting by viewModel.hideBlockedTasksWaiting.collectAsState()
    val showWaitingTasks by viewModel.showWaitingTasks.collectAsState()
    val showPriorityBadge by viewModel.showPriorityBadge.collectAsState()
    val showUrgencyBar by viewModel.showUrgencyBar.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val taskContexts by viewModel.taskContexts.collectAsState()
    val activeTaskContextId by viewModel.activeTaskContextId.collectAsState()
    val taskrcImportText by viewModel.taskrcImportText.collectAsState()
    val taskrcImportPreview by viewModel.taskrcImportPreview.collectAsState()

    var selectedTab by rememberSaveable { mutableStateOf(SettingsTab.SYNC) }
    val syncScrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    val taskrcScrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    val displayScrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    val notificationsScrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    val aboutScrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    var secretVisible by remember { mutableStateOf(false) }
    var s3SecretVisible by remember { mutableStateOf(false) }
    var editingContext by remember { mutableStateOf<TaskContext?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    fun launchSnackbar(message: String) {
        scope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    val onSaveSyncSettings: () -> Unit = {
        val saved = viewModel.save()
        focusManager.clearFocus()
        launchSnackbar(if (saved) "Settings saved" else "Fill in all required sync fields")
    }
    val onClearSyncSettings: () -> Unit = {
        viewModel.clear()
        focusManager.clearFocus()
        launchSnackbar("Settings cleared")
    }
    val onApplyTaskrcImport: () -> Unit = {
        viewModel.applyTaskrcImport()
        launchSnackbar("Taskrc import applied")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.headlineSmall) },
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionContentColor = MaterialTheme.colorScheme.inversePrimary,
                ) {
                    Text(
                        text = data.visuals.message,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
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
                    .padding(padding),
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                modifier = Modifier.fillMaxWidth().testTag("SettingsTabs"),
                edgePadding = 0.dp,
            ) {
                SettingsTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        modifier = Modifier.testTag(tab.tag),
                        text = { Text(tab.label) },
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    SettingsTab.SYNC ->
                        SyncSettingsSection(
                            scrollState = syncScrollState,
                            state =
                                SyncSettingsSectionState(
                                    syncType = syncType,
                                    syncUrl = syncUrl,
                                    clientId = clientId,
                                    encryptionSecret = encryptionSecret,
                                    secretVisible = secretVisible,
                                    s3Bucket = s3Bucket,
                                    s3Region = s3Region,
                                    s3EndpointUrl = s3EndpointUrl,
                                    s3AccessKeyId = s3AccessKeyId,
                                    s3SecretAccessKey = s3SecretAccessKey,
                                    s3SecretVisible = s3SecretVisible,
                                ),
                            actions =
                                SyncSettingsSectionActions(
                                    onSyncTypeChange = viewModel::onSyncTypeChange,
                                    onSecretVisibleChange = { secretVisible = it },
                                    onSyncUrlChange = viewModel::onUrlChange,
                                    onClientIdChange = viewModel::onClientIdChange,
                                    onSecretChange = viewModel::onSecretChange,
                                    onS3BucketChange = viewModel::onS3BucketChange,
                                    onS3RegionChange = viewModel::onS3RegionChange,
                                    onS3EndpointUrlChange = viewModel::onS3EndpointUrlChange,
                                    onS3AccessKeyIdChange = viewModel::onS3AccessKeyIdChange,
                                    onS3SecretAccessKeyChange = viewModel::onS3SecretAccessKeyChange,
                                    onS3SecretVisibleChange = { s3SecretVisible = it },
                                    onSave = onSaveSyncSettings,
                                    onClear = onClearSyncSettings,
                                ),
                        )

                    SettingsTab.TASKRC ->
                        ContextSettingsSection(
                            scrollState = taskrcScrollState,
                            firstDayOfWeek = firstDayOfWeek,
                            state =
                                ContextSettingsSectionState(
                                    taskrcImportText = taskrcImportText,
                                    taskrcImportPreview = taskrcImportPreview,
                                    taskContexts = taskContexts,
                                    activeTaskContextId = activeTaskContextId,
                                ),
                            actions =
                                ContextSettingsSectionActions(
                                    onTaskrcImportTextChange = viewModel::onTaskrcImportTextChange,
                                    onPreviewTaskrcImport = viewModel::previewTaskrcImport,
                                    onApplyTaskrcImport = onApplyTaskrcImport,
                                    onUseContext = viewModel::setActiveTaskContext,
                                    onEditContext = { editingContext = it },
                                    onDeleteContext = viewModel::deleteTaskContext,
                                ),
                            onFirstDayOfWeekChange = viewModel::onFirstDayOfWeekChange,
                        )

                    SettingsTab.DISPLAY ->
                        DisplaySettingsSection(
                            scrollState = displayScrollState,
                            themeMode = themeMode,
                            confirmActions = confirmActions,
                            state =
                                DisplaySettingsSectionState(
                                    showCompleted = showCompleted,
                                    showInternalTags = showInternalTags,
                                    showEmptyProjects = showEmptyProjects,
                                    showWaitingTasks = showWaitingTasks,
                                    showPriorityBadge = showPriorityBadge,
                                    showUrgencyBar = showUrgencyBar,
                                    hideBlockedTasksWaiting = hideBlockedTasksWaiting,
                                    tagsPerLine = tagsPerLine,
                                ),
                            actions =
                                DisplaySettingsSectionActions(
                                    onShowCompletedChange = viewModel::onShowCompletedChange,
                                    onShowInternalTagsChange = viewModel::onShowInternalTagsChange,
                                    onShowEmptyProjectsChange = viewModel::onShowEmptyProjectsChange,
                                    onShowWaitingTasksChange = viewModel::onShowWaitingTasksChange,
                                    onShowPriorityBadgeChange = viewModel::onShowPriorityBadgeChange,
                                    onShowUrgencyBarChange = viewModel::onShowUrgencyBarChange,
                                    onHideBlockedTasksWaitingChange = viewModel::onHideBlockedTasksWaitingChange,
                                    onTagsPerLineChange = viewModel::onTagsPerLineChange,
                                ),
                            onThemeModeChange = viewModel::onThemeModeChange,
                            onConfirmActionsChange = viewModel::onConfirmActionsChange,
                        )

                    SettingsTab.NOTIFICATIONS ->
                        NotificationSettingsSection(
                            scrollState = notificationsScrollState,
                            state =
                                NotificationSettingsSectionState(
                                    dailyNotificationsEnabled = dailyNotificationsEnabled,
                                    notificationHour = notificationHour,
                                    includeDueToday = includeDueToday,
                                    includeScheduledToday = includeScheduledToday,
                                    includeOverdue = includeOverdue,
                                ),
                            actions =
                                NotificationSettingsSectionActions(
                                    onDailyNotificationsChange = viewModel::onDailyNotificationsChange,
                                    onNotificationHourChange = viewModel::onNotificationHourChange,
                                    onIncludeDueTodayChange = viewModel::onIncludeDueTodayChange,
                                    onIncludeScheduledTodayChange = viewModel::onIncludeScheduledTodayChange,
                                    onIncludeOverdueChange = viewModel::onIncludeOverdueChange,
                                ),
                        )

                    SettingsTab.ABOUT ->
                        AboutSettingsSection(
                            scrollState = aboutScrollState,
                        )
                }
            }

            editingContext?.let { context ->
                TaskFilterBuilderSheet(
                    initialState = TaskFilterState.fromContext(context),
                    availableProjects = availableProjects,
                    availableTags = availableTags,
                    contextName = context.name,
                    purpose = TaskFilterBuilderPurpose.CONTEXT,
                    onDismiss = { editingContext = null },
                    onApply = { filter ->
                        viewModel.saveTaskContext(filter.toContext(name = context.name, id = context.id))
                        editingContext = null
                    },
                    onSaveContext = { name, filter ->
                        viewModel.saveTaskContext(filter.toContext(name = name, id = context.id))
                        editingContext = null
                    },
                )
            }
        }
    }
}

@Composable
private fun AboutSettingsSection(scrollState: ScrollState) {
    SettingsSection(scrollState = scrollState) {
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
    }
}

@Composable
private fun SyncSettingsSection(
    scrollState: ScrollState,
    state: SyncSettingsSectionState,
    actions: SyncSettingsSectionActions,
) {
    SettingsSection(scrollState = scrollState) {
        Text(
            text = "Sync Configuration",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Row(
                modifier =
                    Modifier
                        .selectable(
                            selected = state.syncType == SyncType.SERVER,
                            onClick = { actions.onSyncTypeChange(SyncType.SERVER) },
                            role = Role.RadioButton,
                        ),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = state.syncType == SyncType.SERVER,
                    onClick = null,
                )
                Text(
                    text = "Sync server",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Row(
                modifier =
                    Modifier
                        .selectable(
                            selected = state.syncType == SyncType.S3,
                            onClick = { actions.onSyncTypeChange(SyncType.S3) },
                            role = Role.RadioButton,
                        ),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = state.syncType == SyncType.S3,
                    onClick = null,
                )
                Text(
                    text = "S3 storage",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        if (state.syncType == SyncType.SERVER) {
            TextField(
                value = state.syncUrl,
                onValueChange = actions.onSyncUrlChange,
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
                value = state.clientId,
                onValueChange = actions.onClientIdChange,
                label = { Text("Client ID (UUID)") },
                placeholder = { Text("00000000-0000-0000-0000-000000000000") },
                modifier = Modifier.fillMaxWidth(),
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        } else {
            TextField(
                value = state.s3Bucket,
                onValueChange = actions.onS3BucketChange,
                label = { Text("Bucket") },
                placeholder = { Text("my-tasks-bucket") },
                modifier = Modifier.fillMaxWidth(),
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
            )

            TextField(
                value = state.s3EndpointUrl,
                onValueChange = actions.onS3EndpointUrlChange,
                label = { Text("Endpoint URL (optional)") },
                placeholder = { Text("https://minio.example.com") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
            )

            TextField(
                value = state.s3Region,
                onValueChange = actions.onS3RegionChange,
                label = { Text("Region (optional)") },
                placeholder = { Text("us-east-1") },
                modifier = Modifier.fillMaxWidth(),
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
            )

            TextField(
                value = state.s3AccessKeyId,
                onValueChange = actions.onS3AccessKeyIdChange,
                label = { Text("Access Key ID") },
                modifier = Modifier.fillMaxWidth(),
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
            )

            TextField(
                value = state.s3SecretAccessKey,
                onValueChange = actions.onS3SecretAccessKeyChange,
                label = { Text("Secret Access Key") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (state.s3SecretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { actions.onS3SecretVisibleChange(!state.s3SecretVisible) }) {
                        Icon(
                            imageVector = if (state.s3SecretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (state.s3SecretVisible) "Hide secret" else "Show secret",
                        )
                    }
                },
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        }

        TextField(
            value = state.encryptionSecret,
            onValueChange = actions.onSecretChange,
            label = { Text("Encryption Secret") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (state.secretVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { actions.onSecretVisibleChange(!state.secretVisible) }) {
                    Icon(
                        imageVector = if (state.secretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (state.secretVisible) "Hide secret" else "Show secret",
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
                onClick = actions.onSave,
                modifier = Modifier.weight(1f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Text("Save")
            }

            OutlinedButton(
                onClick = actions.onClear,
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
    }
}

@Composable
private fun ContextSettingsSection(
    scrollState: ScrollState,
    firstDayOfWeek: Int,
    state: ContextSettingsSectionState,
    actions: ContextSettingsSectionActions,
    onFirstDayOfWeekChange: (Int) -> Unit,
) {
    SettingsSection(scrollState = scrollState) {
        Text(
            text = "Taskrc",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        TaskrcImportSection(
            taskrcImportText = state.taskrcImportText,
            taskrcImportPreview = state.taskrcImportPreview,
            onTaskrcImportTextChange = actions.onTaskrcImportTextChange,
            onPreviewTaskrcImport = actions.onPreviewTaskrcImport,
            onApplyTaskrcImport = actions.onApplyTaskrcImport,
        )

        Text(
            text = "Contexts",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        if (state.taskContexts.isEmpty()) {
            Text(
                text = "No saved contexts",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.taskContexts.forEach { context ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = context.name,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = context.summary(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            TextButton(onClick = { actions.onUseContext(context.id) }) {
                                Text(if (state.activeTaskContextId == context.id) "Active" else "Use")
                            }
                            TextButton(onClick = { actions.onEditContext(context) }) {
                                Text("Edit")
                            }
                            TextButton(onClick = { actions.onDeleteContext(context.id) }) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        FirstDayOfWeekSetting(
            firstDayOfWeek = firstDayOfWeek,
            onFirstDayOfWeekChange = onFirstDayOfWeekChange,
        )
    }
}

@Composable
private fun DisplaySettingsSection(
    scrollState: ScrollState,
    themeMode: ThemeMode,
    confirmActions: Boolean,
    state: DisplaySettingsSectionState,
    actions: DisplaySettingsSectionActions,
    onThemeModeChange: (ThemeMode) -> Unit,
    onConfirmActionsChange: (Boolean) -> Unit,
) {
    SettingsSection(scrollState = scrollState) {
        Text(
            text = "Display",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        ThemeModeSetting(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
        )

        SettingsCheckboxRow(
            checked = confirmActions,
            onCheckedChange = onConfirmActionsChange,
            label = "Confirm complete/delete",
        )

        SettingsCheckboxRow(
            checked = state.showCompleted,
            onCheckedChange = actions.onShowCompletedChange,
            label = "Show completed tasks",
        )

        SettingsCheckboxRow(
            checked = state.showInternalTags,
            onCheckedChange = actions.onShowInternalTagsChange,
            label = "Show internal tags",
        )

        SettingsCheckboxRow(
            checked = state.showEmptyProjects,
            onCheckedChange = actions.onShowEmptyProjectsChange,
            label = "Show empty projects",
        )

        SettingsCheckboxRow(
            checked = state.showWaitingTasks,
            onCheckedChange = actions.onShowWaitingTasksChange,
            label = "Show waiting tasks",
        )

        SettingsCheckboxRow(
            checked = state.showPriorityBadge,
            onCheckedChange = actions.onShowPriorityBadgeChange,
            label = "Show priority badge",
        )

        SettingsCheckboxRow(
            checked = state.showUrgencyBar,
            onCheckedChange = actions.onShowUrgencyBarChange,
            label = "Show urgency bar",
        )

        SettingsCheckboxRow(
            checked = state.hideBlockedTasksWaiting,
            onCheckedChange = actions.onHideBlockedTasksWaitingChange,
            label = "Hide blocked tasks (waiting-only deps)",
        )

        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(
                text = "Tags per line: ${state.tagsPerLine}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Slider(
                value = state.tagsPerLine.toFloat(),
                onValueChange = { actions.onTagsPerLineChange(it.toInt()) },
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

@Composable
private fun ThemeModeSetting(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    Text(
        text = "Theme",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )

    Row(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThemeMode.entries.forEach { mode ->
            val selected = themeMode == mode
            OutlinedButton(
                onClick = { onThemeModeChange(mode) },
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics {
                            this.selected = selected
                            this.role = Role.RadioButton
                        },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        containerColor =
                            if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.Transparent
                            },
                        contentColor =
                            if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                    ),
            ) {
                Text(
                    text =
                        when (mode) {
                            ThemeMode.SYSTEM -> "System"
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                        },
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun FirstDayOfWeekSetting(
    firstDayOfWeek: Int,
    onFirstDayOfWeekChange: (Int) -> Unit,
) {
    Text(
        text = "First day of week",
        style = MaterialTheme.typography.bodyLarge,
    )

    Row(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .selectable(
                        selected = firstDayOfWeek == java.util.Calendar.MONDAY,
                        onClick = { onFirstDayOfWeekChange(java.util.Calendar.MONDAY) },
                        role = Role.RadioButton,
                    ),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = firstDayOfWeek == java.util.Calendar.MONDAY,
                onClick = null,
            )
            Text(
                text = "Monday",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Row(
            modifier =
                Modifier
                    .selectable(
                        selected = firstDayOfWeek == java.util.Calendar.SUNDAY,
                        onClick = { onFirstDayOfWeekChange(java.util.Calendar.SUNDAY) },
                        role = Role.RadioButton,
                    ),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = firstDayOfWeek == java.util.Calendar.SUNDAY,
                onClick = null,
            )
            Text(
                text = "Sunday",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationSettingsSection(
    scrollState: ScrollState,
    state: NotificationSettingsSectionState,
    actions: NotificationSettingsSectionActions,
) {
    SettingsSection(scrollState = scrollState) {
        Text(
            text = "Notifications",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        SettingsCheckboxRow(
            checked = state.dailyNotificationsEnabled,
            onCheckedChange = actions.onDailyNotificationsChange,
            label = "Enable daily notifications",
        )

        if (state.dailyNotificationsEnabled) {
            var expanded by remember { mutableStateOf(false) }
            val hours = (0..23).toList()

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextField(
                    value = String.format("%02d:00", state.notificationHour),
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
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
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
                                actions.onNotificationHourChange(hour)
                                expanded = false
                            },
                        )
                    }
                }
            }

            SettingsCheckboxRow(
                checked = state.includeDueToday,
                onCheckedChange = actions.onIncludeDueTodayChange,
                label = "Include tasks due today",
            )

            SettingsCheckboxRow(
                checked = state.includeScheduledToday,
                onCheckedChange = actions.onIncludeScheduledTodayChange,
                label = "Include tasks scheduled today",
            )

            SettingsCheckboxRow(
                checked = state.includeOverdue,
                onCheckedChange = actions.onIncludeOverdueChange,
                label = "Include overdue tasks",
            )
        }
    }
}

@Composable
private fun SettingsSection(
    scrollState: ScrollState,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun TaskrcImportSection(
    taskrcImportText: String,
    taskrcImportPreview: TaskrcImportPreview?,
    onTaskrcImportTextChange: (String) -> Unit,
    onPreviewTaskrcImport: () -> Unit,
    onApplyTaskrcImport: () -> Unit,
) {
    Text(
        text = "Taskrc import",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )

    TextField(
        value = taskrcImportText,
        onValueChange = onTaskrcImportTextChange,
        label = { Text("Paste .taskrc") },
        minLines = 4,
        maxLines = 8,
        modifier = Modifier.fillMaxWidth(),
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
        OutlinedButton(
            onClick = onPreviewTaskrcImport,
            enabled = taskrcImportText.isNotBlank(),
            modifier = Modifier.weight(1f),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        ) {
            Text("Preview import")
        }
        Button(
            onClick = onApplyTaskrcImport,
            enabled = taskrcImportPreview != null && taskrcImportPreview.hasErrors == false,
            modifier = Modifier.weight(1f),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        ) {
            Text("Apply import")
        }
    }

    taskrcImportPreview?.let { preview ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            preview.actions.forEach { action ->
                val color =
                    when (action.type) {
                        TaskrcImportResultType.ERROR -> MaterialTheme.colorScheme.error
                        TaskrcImportResultType.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                Text(
                    text =
                        if (action.lineNumber > 0) {
                            "Line ${action.lineNumber}: ${action.type} ${action.message}"
                        } else {
                            "${action.type} ${action.message}"
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                )
            }
        }
    }
}

@Composable
private fun SettingsCheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    onValueChange = onCheckedChange,
                    role = Role.Checkbox,
                )
                .padding(vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors =
                CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
