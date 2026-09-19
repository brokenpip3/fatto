package com.brokenpip3.fatto

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.brokenpip3.fatto.data.SettingsRepositoryImpl
import com.brokenpip3.fatto.data.TaskrcImporter
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import java.io.File

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DefaultProjectIntegrationTest {
    @get:Rule
    val clearAppStateRule: ExternalResource =
        object : ExternalResource() {
            override fun before() {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                File(context.filesDir, "taskchampion").deleteRecursively()
                context.getSharedPreferences("sync_settings", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit()
            }
        }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    @Test
    fun importedDefaultProjectPrefillsNewTaskAndActiveProjectWins() {
        val suffix = System.currentTimeMillis()
        val defaultProject = "Inbox$suffix"
        val activeProject = "Active$suffix"
        val defaultTask = "Imported default task $suffix"
        val activeTask = "Active project source $suffix"
        val repository = SettingsRepositoryImpl(composeTestRule.activity.applicationContext)

        try {
            repository.applyTaskrcImport(
                TaskrcImporter.preview(
                    text = "default.project=$defaultProject",
                    existingContexts = repository.getTaskContexts(),
                    currentActiveContextId = repository.getActiveTaskContextId(),
                    currentFirstDayOfWeek = repository.getFirstDayOfWeek(),
                    currentDefaultProjectEnabled = repository.getDefaultProjectEnabled(),
                    currentDefaultProject = repository.getDefaultProject(),
                    currentSyncCredentials = repository.getCredentials(),
                    currentS3Credentials = repository.getS3Credentials(),
                    currentSyncType = repository.getSyncType(),
                ),
            )

            composeTestRule.activityRule.scenario.recreate()
            composeTestRule.waitUntilAtLeastOneExists(hasTestTag("TaskList"), 15_000)

            composeTestRule.onNodeWithContentDescription("Add Task").performClick()
            composeTestRule.onNodeWithTag("ProjectInput").assertTextContains(defaultProject)
            composeTestRule.onNodeWithTag("ProjectInput").performTextClearance()
            composeTestRule.onNodeWithTag("DescriptionInput").performTextInput(defaultTask)
            composeTestRule.onNodeWithText("Create").performClick()
            composeTestRule.waitUntilDoesNotExist(hasTestTag("AddTaskDialog"), 15_000)
            composeTestRule.waitUntilAtLeastOneExists(taskListText(defaultTask), 15_000)

            composeTestRule.onNode(taskListText(defaultTask)).performClick()
            composeTestRule.waitUntilAtLeastOneExists(hasTestTag("TaskDetailBottomSheet"), 15_000)
            composeTestRule.onNode(
                hasText(defaultProject) and hasAnyAncestor(hasTestTag("TaskDetailBottomSheet")),
            ).assertExists()
            dismissBottomSheet()

            composeTestRule.onNodeWithContentDescription("Add Task").performClick()
            composeTestRule.onNodeWithTag("ProjectInput").performTextClearance()
            composeTestRule.onNodeWithTag("ProjectInput").performTextInput(activeProject)
            composeTestRule.onNodeWithTag("DescriptionInput").performTextInput(activeTask)
            composeTestRule.onNodeWithText("Create").performClick()
            composeTestRule.waitUntilDoesNotExist(hasTestTag("AddTaskDialog"), 15_000)
            composeTestRule.waitUntilAtLeastOneExists(taskListText(activeTask), 15_000)

            composeTestRule.onNodeWithText("Projects").performClick()
            composeTestRule.waitUntilAtLeastOneExists(hasText(activeProject), 15_000)
            composeTestRule.onNodeWithText(activeProject).performClick()
            composeTestRule.waitUntilAtLeastOneExists(taskListText(activeTask), 15_000)

            composeTestRule.onNodeWithContentDescription("Add Task").performClick()
            composeTestRule.onNodeWithTag("ProjectInput").assertTextContains(activeProject)
            composeTestRule.onNodeWithText("Cancel").performClick()
        } finally {
            resetPreferences(repository)
        }
    }

    @Test
    fun editingProjectlessTaskDoesNotApplyEnabledDefaultProject() {
        val suffix = System.currentTimeMillis()
        val defaultProject = "DefaultAfterCreation$suffix"
        val originalDescription = "Projectless before default $suffix"
        val editedDescription = "Projectless after edit $suffix"
        val defaultProjectSource = "Default project source $suffix"
        val repository = SettingsRepositoryImpl(composeTestRule.activity.applicationContext)

        try {
            createTask(originalDescription)
            createTask(defaultProjectSource, defaultProject)

            composeTestRule.onNodeWithText("Settings").performClick()
            composeTestRule.onNodeWithTag("SettingsTabTaskrc").performScrollTo().performClick()
            composeTestRule.onNodeWithTag("DefaultProjectToggle").performScrollTo().performClick()
            composeTestRule.waitUntilAtLeastOneExists(hasTestTag("ProjectPickerOption-$defaultProject"), 15_000)
            composeTestRule.onNodeWithTag("ProjectPickerOption-$defaultProject").performClick()
            composeTestRule.onNodeWithTag("ProjectPickerConfirmButton").performClick()
            composeTestRule.onNodeWithText("Tasks").performClick()

            composeTestRule.waitUntilAtLeastOneExists(taskListText(originalDescription), 15_000)
            composeTestRule.onNode(taskListText(originalDescription)).performClick()
            composeTestRule.waitUntilAtLeastOneExists(hasTestTag("TaskDetailBottomSheet"), 15_000)
            composeTestRule.onNode(
                hasText(defaultProject) and hasAnyAncestor(hasTestTag("TaskDetailBottomSheet")),
            ).assertDoesNotExist()
            composeTestRule.onNodeWithContentDescription("TaskDescriptionInput", useUnmergedTree = true)
                .performScrollTo()
                .performTextReplacement(editedDescription)
            dismissBottomSheet()

            composeTestRule.waitUntilAtLeastOneExists(taskListText(editedDescription), 15_000)
            composeTestRule.onNode(taskListText(editedDescription)).performClick()
            composeTestRule.waitUntilAtLeastOneExists(hasTestTag("TaskDetailBottomSheet"), 15_000)
            composeTestRule.onNode(
                hasText(defaultProject) and hasAnyAncestor(hasTestTag("TaskDetailBottomSheet")),
            ).assertDoesNotExist()
            dismissBottomSheet()
        } finally {
            resetPreferences(repository)
        }
    }

    @Test
    fun projectPickerShowsCompletedOnlyProjectsWhenRequested() {
        val suffix = System.currentTimeMillis()
        val pendingProject = "PendingProject$suffix"
        val completedProject = "CompletedProject$suffix"
        val pendingTask = "Pending project task $suffix"
        val completedTask = "Completed project task $suffix"
        val repository = SettingsRepositoryImpl(composeTestRule.activity.applicationContext)

        try {
            createTask(pendingTask, pendingProject)
            createTask(completedTask, completedProject)
            composeTestRule.onNode(
                hasContentDescription("Complete") and hasAnyAncestor(hasText(completedTask)),
            ).performClick()
            composeTestRule.waitUntilAtLeastOneExists(hasText("Confirm"), 15_000)
            composeTestRule.onNodeWithText("Confirm").performClick()
            composeTestRule.waitUntilDoesNotExist(taskListText(completedTask), 15_000)

            composeTestRule.onNodeWithContentDescription("Add Task").performClick()
            composeTestRule.onNodeWithTag("SelectProjectButton").performClick()
            composeTestRule.waitUntilAtLeastOneExists(hasTestTag("ProjectPickerOption-$pendingProject"), 15_000)
            composeTestRule.onNodeWithTag("ProjectPickerOption-$completedProject").assertDoesNotExist()

            composeTestRule.onNodeWithTag("ProjectPickerCancelButton").performClick()
            composeTestRule.onNodeWithText("Cancel").performClick()
            enableDisplayOption("Show empty projects")

            composeTestRule.onNodeWithContentDescription("Add Task").performClick()
            composeTestRule.onNodeWithTag("SelectProjectButton").performClick()
            composeTestRule.waitUntilAtLeastOneExists(hasTestTag("ProjectPickerOption-$completedProject"), 15_000)
            composeTestRule.onNodeWithTag("ProjectPickerCancelButton").performClick()
            composeTestRule.onNodeWithText("Cancel").performClick()
        } finally {
            resetPreferences(repository)
        }
    }

    @Test
    fun tagPickerShowsInternalTagsOnlyWhenEnabledAndEditingPreservesThem() {
        val suffix = System.currentTimeMillis()
        val description = "Internal tag task $suffix"
        val visibleTag = "visible$suffix"
        val repository = SettingsRepositoryImpl(composeTestRule.activity.applicationContext)

        try {
            createTask(description)
            composeTestRule.onNode(taskListText(description)).performClick()
            composeTestRule.waitUntilAtLeastOneExists(hasContentDescription("Start"), 15_000)
            composeTestRule.onNodeWithContentDescription("Start", useUnmergedTree = true)
                .performScrollTo()
                .performClick()
            composeTestRule.onNodeWithContentDescription("TagInput", useUnmergedTree = true)
                .performScrollTo()
                .performTextInput(visibleTag)
            composeTestRule.onNodeWithContentDescription("AddTagButton", useUnmergedTree = true)
                .performClick()
            composeTestRule.waitUntilAtLeastOneExists(
                hasText(visibleTag) and hasAnyAncestor(hasTestTag("TaskDetailBottomSheet")),
                15_000,
            )
            dismissBottomSheet()
            repository.setShowInternalTags(false)

            composeTestRule.onNode(taskListText(description)).performClick()
            composeTestRule.waitUntilAtLeastOneExists(hasTestTag("TaskDetailBottomSheet"), 15_000)
            composeTestRule.onNodeWithTag("SelectTagsButton").performScrollTo().performClick()
            composeTestRule.onNodeWithTag("TagPickerOption-ACTIVE").assertDoesNotExist()
            composeTestRule.onNodeWithTag("TagPickerOption-$visibleTag").performClick()
            composeTestRule.onNodeWithTag("TagPickerConfirmButton").performClick()
            dismissBottomSheet()

            enableDisplayOption("Show internal tags")
            composeTestRule.onNode(taskListText(description)).performClick()
            composeTestRule.waitUntilAtLeastOneExists(hasTestTag("TaskDetailBottomSheet"), 15_000)
            composeTestRule.onNode(
                hasText("ACTIVE") and hasAnyAncestor(hasTestTag("TaskDetailBottomSheet")),
            ).assertExists()
            composeTestRule.onNodeWithTag("SelectTagsButton").performScrollTo().performClick()
            composeTestRule.waitUntilAtLeastOneExists(hasTestTag("TagPickerOption-ACTIVE"), 15_000)
            composeTestRule.onNodeWithText("Cancel").performClick()
            dismissBottomSheet()

            composeTestRule.waitUntilAtLeastOneExists(
                hasText("ACTIVE") and hasAnyAncestor(hasTestTag("TaskList")),
                15_000,
            )
        } finally {
            resetPreferences(repository)
        }
    }

    private fun createTask(
        description: String,
        project: String? = null,
    ) {
        composeTestRule.onNodeWithContentDescription("Add Task").performClick()
        composeTestRule.onNodeWithTag("DescriptionInput").performTextInput(description)
        project?.let {
            composeTestRule.onNodeWithTag("ProjectInput").performTextInput(it)
        }
        composeTestRule.onNodeWithText("Create").performClick()
        composeTestRule.waitUntilDoesNotExist(hasTestTag("AddTaskDialog"), 15_000)
        composeTestRule.waitUntilAtLeastOneExists(taskListText(description), 15_000)
    }

    private fun dismissBottomSheet() {
        composeTestRule.onNodeWithContentDescription("CloseButton", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        composeTestRule.waitUntilDoesNotExist(hasTestTag("TaskDetailBottomSheet"), 15_000)
    }

    private fun taskListText(text: String) = hasText(text) and hasAnyAncestor(hasTestTag("TaskList"))

    private fun enableDisplayOption(label: String) {
        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.onNodeWithTag("SettingsTabDisplay").performScrollTo().performClick()
        composeTestRule.onNodeWithText(label).performScrollTo().performClick()
        composeTestRule.onNodeWithText("Tasks").performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("TaskList"), 15_000)
    }

    private fun resetPreferences(repository: SettingsRepositoryImpl) {
        repository.setDefaultProjectEnabled(false)
        repository.setDefaultProject(null)
        repository.setShowEmptyProjects(false)
        repository.setShowInternalTags(false)
    }
}
