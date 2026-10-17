package com.brokenpip3.fatto.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.brokenpip3.fatto.data.review.ReviewAction
import com.brokenpip3.fatto.data.review.ReviewSettings
import com.brokenpip3.fatto.notification.ReviewReminderScheduler
import com.brokenpip3.fatto.ui.theme.FattoFieldDefaults
import com.brokenpip3.fatto.ui.theme.FattoSpacing

@Composable
fun ReviewSettingsSection(
    settings: ReviewSettings,
    projects: List<String>,
    tags: List<String>,
    onChange: (ReviewSettings) -> Unit,
    error: String? = null,
) {
    val context = LocalContext.current
    val owner = LocalView.current.findViewTreeLifecycleOwner()
    var notificationRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) notificationRevision++ }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
    val notificationsEnabled =
        remember(notificationRevision, settings.reminderDays) {
            ReviewReminderScheduler.notificationsEnabled(context)
        }
    Column(verticalArrangement = Arrangement.spacedBy(FattoSpacing.large)) {
        Text("Review", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text("Review pending and waiting tasks, oldest reviewed first.", style = MaterialTheme.typography.bodyMedium)
        ReviewNumberField("Review interval (days)", settings.intervalDays, false) {
            onChange(settings.copy(intervalDays = checkNotNull(it)))
        }
        ReviewNumberField("Tasks per session", settings.sessionLimit, true) { onChange(settings.copy(sessionLimit = it)) }
        ReviewChoiceField("Project", settings.project ?: "All projects", listOf("All projects") + projects) {
            onChange(settings.copy(project = it.takeUnless { value -> value == "All projects" }))
        }
        ReviewChoiceField("Tag", settings.tag ?: "All tags", listOf("All tags") + tags.sorted()) {
            onChange(settings.copy(tag = it.takeUnless { value -> value == "All tags" }))
        }
        Text("Swipe actions", style = MaterialTheme.typography.titleMedium)
        Text("These actions apply only to review.", style = MaterialTheme.typography.bodyMedium)
        ReviewChoiceField("Swipe right", settings.rightAction.label, ReviewAction.entries.map { it.label }) {
            onChange(settings.copy(rightAction = ReviewAction.entries.first { action -> action.label == it }))
        }
        ReviewChoiceField("Swipe left", settings.leftAction.label, ReviewAction.entries.map { it.label }) {
            onChange(settings.copy(leftAction = ReviewAction.entries.first { action -> action.label == it }))
        }
        Text("Review reminders", style = MaterialTheme.typography.titleMedium)
        var custom by remember {
            mutableStateOf(
                settings.reminderDays != null && settings.reminderDays !in listOf(7, 30),
            )
        }
        val selected =
            if (custom) {
                "Custom"
            } else {
                when (settings.reminderDays) {
                    null -> "Off"
                    7 -> "Every 7 days"
                    else -> "Every 30 days"
                }
            }
        ReviewChoiceField("Remind me to review", selected, listOf("Off", "Every 7 days", "Every 30 days", "Custom")) {
            custom = it == "Custom"
            when (it) {
                "Off" -> onChange(settings.copy(reminderDays = null))
                "Every 7 days" -> onChange(settings.copy(reminderDays = 7))
                "Every 30 days" -> onChange(settings.copy(reminderDays = 30))
                else -> Unit
            }
        }
        if (custom) {
            ReviewNumberField("Reminder interval (days)", settings.reminderDays, false) {
                onChange(settings.copy(reminderDays = checkNotNull(it)))
            }
        }
        Text(
            "Reminders are separate from task eligibility. A session with saved review activity restarts the countdown.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (settings.reminderDays != null && !notificationsEnabled) {
            Text(
                "Review notifications are blocked. Enable Fatto and its Review reminders channel in Android notification settings.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
            )
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun ReviewNumberField(
    label: String,
    value: Int?,
    optional: Boolean,
    onValid: (Int?) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value?.toString().orEmpty()) }
    val valid = (optional && text.isBlank()) || (text.toIntOrNull()?.let { it > 0 } == true)
    TextField(
        value = text, onValueChange = { raw ->
            text = raw
            val parsed = raw.toIntOrNull()
            if (optional && raw.isBlank()) {
                onValid(null)
            } else if (parsed != null && parsed > 0) {
                onValid(parsed)
            }
        }, label = { Text(label) }, isError = !valid,
        supportingText = {
            Text(
                if (!valid) {
                    "Enter a positive whole number"
                } else if (optional) {
                    "Leave blank to review all eligible tasks"
                } else {
                    ""
                },
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.bodyLarge, colors = FattoFieldDefaults.filledColors(),
        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewChoiceField(
    label: String,
    selected: String,
    options: List<String>,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
        TextField(
            value = selected, onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            textStyle = MaterialTheme.typography.bodyLarge, colors = FattoFieldDefaults.filledColors(),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            options.distinct().forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    onChange(option)
                    expanded = false
                })
            }
        }
    }
}
