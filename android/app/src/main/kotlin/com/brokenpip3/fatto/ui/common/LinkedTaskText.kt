package com.brokenpip3.fatto.ui.common

import android.content.ActivityNotFoundException
import android.text.SpannableString
import android.text.style.URLSpan
import android.text.util.Linkify
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

internal data class TaskTextLink(val start: Int, val end: Int, val url: String)

internal fun taskTextLinks(text: String): List<TaskTextLink> {
    val linked = SpannableString(text)
    Linkify.addLinks(linked, Linkify.WEB_URLS)
    return linked.getSpans(0, linked.length, URLSpan::class.java)
        .filter { it.url.startsWith("https://", ignoreCase = true) || it.url.startsWith("http://", ignoreCase = true) }
        .map {
            // Android's matcher can include sentence punctuation in the destination.
            val url = it.url.trimEnd('.', ',', ';', ':', '!', '?')
            val end = linked.getSpanEnd(it) - (it.url.length - url.length)
            TaskTextLink(linked.getSpanStart(it), end, url)
        }
        .sortedBy { it.start }
}

/** Keep URL opening failures in the sheet instead of interrupting the task editor. */
@Composable
private fun rememberOpenTaskLink(text: String): Pair<(String) -> Unit, String?> {
    val uriHandler = LocalUriHandler.current
    var error by remember(text) { mutableStateOf<String?>(null) }
    val open: (String) -> Unit = { url ->
        error =
            try {
                uriHandler.openUri(url)
                null
            } catch (_: ActivityNotFoundException) {
                "No app available to open this link."
            } catch (_: IllegalArgumentException) {
                "Could not open this link."
            }
    }
    return open to error
}

@Composable
fun LinkedTaskText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val links = remember(text) { taskTextLinks(text) }
    val (open, error) = rememberOpenTaskLink(text)
    val linkStyle = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
    val annotated =
        buildAnnotatedString {
            var offset = 0
            links.forEach { link ->
                append(text.substring(offset, link.start))
                withLink(LinkAnnotation.Url(link.url, styles = linkStyle, linkInteractionListener = { open(link.url) })) {
                    append(text.substring(link.start, link.end))
                }
                offset = link.end
            }
            append(text.substring(offset))
        }
    Column(modifier) {
        Text(annotated, style = style)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

/** Separate actions preserve cursor placement and selection inside editable text fields. */
@Composable
fun TaskEditorLinks(text: String) {
    val links = remember(text) { taskTextLinks(text).distinctBy { it.url } }
    val (open, error) = rememberOpenTaskLink(text)
    if (links.isNotEmpty()) {
        Column {
            links.forEach { link ->
                TextButton(onClick = { open(link.url) }) {
                    Text(if (links.size == 1) "Open link" else "Open link: ${link.url}")
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
