package com.brokenpip3.fatto.ui.tasklist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.brokenpip3.fatto.ui.theme.toNordicColor

enum class DatePickerType { DUE, WAIT, SCHEDULED }

@Composable
fun DatePickerIconButton(
    label: String,
    date: String?,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint =
                    if (date != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = 0.6f,
                        )
                    },
            )
        }
        Text(
            text = date?.take(10) ?: label,
            style = MaterialTheme.typography.labelSmall,
            color = if (date != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

@Composable
fun SuggestionChip(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun TagChip(
    tag: String,
    onRemove: (() -> Unit)? = null,
) {
    Surface(
        color = tag.toNordicColor().copy(alpha = 0.1f),
        shape = RoundedCornerShape(4.dp),
        modifier =
            if (onRemove != null) {
                Modifier.clickable { onRemove() }
            } else {
                Modifier
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = tag,
                style = MaterialTheme.typography.labelSmall,
                color = tag.toNordicColor(),
                fontWeight = FontWeight.Bold,
            )
            if (onRemove != null) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove $tag",
                    tint = tag.toNordicColor(),
                    modifier = Modifier.padding(start = 4.dp).size(12.dp),
                )
            }
        }
    }
}
