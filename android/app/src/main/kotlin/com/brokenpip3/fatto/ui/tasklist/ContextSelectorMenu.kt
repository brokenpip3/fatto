package com.brokenpip3.fatto.ui.tasklist

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.brokenpip3.fatto.data.model.TaskContext

@Composable
fun ContextSelectorMenu(
    contexts: List<TaskContext>,
    activeContextId: String?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onContextSelected: (String?) -> Unit,
    onCreateContext: () -> Unit,
    onManageContexts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = { onExpandedChange(true) }, modifier = modifier) {
        Icon(Icons.Default.Workspaces, contentDescription = "Contexts")
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { onExpandedChange(false) },
        shape = MaterialTheme.shapes.medium,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        DropdownMenuItem(
            text = { Text("No context") },
            onClick = {
                onContextSelected(null)
                onExpandedChange(false)
            },
            trailingIcon = {
                if (activeContextId == null) {
                    Icon(Icons.Default.Check, contentDescription = null)
                }
            },
        )
        contexts.forEach { context ->
            DropdownMenuItem(
                text = { Text(context.name) },
                onClick = {
                    onContextSelected(context.id)
                    onExpandedChange(false)
                },
                trailingIcon = {
                    if (activeContextId == context.id) {
                        Icon(Icons.Default.Check, contentDescription = null)
                    }
                },
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        DropdownMenuItem(
            text = { Text("New context") },
            onClick = {
                onCreateContext()
                onExpandedChange(false)
            },
            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text("Manage contexts") },
            onClick = {
                onManageContexts()
                onExpandedChange(false)
            },
            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
        )
    }
}
