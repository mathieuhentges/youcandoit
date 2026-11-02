package dev.mathieu.youcandoit.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import dev.mathieu.youcandoit.domain.fakes.NOW
import dev.mathieu.youcandoit.domain.fakes.aTask
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.Priority
import dev.mathieu.youcandoit.domain.model.Reminder
import dev.mathieu.youcandoit.settings.AppearanceDialogContent
import dev.mathieu.youcandoit.settings.ThemePreference
import dev.mathieu.youcandoit.ui.common.CategoryLabel
import dev.mathieu.youcandoit.ui.common.TaskItem
import dev.mathieu.youcandoit.ui.editor.TaskEditorActions
import dev.mathieu.youcandoit.ui.editor.TaskEditorContent
import dev.mathieu.youcandoit.ui.editor.TaskEditorUiState
import dev.mathieu.youcandoit.ui.tasks.ListTitle
import dev.mathieu.youcandoit.ui.tasks.TaskListContent
import dev.mathieu.youcandoit.ui.tasks.TaskListUiState
import dev.mathieu.youcandoit.ui.theme.TodoTheme
import dev.mathieu.youcandoit.ui.welcome.WelcomeScreen
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Renders the stateless screens with hand-made state and checks what the user sees and taps. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private fun item(id: String, priority: Priority = Priority.MEDIUM, done: Boolean = false) = TaskItem(
        task = aTask(id, priority = priority).let { if (done) it.markDone(NOW) else it }.copy(title = "Task $id"),
        category = CategoryLabel.Named("Work"),
        isOverdue = false,
    )

    @Test
    fun `the task list shows open tasks, the done section, and reports taps`() {
        val toggled = mutableListOf<String>()
        var archivedAll = false
        compose.setContent {
            TodoTheme(dynamicColor = false) {
                TaskListContent(
                    state = TaskListUiState(
                        isLoading = false,
                        title = ListTitle.All,
                        open = listOf(item("a", Priority.URGENT)),
                        done = listOf(item("b", done = true)),
                    ),
                    onAddTask = {},
                    onEditTask = {},
                    onBack = null,
                    onToggleDone = { toggled += it.task.id.value },
                    onArchive = {},
                    onArchiveDone = { archivedAll = true },
                    onMessageShown = {},
                )
            }
        }

        compose.onNodeWithText("All tasks").assertIsDisplayed()
        compose.onNodeWithText("Urgent").assertIsDisplayed()
        compose.onNodeWithText("Done · 1").assertIsDisplayed()

        compose.onNodeWithContentDescription("Mark done: Task a").performClick()
        compose.onNodeWithContentDescription("Mark not done: Task b").performClick()
        compose.onNodeWithText("Archive done").performClick()

        assertEquals(listOf("a", "b"), toggled)
        assertEquals(true, archivedAll)
    }

    @Test
    fun `a done task can be archived without swiping, through its accessibility action`() {
        val archived = mutableListOf<String>()
        compose.setContent {
            TodoTheme(dynamicColor = false) {
                TaskListContent(
                    state = TaskListUiState(isLoading = false, done = listOf(item("b", done = true))),
                    onAddTask = {},
                    onEditTask = {},
                    onBack = null,
                    onToggleDone = {},
                    onArchive = { archived += it.value },
                    onArchiveDone = {},
                    onMessageShown = {},
                )
            }
        }

        compose.onNodeWithText("Task b").performCustomAccessibilityActionWithLabel("Archive")

        assertEquals(listOf("b"), archived)
    }

    @Test
    fun `the welcome screen greets Jeanne and starts the app`() {
        var started = false
        compose.setContent {
            TodoTheme(dynamicColor = false) { WelcomeScreen(onStart = { started = true }) }
        }

        compose.onNodeWithText("You Can Do It, Jeanne!").assertIsDisplayed()
        compose.onNodeWithText("Let’s go").performClick()

        assertEquals(true, started)
    }

    @Test
    fun `an empty list invites the user to add a task`() {
        compose.setContent {
            TodoTheme(dynamicColor = false) {
                TaskListContent(
                    state = TaskListUiState(isLoading = false),
                    onAddTask = {},
                    onEditTask = {},
                    onBack = null,
                    onToggleDone = {},
                    onArchive = {},
                    onArchiveDone = {},
                    onMessageShown = {},
                )
            }
        }

        compose.onNodeWithText("Nothing here yet. Tap + to add a task.").assertIsDisplayed()
    }

    @Test
    fun `save stays disabled until the task has a title`() {
        var saves = 0
        val actions = RecordingActions(onSave = { saves++ })
        var state by mutableStateOf(TaskEditorUiState())
        compose.setContent {
            TodoTheme(dynamicColor = false) {
                TaskEditorContent(state = state, actions = actions, onClose = {})
            }
        }

        compose.onNodeWithText("Save").assertIsNotEnabled()

        compose.runOnIdle { state = state.copy(title = "Buy milk") }
        compose.onNodeWithText("Save").assertIsEnabled().performClick()

        assertEquals(1, saves)
    }

    @Test
    fun `the deadline section shows the date, time and reminder choices`() {
        compose.setContent {
            TodoTheme(dynamicColor = false) {
                TaskEditorContent(
                    state = TaskEditorUiState(
                        title = "Report",
                        hasDeadline = true,
                        date = LocalDate.of(2026, 11, 3),
                        time = LocalTime.of(17, 0),
                        reminder = Reminder.AtDeadline,
                    ),
                    actions = RecordingActions(),
                    onClose = {},
                )
            }
        }

        compose.onNodeWithText("Tue, Nov 3").assertIsDisplayed()
        compose.onNodeWithText("At deadline").assertIsSelected()
        compose.onNodeWithText("1 hour before").assertIsDisplayed()
    }

    @Test
    fun `the appearance dialog marks the current choice and reports a new one`() {
        var picked: ThemePreference? = null
        compose.setContent {
            TodoTheme(dynamicColor = false) {
                AppearanceDialogContent(selected = ThemePreference.SYSTEM, onSelect = { picked = it }, onDismiss = {})
            }
        }

        compose.onNodeWithText("Follow phone").assertIsSelected()
        compose.onNodeWithText("Dark").performClick()

        assertEquals(ThemePreference.DARK, picked)
    }
}

private class RecordingActions(private val onSave: () -> Unit = {}) : TaskEditorActions {
    override fun onTitleChange(title: String) = Unit
    override fun onDescriptionChange(description: String) = Unit
    override fun onPriorityChange(priority: Priority) = Unit
    override fun onCategoryChange(categoryId: CategoryId?) = Unit
    override fun onDeadlineToggle(enabled: Boolean) = Unit
    override fun onDateChange(date: LocalDate) = Unit
    override fun onTimeChange(time: LocalTime) = Unit
    override fun onReminderChange(reminder: Reminder) = Unit
    override fun onSave() = onSave.invoke()
    override fun onDelete() = Unit
    override fun onNewCategoryClick() = Unit
    override fun onNewCategoryNameChange(name: String) = Unit
    override fun onNewCategoryConfirm() = Unit
    override fun onNewCategoryDismiss() = Unit
}

