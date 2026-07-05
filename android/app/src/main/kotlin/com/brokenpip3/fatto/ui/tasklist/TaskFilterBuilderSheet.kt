package com.brokenpip3.fatto.ui.tasklist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.brokenpip3.fatto.data.filter.TaskFilterExpressionParser
import com.brokenpip3.fatto.data.model.TaskContext

enum class TaskFilterBuilderPurpose {
    FILTER,
    CONTEXT,
}

private enum class ContextBuilderTab {
    BUILD,
    EXPRESSION,
}

private data class SimpleFilterContentUiState(
    val state: TaskFilterState,
    val projectQuery: String,
    val tagQuery: String,
    val filteredProjects: List<String>,
    val filteredTags: List<String>,
    val projectExpanded: Boolean,
    val tagsExpanded: Boolean,
)

private data class SimpleFilterContentActions(
    val onStateChange: (TaskFilterState) -> Unit,
    val onProjectQueryChange: (String) -> Unit,
    val onTagQueryChange: (String) -> Unit,
    val onProjectExpandedChange: (Boolean) -> Unit,
    val onTagsExpandedChange: (Boolean) -> Unit,
)

private data class ContextBuilderContentUiState(
    val state: TaskFilterState,
    val contextTab: ContextBuilderTab,
    val projectQuery: String,
    val tagQuery: String,
    val availableProjects: List<String>,
    val availableTags: Set<String>,
    val keywordQuery: String,
    val polarity: ContextBuilderPolarity,
    val sections: ContextBuilderSectionState,
)

private data class ContextBuilderContentActions(
    val onStateChange: (TaskFilterState) -> Unit,
    val onContextTabChange: (ContextBuilderTab) -> Unit,
    val onProjectQueryChange: (String) -> Unit,
    val onTagQueryChange: (String) -> Unit,
    val onKeywordQueryChange: (String) -> Unit,
    val onPolarityChange: (ContextBuilderPolarity) -> Unit,
    val onBuildModeErrorChange: (String?) -> Unit,
    val sectionActions: ContextBuilderSectionActions,
)

private data class ContextBuilderSectionState(
    val projectsExpanded: Boolean,
    val tagsExpanded: Boolean,
    val virtualTagsExpanded: Boolean,
)

private data class ContextBuilderSectionActions(
    val onProjectsExpandedChange: (Boolean) -> Unit,
    val onTagsExpandedChange: (Boolean) -> Unit,
    val onVirtualTagsExpandedChange: (Boolean) -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskFilterBuilderSheet(
    initialState: TaskFilterState,
    availableProjects: List<String>,
    availableTags: Set<String>,
    contextName: String,
    purpose: TaskFilterBuilderPurpose = TaskFilterBuilderPurpose.FILTER,
    onDismiss: () -> Unit,
    onApply: ((TaskFilterState) -> Unit)?,
    onSaveContext: (String, TaskFilterState) -> Unit,
) {
    var state by remember(initialState, purpose) {
        mutableStateOf(
            if (purpose == TaskFilterBuilderPurpose.CONTEXT) {
                initialState.withContextBuilderTerms()
            } else {
                initialState
            },
        )
    }
    var projectQuery by remember(initialState.project) { mutableStateOf(initialState.project ?: "") }
    var tagQuery by remember { mutableStateOf("") }
    var projectExpanded by remember { mutableStateOf(false) }
    var tagsExpanded by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var keywordQuery by remember { mutableStateOf("") }
    var buildModeError by remember { mutableStateOf<String?>(null) }
    var contextPolarity by remember { mutableStateOf(ContextBuilderPolarity.INCLUDE) }
    var contextProjectsExpanded by remember { mutableStateOf(false) }
    var contextTagsExpanded by remember { mutableStateOf(false) }
    var contextVirtualTagsExpanded by remember { mutableStateOf(false) }
    var contextTab by remember(initialState, purpose) {
        mutableStateOf(
            if (initialState.rawExpressionText != null) {
                ContextBuilderTab.EXPRESSION
            } else {
                ContextBuilderTab.BUILD
            },
        )
    }
    val scrollState = rememberScrollState()

    val filteredProjects =
        remember(projectQuery, availableProjects, state.project) {
            if (projectQuery.isBlank()) {
                availableProjects.sorted().take(12)
            } else {
                availableProjects
                    .filter { it.contains(projectQuery, ignoreCase = true) && it != state.project }
                    .sorted()
                    .take(12)
            }
        }
    val filteredTags =
        remember(tagQuery, availableTags, state.tags) {
            if (tagQuery.isBlank()) {
                emptyList()
            } else {
                availableTags
                    .filter { it.contains(tagQuery, ignoreCase = true) && !state.tags.contains(it) }
                    .sorted()
                    .take(12)
            }
        }
    val expressionError =
        remember(state.rawExpressionText, contextTab) {
            if (purpose == TaskFilterBuilderPurpose.CONTEXT && contextTab == ContextBuilderTab.EXPRESSION) {
                TaskFilterExpressionParser.parse(state.rawExpressionText.orEmpty()).exceptionOrNull()?.message
            } else {
                null
            }
        }
    val actionState =
        if (purpose == TaskFilterBuilderPurpose.CONTEXT && contextTab == ContextBuilderTab.BUILD) {
            state.withPendingKeywordTerm(keywordQuery, contextPolarity)
        } else {
            state
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (purpose == TaskFilterBuilderPurpose.FILTER) {
                    "Filters"
                } else {
                    "Context"
                },
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(scrollState),
            ) {
                if (purpose == TaskFilterBuilderPurpose.FILTER) {
                    SimpleFilterContent(
                        uiState =
                            SimpleFilterContentUiState(
                                state = state,
                                projectQuery = projectQuery,
                                tagQuery = tagQuery,
                                filteredProjects = filteredProjects,
                                filteredTags = filteredTags,
                                projectExpanded = projectExpanded,
                                tagsExpanded = tagsExpanded,
                            ),
                        actions =
                            SimpleFilterContentActions(
                                onStateChange = { state = it },
                                onProjectQueryChange = { projectQuery = it },
                                onTagQueryChange = { tagQuery = it },
                                onProjectExpandedChange = { projectExpanded = it },
                                onTagsExpandedChange = { tagsExpanded = it },
                            ),
                    )
                } else {
                    ContextBuilderContent(
                        uiState =
                            ContextBuilderContentUiState(
                                state = state,
                                contextTab = contextTab,
                                projectQuery = projectQuery,
                                tagQuery = tagQuery,
                                availableProjects = availableProjects,
                                availableTags = availableTags,
                                keywordQuery = keywordQuery,
                                polarity = contextPolarity,
                                sections =
                                    ContextBuilderSectionState(
                                        projectsExpanded = contextProjectsExpanded,
                                        tagsExpanded = contextTagsExpanded,
                                        virtualTagsExpanded = contextVirtualTagsExpanded,
                                    ),
                            ),
                        actions =
                            ContextBuilderContentActions(
                                onStateChange = { state = it },
                                onContextTabChange = { selectedTab ->
                                    buildModeError = null
                                    when (selectedTab) {
                                        ContextBuilderTab.BUILD ->
                                            if (contextTab != ContextBuilderTab.BUILD) {
                                                val parsed =
                                                    TaskFilterState.fromContext(
                                                        TaskContext(
                                                            id = "preview",
                                                            name = contextName,
                                                            expressionText = state.rawExpressionText.orEmpty(),
                                                        ),
                                                    )
                                                if (parsed.rawExpressionText == null) {
                                                    state = parsed
                                                    contextTab = ContextBuilderTab.BUILD
                                                } else {
                                                    buildModeError = "This expression needs text editing."
                                                }
                                            }
                                        ContextBuilderTab.EXPRESSION ->
                                            if (contextTab != ContextBuilderTab.EXPRESSION) {
                                                state = state.copy(rawExpressionText = state.expressionText())
                                                contextTab = ContextBuilderTab.EXPRESSION
                                            }
                                    }
                                },
                                onProjectQueryChange = { projectQuery = it },
                                onTagQueryChange = { tagQuery = it },
                                onKeywordQueryChange = { keywordQuery = it },
                                onPolarityChange = { contextPolarity = it },
                                onBuildModeErrorChange = { buildModeError = it },
                                sectionActions =
                                    ContextBuilderSectionActions(
                                        onProjectsExpandedChange = { contextProjectsExpanded = it },
                                        onTagsExpandedChange = { contextTagsExpanded = it },
                                        onVirtualTagsExpandedChange = { contextVirtualTagsExpanded = it },
                                    ),
                            ),
                    )
                }

                if (purpose == TaskFilterBuilderPurpose.CONTEXT) {
                    buildModeError?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 8.dp),
            ) {
                TextButton(
                    onClick = {
                        state = TaskFilterState()
                        contextTab = ContextBuilderTab.BUILD
                        projectQuery = ""
                        tagQuery = ""
                        keywordQuery = ""
                        buildModeError = null
                        contextPolarity = ContextBuilderPolarity.INCLUDE
                        contextProjectsExpanded = false
                        contextTagsExpanded = false
                        contextVirtualTagsExpanded = false
                    },
                ) {
                    Text("Clear")
                }
                if (purpose == TaskFilterBuilderPurpose.FILTER) {
                    TextButton(onClick = { showNameDialog = true }) {
                        Text("Save")
                    }
                    Button(
                        onClick = { onApply?.invoke(state) },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Apply")
                    }
                } else if (contextName.isBlank()) {
                    Button(
                        onClick = { showNameDialog = true },
                        enabled = expressionError == null,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Save")
                    }
                } else if (onApply != null) {
                    Button(
                        onClick = { onApply.invoke(actionState) },
                        enabled = expressionError == null,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Update")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )

    if (showNameDialog) {
        SaveContextDialog(
            initialName = contextName,
            filterState = actionState,
            onDismiss = { showNameDialog = false },
            onSave = { name ->
                onSaveContext(name, actionState)
                showNameDialog = false
            },
        )
    }
}

@Composable
private fun SimpleFilterContent(
    uiState: SimpleFilterContentUiState,
    actions: SimpleFilterContentActions,
) {
    val state = uiState.state
    val projectQuery = uiState.projectQuery
    val tagQuery = uiState.tagQuery
    val filteredProjects = uiState.filteredProjects
    val filteredTags = uiState.filteredTags
    val projectExpanded = uiState.projectExpanded
    val tagsExpanded = uiState.tagsExpanded
    val onStateChange = actions.onStateChange
    val onProjectQueryChange = actions.onProjectQueryChange
    val onTagQueryChange = actions.onTagQueryChange
    val onProjectExpandedChange = actions.onProjectExpandedChange
    val onTagsExpandedChange = actions.onTagsExpandedChange

    OutlinedTextField(
        value = state.descriptionQuery,
        onValueChange = { onStateChange(state.copy(descriptionQuery = it)) },
        label = { Text("Search") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    AccordionSection(
        title = "Project",
        icon = Icons.Default.AccountTree,
        count = if (state.project == null) null else 1,
        expanded = projectExpanded,
        onToggle = { onProjectExpandedChange(!projectExpanded) },
    ) {
        OutlinedTextField(
            value = projectQuery,
            onValueChange = onProjectQueryChange,
            label = { Text("Find project") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            SuggestionChip(
                label = "Any project",
                onClick = {
                    onStateChange(state.copy(project = null))
                    onProjectQueryChange("")
                },
            )
            filteredProjects.forEach { project ->
                SuggestionChip(
                    label = project,
                    onClick = {
                        onStateChange(state.copy(project = project))
                        onProjectQueryChange(project)
                    },
                )
            }
        }
    }

    AccordionSection(
        title = "Tags",
        icon = Icons.Default.Tag,
        count = state.tags.size.takeIf { it > 0 },
        expanded = tagsExpanded,
        onToggle = { onTagsExpandedChange(!tagsExpanded) },
    ) {
        if (state.tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                state.tags.sorted().forEach { tag ->
                    TagChip(tag = tag, onRemove = { onStateChange(state.copy(tags = state.tags - tag)) })
                }
            }
        }
        OutlinedTextField(
            value = tagQuery,
            onValueChange = onTagQueryChange,
            label = { Text("Add tag") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                TextButton(
                    onClick = {
                        val tag = tagQuery.trim()
                        if (tag.isNotBlank()) {
                            onStateChange(state.copy(tags = state.tags + tag))
                            onTagQueryChange("")
                        }
                    },
                ) {
                    Text("Add")
                }
            },
        )
        if (filteredTags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                filteredTags.forEach { tag ->
                    SuggestionChip(
                        label = tag,
                        onClick = {
                            onStateChange(state.copy(tags = state.tags + tag))
                            onTagQueryChange("")
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ContextBuilderContent(
    uiState: ContextBuilderContentUiState,
    actions: ContextBuilderContentActions,
) {
    val state = uiState.state
    val contextTab = uiState.contextTab
    val projectQuery = uiState.projectQuery
    val tagQuery = uiState.tagQuery
    val availableProjects = uiState.availableProjects
    val availableTags = uiState.availableTags
    val keywordQuery = uiState.keywordQuery
    val polarity = uiState.polarity
    val sections = uiState.sections
    val onStateChange = actions.onStateChange
    val onContextTabChange = actions.onContextTabChange
    val onProjectQueryChange = actions.onProjectQueryChange
    val onTagQueryChange = actions.onTagQueryChange
    val onKeywordQueryChange = actions.onKeywordQueryChange
    val onPolarityChange = actions.onPolarityChange
    val onBuildModeErrorChange = actions.onBuildModeErrorChange
    val sectionActions = actions.sectionActions

    val filteredProjects =
        remember(projectQuery, availableProjects, state.builderTerms) {
            if (projectQuery.isBlank()) {
                availableProjects
                    .filterNot { candidate ->
                        state.builderTerms.any {
                            it.kind == ContextBuilderTermKind.PROJECT && it.value.equals(candidate, ignoreCase = true)
                        }
                    }
                    .sorted()
                    .take(12)
            } else {
                availableProjects
                    .filter {
                        it.contains(projectQuery, ignoreCase = true) &&
                            !state.builderTerms.any { term ->
                                term.kind == ContextBuilderTermKind.PROJECT && term.value.equals(it, ignoreCase = true)
                            }
                    }
                    .sorted()
                    .take(12)
            }
        }
    val filteredTags =
        remember(tagQuery, availableTags, state.builderTerms) {
            if (tagQuery.isBlank()) {
                emptyList()
            } else {
                availableTags
                    .filter {
                        it.contains(tagQuery, ignoreCase = true) &&
                            !state.builderTerms.any { term ->
                                term.kind == ContextBuilderTermKind.TAG && term.value.equals(it, ignoreCase = true)
                            }
                    }
                    .sorted()
                    .take(12)
            }
        }
    val virtualTags = listOf("PENDING", "COMPLETED", "DUE", "WAITING", "ACTIVE", "BLOCKING", "BLOCKED")
    val includeTerms = state.builderTerms.filter { it.polarity == ContextBuilderPolarity.INCLUDE }
    val excludeTerms = state.builderTerms.filter { it.polarity == ContextBuilderPolarity.EXCLUDE }
    val projectTermCount = state.builderTerms.count { it.kind == ContextBuilderTermKind.PROJECT }
    val tagTermCount = state.builderTerms.count { it.kind == ContextBuilderTermKind.TAG }
    val virtualTagTermCount = state.builderTerms.count { it.kind == ContextBuilderTermKind.VIRTUAL_TAG }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (contextTab == ContextBuilderTab.BUILD) {
                Button(
                    onClick = { onContextTabChange(ContextBuilderTab.BUILD) },
                    modifier = Modifier.testTag("ContextBuildTab"),
                ) {
                    Text("Build")
                }
                TextButton(
                    onClick = { onContextTabChange(ContextBuilderTab.EXPRESSION) },
                    modifier = Modifier.testTag("ContextExpressionTab"),
                ) {
                    Text("Expression")
                }
            } else {
                TextButton(
                    onClick = { onContextTabChange(ContextBuilderTab.BUILD) },
                    modifier = Modifier.testTag("ContextBuildTab"),
                ) {
                    Text("Build")
                }
                Button(
                    onClick = { onContextTabChange(ContextBuilderTab.EXPRESSION) },
                    modifier = Modifier.testTag("ContextExpressionTab"),
                ) {
                    Text("Expression")
                }
            }
        }

        if (contextTab == ContextBuilderTab.BUILD) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (polarity == ContextBuilderPolarity.INCLUDE) {
                    Button(onClick = { onPolarityChange(ContextBuilderPolarity.INCLUDE) }) { Text("+") }
                    TextButton(onClick = { onPolarityChange(ContextBuilderPolarity.EXCLUDE) }) { Text("-") }
                } else {
                    TextButton(onClick = { onPolarityChange(ContextBuilderPolarity.INCLUDE) }) { Text("+") }
                    Button(onClick = { onPolarityChange(ContextBuilderPolarity.EXCLUDE) }) { Text("-") }
                }
            }

            OutlinedTextField(
                value = keywordQuery,
                onValueChange = onKeywordQueryChange,
                label = { Text("Search") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(
                        onClick = {
                            val keyword = keywordQuery.trim()
                            if (keyword.isNotBlank()) {
                                onStateChange(
                                    state.addBuilderTerm(
                                        ContextBuilderTerm(ContextBuilderTermKind.KEYWORD, keyword, polarity),
                                    ),
                                )
                                onKeywordQueryChange("")
                            }
                        },
                    ) {
                        Text("Add")
                    }
                },
            )

            AccordionSection(
                title = "Projects",
                icon = Icons.Default.AccountTree,
                count = projectTermCount.takeIf { it > 0 },
                expanded = sections.projectsExpanded,
                onToggle = { sectionActions.onProjectsExpandedChange(!sections.projectsExpanded) },
            ) {
                OutlinedTextField(
                    value = projectQuery,
                    onValueChange = onProjectQueryChange,
                    label = { Text("Find project") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    filteredProjects.forEach { project ->
                        SuggestionChip(
                            label = project,
                            onClick = {
                                onStateChange(
                                    state.addBuilderTerm(
                                        ContextBuilderTerm(ContextBuilderTermKind.PROJECT, project, polarity),
                                    ),
                                )
                                onProjectQueryChange("")
                            },
                        )
                    }
                }
            }

            AccordionSection(
                title = "Tags",
                icon = Icons.Default.Tag,
                count = tagTermCount.takeIf { it > 0 },
                expanded = sections.tagsExpanded,
                onToggle = { sectionActions.onTagsExpandedChange(!sections.tagsExpanded) },
            ) {
                OutlinedTextField(
                    value = tagQuery,
                    onValueChange = onTagQueryChange,
                    label = { Text("Find tag") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    filteredTags.forEach { tag ->
                        SuggestionChip(
                            label = tag,
                            onClick = {
                                onStateChange(
                                    state.addBuilderTerm(
                                        ContextBuilderTerm(ContextBuilderTermKind.TAG, tag, polarity),
                                    ),
                                )
                                onTagQueryChange("")
                            },
                        )
                    }
                }
            }

            AccordionSection(
                title = "Virtual tags",
                icon = Icons.Default.Tag,
                count = virtualTagTermCount.takeIf { it > 0 },
                expanded = sections.virtualTagsExpanded,
                onToggle = { sectionActions.onVirtualTagsExpandedChange(!sections.virtualTagsExpanded) },
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    virtualTags.forEach { tag ->
                        SuggestionChip(
                            label = tag,
                            onClick = {
                                onStateChange(
                                    state.addBuilderTerm(
                                        ContextBuilderTerm(ContextBuilderTermKind.VIRTUAL_TAG, tag, polarity),
                                    ),
                                )
                            },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (includeTerms.isNotEmpty()) {
                    Text(
                        text = "Include",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        includeTerms.forEach { term ->
                            TagChip(
                                tag = term.value,
                                onRemove = { onStateChange(state.removeBuilderTerm(term)) },
                            )
                        }
                    }
                }
                if (excludeTerms.isNotEmpty()) {
                    Text(
                        text = "Exclude",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        excludeTerms.forEach { term ->
                            TagChip(
                                tag = term.value,
                                onRemove = { onStateChange(state.removeBuilderTerm(term)) },
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = state.expressionText(),
                onValueChange = {},
                readOnly = true,
                label = { Text("Expression") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            OutlinedTextField(
                value = state.rawExpressionText.orEmpty(),
                onValueChange = {
                    onBuildModeErrorChange(null)
                    onStateChange(TaskFilterState(rawExpressionText = it))
                },
                label = { Text("Expression") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth().testTag("ContextExpressionInput"),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("+", "-", "(", ")", "or", "project:").forEach { token ->
                    TextButton(
                        onClick = {
                            onBuildModeErrorChange(null)
                            onStateChange(TaskFilterState(rawExpressionText = appendExpressionToken(state.rawExpressionText, token)))
                        },
                    ) {
                        Text(token)
                    }
                }
            }
            TaskFilterExpressionParser.parse(state.rawExpressionText.orEmpty())
                .exceptionOrNull()
                ?.message
                ?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
        }
    }
}

@Composable
private fun SaveContextDialog(
    initialName: String,
    filterState: TaskFilterState,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(initialName, filterState) {
        mutableStateOf(initialName.ifBlank { filterState.defaultContextName() })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save context") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Context name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim()) },
                enabled = name.isNotBlank(),
                modifier =
                    Modifier.semantics {
                        contentDescription = "Confirm save context"
                    },
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun TaskFilterState.defaultContextName(): String {
    return when {
        project != null -> "Project $project"
        tags.isNotEmpty() -> "Tags ${tags.sorted().joinToString(", ")}"
        descriptionQuery.isNotBlank() -> descriptionQuery.trim().take(32)
        else -> "Context"
    }
}

private fun TaskFilterState.withContextBuilderTerms(): TaskFilterState {
    if (rawExpressionText != null || builderTerms.isNotEmpty()) return this
    val terms =
        buildList {
            if (descriptionQuery.isNotBlank()) {
                add(ContextBuilderTerm(ContextBuilderTermKind.KEYWORD, descriptionQuery))
            }
            project?.trim()?.takeIf { it.isNotBlank() }?.let {
                add(ContextBuilderTerm(ContextBuilderTermKind.PROJECT, it))
            }
            tags.sorted().forEach {
                add(ContextBuilderTerm(ContextBuilderTermKind.TAG, it))
            }
        }
    return copy(builderTerms = terms)
}

private fun TaskFilterState.addBuilderTerm(term: ContextBuilderTerm): TaskFilterState =
    copy(
        rawExpressionText = null,
        builderTerms =
            builderTerms
                .filterNot { it.kind == term.kind && it.value.equals(term.value, ignoreCase = true) }
                .plus(term),
    )

private fun TaskFilterState.withPendingKeywordTerm(
    keywordQuery: String,
    polarity: ContextBuilderPolarity,
): TaskFilterState {
    val keyword = keywordQuery.trim()
    if (keyword.isBlank() || rawExpressionText != null) return this
    return addBuilderTerm(ContextBuilderTerm(ContextBuilderTermKind.KEYWORD, keyword, polarity))
}

private fun TaskFilterState.removeBuilderTerm(term: ContextBuilderTerm): TaskFilterState =
    copy(rawExpressionText = null, builderTerms = builderTerms - term)

private fun appendExpressionToken(
    expressionText: String?,
    token: String,
): String {
    val trimmedToken = token.trim()
    if (trimmedToken.isBlank()) return expressionText.orEmpty()
    val base = expressionText.orEmpty().trimEnd()
    return if (base.isBlank()) {
        trimmedToken
    } else {
        "$base $trimmedToken"
    }
}
