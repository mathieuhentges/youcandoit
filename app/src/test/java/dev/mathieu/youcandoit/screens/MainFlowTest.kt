package dev.mathieu.youcandoit.screens

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import dev.mathieu.youcandoit.domain.fakes.SequentialIds
import dev.mathieu.youcandoit.domain.fakes.TestEnv
import dev.mathieu.youcandoit.domain.model.TaskStatus
import dev.mathieu.youcandoit.domain.usecase.AddTask
import dev.mathieu.youcandoit.domain.usecase.ArchiveDoneTasks
import dev.mathieu.youcandoit.domain.usecase.ArchiveTask
import dev.mathieu.youcandoit.domain.usecase.CreateCategory
import dev.mathieu.youcandoit.domain.usecase.DeleteTask
import dev.mathieu.youcandoit.domain.usecase.GetTask
import dev.mathieu.youcandoit.domain.usecase.MarkTaskDone
import dev.mathieu.youcandoit.domain.usecase.MarkTaskNotDone
import dev.mathieu.youcandoit.domain.usecase.ObserveArchivedTasks
import dev.mathieu.youcandoit.domain.usecase.ObserveCategories
import dev.mathieu.youcandoit.domain.usecase.ObserveProgressStats
import dev.mathieu.youcandoit.domain.usecase.ObserveTaskList
import dev.mathieu.youcandoit.domain.usecase.RestoreTask
import dev.mathieu.youcandoit.domain.usecase.UpdateTask
import dev.mathieu.youcandoit.ui.FakeTicker
import dev.mathieu.youcandoit.ui.editor.TaskEditorContent
import dev.mathieu.youcandoit.ui.editor.TaskEditorViewModel
import dev.mathieu.youcandoit.ui.history.HistoryContent
import dev.mathieu.youcandoit.ui.history.HistoryViewModel
import dev.mathieu.youcandoit.ui.tasks.TaskListContent
import dev.mathieu.youcandoit.ui.tasks.TaskListViewModel
import dev.mathieu.youcandoit.ui.theme.TodoTheme
import java.time.ZoneId
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The main journey through real screens, ViewModels and use cases (in-memory repositories):
 * add a task → tick it → archive it → find it in History.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MainFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private enum class Screen { EDITOR, HOME, HISTORY }

    private val env = TestEnv()

    private val editor = TaskEditorViewModel(
        savedStateHandle = SavedStateHandle(),
        observeCategories = ObserveCategories(env.categories),
        getTask = GetTask(env.tasks),
        addTask = AddTask(env.tasks, env.categories, env.reminders, env.clock, SequentialIds("task")),
        updateTask = UpdateTask(env.tasks, env.categories, env.reminders, env.clock),
        deleteTask = DeleteTask(env.tasks, env.reminders),
        createCategory = CreateCategory(env.categories, env.clock, SequentialIds("cat")),
        clock = env.clock,
        zone = ZoneId.of("America/Montreal"),
    )
    private val home = TaskListViewModel(
        savedStateHandle = SavedStateHandle(),
        observeTaskList = ObserveTaskList(env.tasks),
        observeCategories = ObserveCategories(env.categories),
        ticker = FakeTicker(env.clock),
        markTaskDone = MarkTaskDone(env.tasks, env.reminders, env.clock),
        markTaskNotDone = MarkTaskNotDone(env.tasks, env.reminders),
        archiveTask = ArchiveTask(env.tasks, env.clock),
        archiveDoneTasks = ArchiveDoneTasks(env.tasks, env.clock),
    )
    private val history = HistoryViewModel(
        observeProgressStats = ObserveProgressStats(env.tasks, env.clock),
        observeArchivedTasks = ObserveArchivedTasks(env.tasks),
        observeCategories = ObserveCategories(env.categories),
        restoreTask = RestoreTask(env.tasks),
        ticker = FakeTicker(env.clock),
    )

    @Test
    fun `add a task, tick it, archive it and find it in History`() {
        var screen by mutableStateOf(Screen.EDITOR)
        compose.setContent {
            TodoTheme(dynamicColor = false) {
                when (screen) {
                    Screen.EDITOR -> {
                        val state by editor.uiState.collectAsState()
                        TaskEditorContent(state = state, actions = editor, onClose = {})
                    }
                    Screen.HOME -> {
                        val state by home.uiState.collectAsState()
                        TaskListContent(
                            state = state,
                            onAddTask = {},
                            onEditTask = {},
                            onBack = null,
                            onToggleDone = home::onToggleDone,
                            onArchive = home::onArchive,
                            onArchiveDone = home::onArchiveDone,
                            onMessageShown = home::onMessageShown,
                        )
                    }
                    Screen.HISTORY -> {
                        val state by history.uiState.collectAsState()
                        HistoryContent(state = state, onPeriodSelected = history::onPeriodSelected, onRestore = history::onRestore)
                    }
                }
            }
        }

        // Add
        compose.onNodeWithText("Title").performTextInput("Buy milk")
        compose.onNodeWithText("Save").performClick()
        compose.waitForIdle()
        assertTrue(editor.uiState.value.isFinished)

        // Tick
        compose.runOnIdle { screen = Screen.HOME }
        compose.onNodeWithText("Buy milk").assertIsDisplayed()
        compose.onNodeWithContentDescription("Mark done: Buy milk").performClick()
        compose.onNodeWithText("Done · 1").assertIsDisplayed()

        // Archive
        compose.onNodeWithText("Archive done").performClick()
        compose.waitForIdle()
        assertTrue(home.uiState.value.done.isEmpty())
        assertIs<TaskStatus.Archived>(env.tasks.all.single().status)

        // History
        compose.runOnIdle { screen = Screen.HISTORY }
        compose.onNodeWithText("Buy milk").assertIsDisplayed()
        compose.onNodeWithContentDescription("Restore task: Buy milk").assertIsDisplayed()
    }
}
