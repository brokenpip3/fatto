package com.brokenpip3.fatto.ui.common

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.flowWithLifecycle
import com.brokenpip3.fatto.data.TaskDateTime
import com.brokenpip3.fatto.data.TaskDateValue
import java.time.Instant
import java.time.ZoneId

val LocalTaskTimeZone = compositionLocalOf { ZoneId.systemDefault() }
val LocalTaskNow = compositionLocalOf { Instant.now() }

@Composable
fun formatTaskDate(value: TaskDateValue): String? =
    value.timestamp?.let {
        TaskDateTime.format(
            value, LocalTaskTimeZone.current, DateFormat.is24HourFormat(LocalContext.current),
            LocalConfiguration.current.locales[0],
        ) ?: "Imported date"
    }

@Composable
fun readableTaskDate(value: TaskDateValue): String = formatTaskDate(value) ?: "No date"

/** Background UI collectors retain the last snapshot without keeping the foreground ticker alive. */
fun com.brokenpip3.fatto.data.TaskTimeSource.forLifecycle(
    lifecycle: androidx.lifecycle.Lifecycle,
): com.brokenpip3.fatto.data.TaskTimeSource {
    val source = this
    return object : com.brokenpip3.fatto.data.TaskTimeSource {
        override val snapshots = source.snapshots.flowWithLifecycle(lifecycle, androidx.lifecycle.Lifecycle.State.STARTED)

        override fun refresh() = source.refresh()
    }
}
