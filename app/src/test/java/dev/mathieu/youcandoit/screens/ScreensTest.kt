package dev.mathieu.youcandoit.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import dev.mathieu.youcandoit.domain.fakes.NOW
import dev.mathieu.youcandoit.domain.fakes.aTask
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.DoneOrder
import dev.mathieu.youcandoit.domain.model.HistoryOrder
import dev.mathieu.youcandoit.domain.model.Priority
import dev.mathieu.youcandoit.domain.model.Reminder
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.model.TaskList
import dev.mathieu.youcandoit.domain.model.TaskOrder
import dev.mathieu.youcandoit.settings.AppearanceDialogContent
import dev.mathieu.youcandoit.settings.ListSort
import dev.mathieu.youcandoit.settings.ThemePreference
import dev.mathieu.youcandoit.ui.common.CategoryLabel
import dev.mathieu.youcandoit.ui.common.SubTaskCount
import dev.mathieu.youcandoit.ui.common.TaskItem
import dev.mathieu.youcandoit.ui.editor.TaskEditorActions
import dev.mathieu.youcandoit.ui.editor.TaskEditorContent
import dev.mathieu.youcandoit.ui.editor.TaskEditorUiState
import dev.mathieu.youcandoit.ui.history.HistoryContent
import dev.mathieu.youcandoit.ui.history.HistoryUiState
import dev.mathieu.youcandoit.ui.tasks.ListTitle
import dev.mathieu.youcandoit.ui.tasks.TaskGroup
import dev.mathieu.youcandoit.ui.tasks.TaskListContent
import dev.mathieu.youcandoit.ui.tasks.TaskListUiState
import dev.mathieu.youcandoit.ui.tasks.TaskSelectionActions
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
                        groups = listOf(TaskGroup(null, CategoryLabel.Uncategorized, listOf(item("a", Priority.URGENT)))),
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

    @OptIn(ExperimentalTestApi::class)
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
    fun `the done section has its own sort menu, offering Category only on Home`() {
        var sort by mutableStateOf(ListSort())
        var list by mutableStateOf<TaskList>(TaskList.All)
        compose.setContent {
            TodoTheme(dynamicColor = false) {
                TaskListContent(
                    state = TaskListUiState(isLoading = false, list = list, done = listOf(item("b", done = true)), sort = sort),
                    onAddTask = {},
                    onEditTask = {},
                    onBack = null,
                    onToggleDone = {},
                    onArchive = {},
                    onArchiveDone = {},
                    onMessageShown = {},
                    onSortChange = { sort = it },
                )
            }
        }

        compose.onNodeWithContentDescription("Sort done tasks").performClick()
        compose.onNodeWithText("Category").performClick()
        assertEquals(ListSort(doneOrder = DoneOrder.CATEGORY), sort)

        list = TaskList.Uncategorized
        compose.onNodeWithContentDescription("Sort done tasks").performClick()
        compose.onNodeWithText("Category").assertDoesNotExist()
        compose.onNodeWithText("Recently done").assertIsSelected()
    }

    @Test
    fun `history's sort menu shows the current order and reports a new one`() {
        var chosen: HistoryOrder? = null
        compose.setContent {
            TodoTheme(dynamicColor = false) {
                HistoryContent(
                    state = HistoryUiState(isLoading = false, order = HistoryOrder.RECENTLY_ARCHIVED),
                    onPeriodSelected = {},
                    onRestore = {},
                    onOrderSelected = { chosen = it },
                )
            }
        }

        compose.onNodeWithText("Recently archived").performClick()
        compose.onNodeWithText("Category").performClick()

        assertEquals(HistoryOrder.CATEGORY, chosen)
    }

    @Test
    fun `home has a button that brings the encouragement back`() {
        var asked = 0
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
                    onShowWelcome = { asked++ },
                )
            }
        }

        compose.onNodeWithContentDescription("Show the encouragement again").performClick()

        assertEquals(1, asked)
    }

    @Test
    fun `the sort menu reports a new order, and only my order offers drag handles`() {
        var sort by mutableStateOf(ListSort())
        compose.setContent {
            TodoTheme(dynamicColor = false) {
                TaskListContent(
                    state = TaskListUiState(
                        isLoading = false,
                        groups = listOf(TaskGroup(null, CategoryLabel.Uncategorized, listOf(item("a")))),
                        sort = sort,
                    ),
                    onAddTask = {},
                    onEditTask = {},
                    onBack = null,
                    onToggleDone = {},
                    onArchive = {},
                    onArchiveDone = {},
                    onMessageShown = {},
                    onSortChange = { sort = it },
                )
            }
        }
        compose.onNodeWithContentDescription("Drag to reorder: Task a").assertIsDisplayed()

        compose.onNodeWithContentDescription("Sort").performClick()
        compose.onNodeWithText("Priority").performClick()

        assertEquals(TaskOrder.PRIORITY, sort.order)
        compose.onNodeWithContentDescription("Drag to reorder: Task a").assertDoesNotExist()
    }

    @Test
    fun `a parent shows its sub tasks and done count, collapses, and asks before ticking them all`() {
        var confirmed = false
        val parent = item("p").copy(subTasks = SubTaskCount(done = 1, total = 2))
        compose.setContent {
            TodoTheme(dynamicColor = false) {
                TaskListContent(
                    state = TaskListUiState(
                        isLoading = false,
                        groups = listOf(TaskGroup(null, CategoryLabel.Uncategorized, listOf(parent))),
                        subTasks = mapOf(TaskId("p") to listOf(item("c1"), item("c2", done = true))),
                        confirmDone = parent,
                    ),
                    onAddTask = {},
                    onEditTask = {},
                    onBack = null,
                    onToggleDone = {},
                    onArchive = {},
                    onArchiveDone = {},
                    onMessageShown = {},
                    onConfirmDone = { confirmed = true },
                )
            }
        }

        compose.onNodeWithText("Mark all done?").assertIsDisplayed()
        compose.onNodeWithText("Its open sub task will be marked done too.").assertIsDisplayed()
        compose.onNodeWithText("Mark all done").performClick()
        assertEquals(true, confirmed)
    }

    @Test
    fun `a done parent keeps its sub tasks visible under it in the done section`() {
        val parent = item("p", done = true).copy(subTasks = SubTaskCount(done = 1, total = 1))
        compose.setContent {
            TodoTheme(dynamicColor = false) {
                TaskListContent(
                    state = TaskListUiState(
                        isLoading = false,
                        done = listOf(parent),
                        subTasks = mapOf(TaskId("p") to listOf(item("c", done = true))),
                    ),
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

        compose.onNodeWithText("Task p").assertIsDisplayed()
        compose.onNodeWithText("Task c").assertIsDisplayed()
    }

    @Test
    fun `save and add another confirms with a snackbar`() {
        var state by mutableStateOf(TaskEditorUiState())
        compose.setContent {
            TodoTheme(dynamicColor = false) {
                TaskEditorContent(state = state, actions = RecordingActions(onAddedShown = { state = state.copy(justAdded = null) }), onClose = {})
            }
        }

        compose.runOnIdle { state = state.copy(justAdded = 1) }

        compose.onNodeWithText("1 task added").assertIsDisplayed()
    }

    @Test
    fun `sub tasks can be hidden under their parent`() {
        val parent = item("p").copy(subTasks = SubTaskCount(done = 0, total = 1))
        compose.setContent {
            TodoTheme(dynamicColor = false) {
                TaskListContent(
                    state = TaskListUiState(
                        isLoading = false,
                        groups = listOf(TaskGroup(null, CategoryLabel.Uncategorized, listOf(parent))),
                        subTasks = mapOf(TaskId("p") to listOf(item("c"))),
                    ),
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

        compose.onNodeWithText("0/1").assertIsDisplayed()
        compose.onNodeWithText("Task c").assertIsDisplayed()

        compose.onNodeWithContentDescription("Hide sub tasks").performClick()

        compose.onNodeWithText("Task c").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show sub tasks").assertIsDisplayed()
    }

    @Test
    fun `a long press starts selection mode, where a tap selects instead of opening`() {
        var state by mutableStateOf(
            TaskListUiState(isLoading = false, groups = listOf(TaskGroup(null, CategoryLabel.Uncategorized, listOf(item("a"), item("b"))))),
        )
        val opened = mutableListOf<String>()
        val selection = object : TaskSelectionActions by TaskSelectionActions.None {
            override fun onToggleSelection(id: TaskId) {
                state = state.copy(selected = if (id in state.selected) state.selected - id else state.selected + id)
            }
            override fun onSetSelectedPriority(priority: Priority) {
                state = state.copy(selected = emptySet(), message = null)
                opened += "priority:" + priority.name
            }
        }
        compose.setContent {
            TodoTheme(dynamicColor = false) {
                TaskListContent(
                    state = state,
                    onAddTask = {},
                    onEditTask = { opened += it.value },
                    onBack = null,
                    onToggleDone = {},
                    onArchive = {},
                    onArchiveDone = {},
                    onMessageShown = {},
                    selection = selection,
                )
            }
        }

        compose.onNodeWithText("Task a").performTouchInput { longClick() }
        compose.onNodeWithText("1 selected").assertIsDisplayed()
        compose.onNodeWithText("Task b").performClick()
        compose.onNodeWithText("2 selected").assertIsDisplayed()
        compose.onNodeWithContentDescription("Drag to reorder: Task a").assertDoesNotExist()

        compose.onNodeWithContentDescription("Set priority").performClick()
        compose.onNodeWithText("Urgent").performClick()

        assertEquals(listOf("priority:URGENT"), opened)
        compose.onNodeWithText("All tasks").assertIsDisplayed()
    }

    @Test
    fun `the priority picker runs from Minimal on the left to Urgent on the right`() {
        compose.setContent {
            TodoTheme(dynamicColor = false) {
                TaskEditorContent(state = TaskEditorUiState(), actions = RecordingActions(), onClose = {})
            }
        }

        val lefts = listOf("Minimal", "Low", "Medium", "High", "Urgent")
            .map { compose.onNodeWithText(it).getUnclippedBoundsInRoot().left }
        assertEquals(lefts.sorted(), lefts)
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

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `home shows a header per category, and moving a task past one moves it to that category`() {
        val work = CategoryId("work")
        var reordered: Pair<CategoryId?, List<String>>? = null
        compose.setContent {
            TodoTheme(dynamicColor = false) {
                TaskListContent(
                    state = TaskListUiState(
                        isLoading = false,
                        groups = listOf(
                            TaskGroup(work, CategoryLabel.Named("Work"), listOf(item("a"))),
                            TaskGroup(null, CategoryLabel.Uncategorized, listOf(item("b"), item("c"))),
                        ),
                    ),
                    onAddTask = {},
                    onEditTask = {},
                    onBack = null,
                    onToggleDone = {},
                    onArchive = {},
                    onArchiveDone = {},
                    onMessageShown = {},
                    onReorder = { category, ids -> reordered = category to ids.map { it.value } },
                )
            }
        }

        compose.onNodeWithText("Work").assertIsDisplayed()
        compose.onNodeWithText("Uncategorized").assertIsDisplayed()

        compose.onNodeWithText("Task b").performCustomAccessibilityActionWithLabel("Move up")

        assertEquals(work to listOf("a", "b"), reordered)
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

        compose.onNodeWithText("Tue, Nov 3").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("At deadline").assertIsSelected()
        compose.onNodeWithText("1 hour before").performScrollTo().assertIsDisplayed()
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

private class RecordingActions(
    private val onSave: () -> Unit = {},
    private val onAddedShown: () -> Unit = {},
) : TaskEditorActions {
    override fun onTitleChange(title: String) = Unit
    override fun onSaveAndAddAnother() = Unit
    override fun onAddedShown() = onAddedShown.invoke()
    override fun onSeveralAtOnceChange(enabled: Boolean) = Unit
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

