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
import androidx.glance.color.ColorProvider
import androidx.glance.color.colorProviders
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
import com.brokenpip3.fatto.MainActivity
import com.brokenpip3.fatto.R
import com.brokenpip3.fatto.data.DateTimeUtils
import com.brokenpip3.fatto.data.NextTasksSelector
import com.brokenpip3.fatto.data.SettingsRepositoryImpl
import com.brokenpip3.fatto.data.TaskRepository
import com.brokenpip3.fatto.data.model.Task
import com.brokenpip3.fatto.notification.NotificationNavigation
import com.brokenpip3.fatto.ui.theme.FattoSpacing
import com.brokenpip3.fatto.ui.theme.NordicBlueGrey
import com.brokenpip3.fatto.ui.theme.NordicDarkMoss
import com.brokenpip3.fatto.ui.theme.NordicFrost
import com.brokenpip3.fatto.ui.theme.NordicGrey
import com.brokenpip3.fatto.ui.theme.NordicIce
import com.brokenpip3.fatto.ui.theme.NordicIceBlue
import com.brokenpip3.fatto.ui.theme.NordicMidnight
import com.brokenpip3.fatto.ui.theme.NordicMist
import com.brokenpip3.fatto.ui.theme.NordicMoss
import com.brokenpip3.fatto.ui.theme.NordicNight
import com.brokenpip3.fatto.ui.theme.NordicNightSurface
import com.brokenpip3.fatto.ui.theme.NordicNightSurfaceVariant
import com.brokenpip3.fatto.ui.theme.NordicOnSecondaryContainerDark
import com.brokenpip3.fatto.ui.theme.NordicOnSecondaryContainerLight
import com.brokenpip3.fatto.ui.theme.NordicSecondaryContainerDark
import com.brokenpip3.fatto.ui.theme.NordicSecondaryContainerLight
import com.brokenpip3.fatto.ui.theme.NordicSlate
import com.brokenpip3.fatto.ui.theme.NordicStorm
import com.brokenpip3.fatto.ui.theme.NordicTertiaryDark

private val widgetColors =
    colorProviders(
        primary = ColorProvider(day = NordicSlate, night = NordicIceBlue),
        onPrimary = ColorProvider(day = Color.White, night = NordicNight),
        primaryContainer = ColorProvider(day = NordicIce, night = NordicNightSurfaceVariant),
        onPrimaryContainer = ColorProvider(day = NordicMidnight, night = NordicMist),
        secondary = ColorProvider(day = NordicMoss, night = NordicDarkMoss),
        onSecondary = ColorProvider(day = Color.White, night = NordicNight),
        secondaryContainer = ColorProvider(day = NordicSecondaryContainerLight, night = NordicSecondaryContainerDark),
        onSecondaryContainer = ColorProvider(day = NordicOnSecondaryContainerLight, night = NordicOnSecondaryContainerDark),
        tertiary = ColorProvider(day = NordicStorm, night = NordicTertiaryDark),
        onTertiary = ColorProvider(day = Color.White, night = NordicNight),
        tertiaryContainer = ColorProvider(day = NordicIce, night = Color(0xFF33283D)),
        onTertiaryContainer = ColorProvider(day = NordicMidnight, night = NordicMist),
        error = ColorProvider(day = Color(0xFFBA1A1A), night = Color(0xFFFFB4AB)),
        errorContainer = ColorProvider(day = Color(0xFFFFDAD6), night = Color(0xFF93000A)),
        onError = ColorProvider(day = Color.White, night = Color(0xFF690005)),
        onErrorContainer = ColorProvider(day = Color(0xFF410002), night = Color(0xFFFFDAD6)),
        background = ColorProvider(day = NordicFrost, night = NordicNight),
        onBackground = ColorProvider(day = NordicMidnight, night = NordicMist),
        surface = ColorProvider(day = Color.White, night = NordicNightSurface),
        onSurface = ColorProvider(day = NordicMidnight, night = NordicMist),
        surfaceVariant = ColorProvider(day = NordicIce, night = NordicNightSurfaceVariant),
        onSurfaceVariant = ColorProvider(day = NordicGrey, night = NordicBlueGrey),
        outline = ColorProvider(day = NordicGrey, night = Color(0xFF5D6A75)),
        inverseOnSurface = ColorProvider(day = NordicFrost, night = NordicNight),
        inverseSurface = ColorProvider(day = NordicMidnight, night = NordicMist),
        inversePrimary = ColorProvider(day = NordicIce, night = NordicSlate),
        widgetBackground = ColorProvider(day = Color.White, night = NordicNightSurface),
    )

/** Error-role colors from the light and dark Material 3 palettes. */
private val overdueColor = widgetColors.error

/** Widget title style (Glance 1.2.0-rc01 has no GlanceTheme.typography). */
private val titleStyle =
    TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
    )

/** Metadata style, matching bodySmall while retaining the platform font. */
private val bodySmallStyle =
    TextStyle(
        fontSize = 12.sp,
    )

/** Task and empty-state style, matching bodyMedium while retaining the platform font. */
private val bodyMediumStyle =
    TextStyle(
        fontSize = 14.sp,
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
            GlanceTheme(colors = widgetColors) {
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
        modifier = GlanceModifier.fillMaxSize().background(colors.surface).padding(FattoSpacing.large),
    ) {
        Text(
            text = widgetTitle,
            style = titleStyle.copy(color = colors.onSurface),
            modifier = GlanceModifier.padding(bottom = FattoSpacing.large),
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
        style = bodyMediumStyle.copy(color = GlanceTheme.colors.onSurfaceVariant),
    )
}

@androidx.compose.runtime.Composable
private fun TaskRow(task: Task) {
    // Compact rows preserve the existing 3/5/8-task policy; dense widget sizes may fall below 48dp targets.
    val colors = GlanceTheme.colors
    val dueText = DateTimeUtils.formatLocalDate(task.due).orEmpty()
    val overdue = DateTimeUtils.isOverdue(task.due)

    Row(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .padding(vertical = FattoSpacing.xSmall)
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
                style = bodyMediumStyle.copy(color = colors.onSurface),
                maxLines = 1,
            )
            task.project?.let { project ->
                Text(
                    text = project,
                    style = bodySmallStyle.copy(color = colors.onSurfaceVariant),
                    modifier =
                        GlanceModifier
                            .background(colors.surfaceVariant)
                            .padding(horizontal = FattoSpacing.small, vertical = FattoSpacing.xSmall),
                    maxLines = 1,
                )
            }
        }
        Spacer(GlanceModifier.width(FattoSpacing.small))
        Text(
            text = dueText,
            style = bodySmallStyle.copy(color = if (overdue) overdueColor else colors.onSurfaceVariant),
        )
    }
}
