package com.brokenpip3.fatto.ui.tasklist

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
    ) {
        DropdownMenuItem(
            text = { Text("No context") },
            onClick = {
                onContextSelected(null)
                onExpandedChange(false)
            },
            leadingIcon = {
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
                leadingIcon = {
                    if (activeContextId == context.id) {
                        Icon(Icons.Default.Check, contentDescription = null)
                    }
                },
            )
        }
        DropdownMenuItem(
            text = { Text("New context") },
            onClick = {
                onCreateContext()
                onExpandedChange(false)
            },
        )
        DropdownMenuItem(
            text = { Text("Manage contexts") },
            onClick = {
                onManageContexts()
                onExpandedChange(false)
            },
        )
    }
}
