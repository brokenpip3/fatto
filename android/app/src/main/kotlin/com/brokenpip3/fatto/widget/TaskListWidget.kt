package com.brokenpip3.fatto.widget

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.brokenpip3.fatto.MainActivity
import com.brokenpip3.fatto.R
import com.brokenpip3.fatto.data.DateTimeUtils
import com.brokenpip3.fatto.data.NextTasksSelector
import com.brokenpip3.fatto.data.SettingsRepositoryImpl
import com.brokenpip3.fatto.data.TaskRepository
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.notification.NotificationNavigation

/** Nordic aurora red, used for overdue due dates. */
private val OVERDUE_RED = ColorProvider(Color(0xFFBF616A))

/** Widget title style (Glance 1.2.0-rc01 has no GlanceTheme.typography). */
private val titleStyle =
    TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
    )

/** Project chip style. */
private val labelStyle =
    TextStyle(
        fontSize = 11.sp,
    )

class TaskListWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val result = runCatching { loadTasks(context) }
        if (result.isFailure) {
            Log.e("TaskListWidget", "Failed to load tasks for widget", result.exceptionOrNull())
        }
        provideContent {
            GlanceTheme {
                TaskListWidgetContent(
                    tasks = result.getOrDefault(emptyList()),
                    error = result.isFailure,
                )
            }
        }
    }

    private suspend fun loadTasks(context: Context): List<Task> {
        val settingsRepository = SettingsRepositoryImpl(context)
        val repository = TaskRepository(context, settingsRepository)
        repository.init()
        return NextTasksSelector.nextTasks(repository.tasks.value, MAX_TASKS)
    }

    companion object {
        const val MAX_TASKS = 8
    }
}

@androidx.compose.runtime.Composable
internal fun TaskListWidgetContent(
    tasks: List<Task>,
    error: Boolean,
) {
    val colors = GlanceTheme.colors
    val widgetTitle = LocalContext.current.getString(R.string.widget_name)
    val visibleTasks =
        when {
            LocalSize.current.height >= 260.dp -> tasks
            LocalSize.current.height >= 190.dp -> tasks.take(5)
            else -> tasks.take(3)
        }

    Column(
        modifier = GlanceModifier.fillMaxSize().background(colors.surface).padding(16.dp),
    ) {
        Text(
            text = widgetTitle,
            style = titleStyle.copy(color = colors.onSurface),
            modifier = GlanceModifier.padding(bottom = 8.dp),
        )
        when {
            error -> EmptyState(LocalContext.current.getString(R.string.widget_error_message))
            tasks.isEmpty() -> EmptyState("No pending tasks")
            else -> visibleTasks.forEach { task -> TaskRow(task) }
        }
    }
}

@androidx.compose.runtime.Composable
private fun EmptyState(message: String) {
    Text(
        text = message,
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
    )
}

@androidx.compose.runtime.Composable
private fun TaskRow(task: Task) {
    val colors = GlanceTheme.colors
    val dueText = DateTimeUtils.formatLocalDate(task.due).orEmpty()
    val overdue = DateTimeUtils.isOverdue(task.due)

    Row(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clickable(
                    actionStartActivity<MainActivity>(
                        actionParametersOf(
                            ActionParameters.Key<String>(NotificationNavigation.EXTRA_TASK_UUID) to task.uuid,
                        ),
                    ),
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = task.description,
                style = TextStyle(color = colors.onSurface),
                maxLines = 1,
            )
            task.project?.let { project ->
                Text(
                    text = project,
                    style = labelStyle.copy(color = colors.onSurfaceVariant),
                    modifier =
                        GlanceModifier
                            .background(colors.surfaceVariant)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = dueText,
            style = TextStyle(color = if (overdue) OVERDUE_RED else colors.onSurfaceVariant),
        )
    }
}
