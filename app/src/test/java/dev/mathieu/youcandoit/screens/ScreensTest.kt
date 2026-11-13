package dev.mathieu.youcandoit.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import kotlin.test.assertTrue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
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
import dev.mathieu.youcandoit.domain.model.Category
import dev.mathieu.youcandoit.domain.model.CategorySummary
import dev.mathieu.youcandoit.domain.model.ListCounts
import dev.mathieu.youcandoit.settings.CustomizeContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.performSemanticsAction
import dev.mathieu.youcandoit.settings.PriorityColors
import dev.mathieu.youcandoit.ui.lists.ListsContent
import dev.mathieu.youcandoit.ui.lists.ListsUiState
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import dev.mathieu.youcandoit.settings.ListSort
import dev.mathieu.youcandoit.settings.ThemePreference
import dev.mathieu.youcandoit.ui.about.AboutContent
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
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlin.test.assertNull
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
        // Just the dot: the priority's name is only for screen readers, so the title keeps the room.
        compose.onNodeWithContentDescription("Urgent", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Urgent").assertDoesNotExist()
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

        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Show the encouragement again").performClick()

        assertEquals(1, asked)
    }

    @Test
    fun `home's menu holds appearance, the encouragement, export, import and about`() {
        var opened = 0
        var exported = 0
        var imported = 0
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
                    onOpenCustomize = {},
                    onShowWelcome = {},
                    onOpenAbout = { opened++ },
                    onExport = { exported++ },
                    onImport = { imported++ },
                )
            }
        }

        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Customize").assertIsDisplayed()
        compose.onNodeWithText("Show the encouragement again").assertIsDisplayed()
        compose.onNodeWithText("Export tasks").performClick()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Import tasks").performClick()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("About & contact").performClick()

        assertEquals(listOf(1, 1, 1), listOf(exported, imported, opened))
    }

    @Test
    fun `about shows the version and the contact address, and reports its buttons`() {
        var feedback = 0
        var privacy = 0
        compose.setContent {
            TodoTheme(dynamicColor = false) {
                AboutContent(version = "0.1.0", onBack = {}, onSendFeedback = { feedback++ }, onOpenPrivacyPolicy = { privacy++ })
            }
        }

        compose.onNodeWithText("Version 0.1.0").assertIsDisplayed()
        compose.onNodeWithText("mathieuhentges+todoapp@gmail.com").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Send feedback").performScrollTo().performClick()
        compose.onNodeWithText("Privacy policy").performScrollTo().performClick()

        assertEquals(1, feedback)
        assertEquals(1, privacy)
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
    fun `the sub-task count next to a parent folds its sub tasks away and back`() {
        val toggled = mutableListOf<String>()
        val parent = item("p").copy(subTasks = SubTaskCount(done = 1, total = 2))
        var state by mutableStateOf(
            TaskListUiState(
                isLoading = false,
                groups = listOf(TaskGroup(null, CategoryLabel.Uncategorized, listOf(parent))),
                subTasks = mapOf(TaskId("p") to listOf(item("c1"), item("c2", done = true))),
            ),
        )
        compose.setContent {
            TodoTheme(dynamicColor = false) {
                TaskListContent(
                    state = state,
                    onAddTask = {},
                    onEditTask = {},
                    onBack = null,
                    onToggleDone = {},
                    onArchive = {},
                    onArchiveDone = {},
                    onMessageShown = {},
                    onToggleSubTasks = { id ->
                        toggled += id.value
                        state = state.copy(collapsed = if (id in state.collapsed) state.collapsed - id else state.collapsed + id)
                    },
                )
            }
        }

        compose.onNodeWithText("Task c1").assertIsDisplayed()
        compose.onNodeWithText("1/2", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Hide sub tasks, 1 of 2 sub tasks done", useUnmergedTree = true).performClick()

        compose.onNodeWithText("Task c1").assertDoesNotExist()
        compose.onNodeWithText("1/2", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Show sub tasks, 1 of 2 sub tasks done", useUnmergedTree = true).performClick()

        compose.onNodeWithText("Task c1").assertIsDisplayed()
        assertEquals(listOf("p", "p"), toggled)
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

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a category can be collapsed, and a task moved under it goes on top of its hidden tasks`() {
        val work = CategoryId("work")
        var reordered: Pair<CategoryId?, List<String>>? = null
        compose.setContent {
            TodoTheme(dynamicColor = false) {
                TaskListContent(
                    state = TaskListUiState(
                        isLoading = false,
                        groups = listOf(
                            TaskGroup(work, CategoryLabel.Named("Work"), listOf(item("a1"), item("a2"))),
                            TaskGroup(null, CategoryLabel.Uncategorized, listOf(item("b"))),
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

        compose.onNodeWithText("Work").performClick()
        compose.onNodeWithText("Task a1").assertDoesNotExist()
        compose.onNodeWithText("Work").assertTextContains("2", substring = true)

        compose.onNodeWithText("Task b").performCustomAccessibilityActionWithLabel("Move up")
        assertEquals(work to listOf("b", "a1", "a2"), reordered)

        compose.onNodeWithText("Work").performClick()
        compose.onNodeWithText("Task a1").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a task can go under the one above it, and a sub task can come back out`() {
        val parent = item("p").copy(subTasks = SubTaskCount(done = 0, total = 1))
        var nested: Pair<String, String>? = null
        var movedOut: String? = null
        compose.setContent {
            TodoTheme(dynamicColor = false) {
                TaskListContent(
                    state = TaskListUiState(
                        isLoading = false,
                        groups = listOf(TaskGroup(null, label = null, listOf(item("a"), item("b"), parent))),
                        subTasks = mapOf(TaskId("p") to listOf(item("c"))),
                    ),
                    onAddTask = {},
                    onEditTask = {},
                    onBack = null,
                    onToggleDone = {},
                    onArchive = {},
                    onArchiveDone = {},
                    onMessageShown = {},
                    onMakeSubTask = { id, parentId -> nested = id.value to parentId.value },
                    onMoveOutOfParent = { movedOut = it.value },
                )
            }
        }

        compose.onNodeWithText("Task b").performCustomAccessibilityActionWithLabel("Make sub task of “Task a”")
        assertEquals("b" to "a", nested)

        // One level only: a parent can't go under another task.
        assertTrue(compose.onNodeWithText("Task p").fetchSemanticsNode().config[SemanticsActions.CustomActions].none { "sub task" in it.label })

        compose.onNodeWithText("Task c").performCustomAccessibilityActionWithLabel("Move out of “Task p”")
        assertEquals("c", movedOut)
    }

    private fun dragList(onReorder: (List<String>) -> Unit, onNest: (Pair<String, String>) -> Unit) = compose.setContent {
        TodoTheme(dynamicColor = false) {
            TaskListContent(
                state = TaskListUiState(isLoading = false, groups = listOf(TaskGroup(null, label = null, listOf(item("a"), item("b"), item("c"))))),
                onAddTask = {},
                onEditTask = {},
                onBack = null,
                onToggleDone = {},
                onArchive = {},
                onArchiveDone = {},
                onMessageShown = {},
                onReorder = { _, ids -> onReorder(ids.map { it.value }) },
                onMakeSubTask = { id, parentId -> onNest(id.value to parentId.value) },
            )
        }
    }

    @Test
    fun `a long drag that drifts sideways only reorders, it doesn't nest`() {
        var reordered: List<String>? = null
        var nested: Pair<String, String>? = null
        dragList({ reordered = it }, { nested = it })

        // Down past two rows, drifting right well beyond the nesting distance on the way.
        compose.onNodeWithContentDescription("Drag to reorder: Task a", useUnmergedTree = true).performTouchInput {
            down(center)
            repeat(60) {
                moveBy(Offset(2.dp.toPx(), 4.dp.toPx()))
                advanceEventTime(16)
            }
            up()
        }
        compose.waitForIdle()

        assertEquals(listOf("b", "c", "a"), reordered)
        assertNull(nested)
    }

    @Test
    fun `a task put in place, then pushed right, goes under the task above`() {
        var nested: Pair<String, String>? = null
        dragList({}, { nested = it })

        val handle = compose.onNodeWithContentDescription("Drag to reorder: Task c", useUnmergedTree = true)
        handle.performTouchInput {
            down(center)
            repeat(5) {
                moveBy(Offset(4.dp.toPx(), 0f))
                advanceEventTime(16)
            }
        }
        // While dragging, the row says which way to push and, once far enough, what letting go does.
        compose.onNodeWithText("Push right to put it under “Task b”").assertIsDisplayed()
        handle.performTouchInput {
            repeat(15) {
                moveBy(Offset(4.dp.toPx(), 0f))
                advanceEventTime(16)
            }
        }
        compose.onNodeWithText("Let go: sub task of “Task b”").assertIsDisplayed()
        handle.performTouchInput { up() }
        compose.waitForIdle()

        assertEquals("c" to "b", nested)
        compose.onNodeWithText("Let go: sub task of “Task b”").assertDoesNotExist()
    }

    @Test
    fun `editing a task offers the tasks it can go under, and hides the category once one is picked`() {
        var parent: TaskId? = null
        var state by mutableStateOf(
            TaskEditorUiState(isEditing = true, title = "Book the van", parentChoices = listOf(aTask("p").copy(title = "Move house"))),
        )
        compose.setContent {
            TodoTheme(dynamicColor = false) {
                TaskEditorContent(state = state, actions = RecordingActions(onParentChange = { parent = it }), onClose = {})
            }
        }

        compose.onNodeWithText("Category").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("None (a main task)").performScrollTo().performClick()
        compose.onNodeWithText("Move house").performClick()
        assertEquals(TaskId("p"), parent)

        state = state.copy(parentId = TaskId("p"), parentTitle = "Move house")
        compose.onNodeWithText("Move house").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Category").assertDoesNotExist()
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
    fun `without a deadline, a reminder can still be set on a chosen date, and the chip shows it`() {
        var state by mutableStateOf(TaskEditorUiState(title = "Call"))
        compose.setContent {
            TodoTheme(dynamicColor = false) {
                TaskEditorContent(state = state, actions = RecordingActions(), onClose = {})
            }
        }

        compose.onNodeWithText("None").performScrollTo().assertIsSelected()
        compose.onNodeWithText("On a date…").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("At deadline").assertDoesNotExist()

        state = state.copy(reminder = Reminder.At(NOW.plus(Duration.ofDays(1))))

        compose.onNodeWithText("On a date…").assertDoesNotExist()
        compose.onNodeWithText("None").assertIsNotSelected()
    }

    @Test
    fun `customize marks the current theme and reports a new one`() {
        var picked: ThemePreference? = null
        compose.setContent {
            TodoTheme(dynamicColor = false) {
                CustomizeContent(
                    theme = ThemePreference.SYSTEM,
                    colors = PriorityColors.DEFAULT,
                    onBack = {},
                    onThemeSelected = { picked = it },
                    onColorSelected = { _, _ -> },
                    onResetColors = {},
                )
            }
        }

        compose.onNodeWithText("Follow phone").assertIsSelected()
        compose.onNodeWithText("Dark").performClick()

        assertEquals(ThemePreference.DARK, picked)
    }

    @Test
    fun `customize picks any color for a priority, and resets them`() {
        var colors by mutableStateOf(PriorityColors.DEFAULT)
        var resets = 0
        compose.setContent {
            TodoTheme(dynamicColor = false) {
                CustomizeContent(
                    theme = ThemePreference.SYSTEM,
                    colors = colors,
                    onBack = {},
                    onThemeSelected = {},
                    onColorSelected = { priority, color -> colors = colors.with(priority, color) },
                    onResetColors = { resets++ },
                )
            }
        }

        compose.onNodeWithText("Reset to the original colors").assertIsNotEnabled()
        compose.onNodeWithText("Urgent").performClick()
        compose.onNodeWithText("Urgent color").assertIsDisplayed()
        compose.onNodeWithText("#D93025").assertIsDisplayed()
        compose.onNodeWithContentDescription("Red").assertIsSelected()

        // A quick pick.
        compose.onNodeWithContentDescription("Purple").performScrollTo().performClick()
        compose.onNodeWithText("#9334E6").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()

        assertEquals(0xFF9334E6.toInt(), colors.customOf(Priority.URGENT))
        compose.onNodeWithText("Urgent color").assertDoesNotExist()
        compose.onNodeWithText("Custom").assertIsDisplayed()

        // Any hue, from the bar (as a screen reader would set it).
        compose.onNodeWithText("High").performClick()
        compose.onNodeWithContentDescription("Hue").performSemanticsAction(SemanticsActions.SetProgress) { it(240f) }
        compose.onNodeWithText("Done").performClick()
        val high = Color(colors.customOf(Priority.HIGH)!!)
        assertTrue(high.blue > high.red && high.blue > high.green)

        // Back to the original from the dialog.
        compose.onNodeWithText("Urgent").performClick()
        compose.onNodeWithText("Use original").performClick()
        assertNull(colors.customOf(Priority.URGENT))

        compose.onNodeWithText("Reset to the original colors").performScrollTo().performClick()
        assertEquals(1, resets)
    }

    @Test
    fun `a category's details count its tasks, and its open ones by priority`() {
        val work = Category(CategoryId("work"), "Work", NOW)
        compose.setContent {
            TodoTheme(dynamicColor = false) {
                ListsContent(
                    state = ListsUiState(
                        isLoading = false,
                        categories = listOf(
                            CategorySummary(
                                work,
                                ListCounts(open = 3, overdue = 1, done = 2),
                                mapOf(Priority.URGENT to 2, Priority.HIGH to 0, Priority.MEDIUM to 1, Priority.LOW to 0, Priority.MINIMAL to 0),
                            ),
                        ),
                    ),
                    onOpenList = {},
                    onNewCategory = {},
                    onRename = {},
                    onDelete = {},
                    onDialogNameChange = {},
                    onDialogConfirm = {},
                    onDialogDismiss = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Options for Work").performClick()
        compose.onNodeWithText("Details").performClick()

        compose.onNodeWithText("Open tasks by priority").assertIsDisplayed()
        compose.onNode(hasText("Open") and hasText("3")).assertIsDisplayed()
        compose.onNode(hasText("Overdue") and hasText("1")).assertIsDisplayed()
        compose.onNode(hasText("Done") and hasText("2")).assertIsDisplayed()
        compose.onNode(hasText("Urgent") and hasText("2")).assertIsDisplayed()
        compose.onNode(hasText("Medium") and hasText("1")).assertIsDisplayed()
        compose.onNode(hasText("High") and hasText("0")).assertIsDisplayed()

        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Open tasks by priority").assertDoesNotExist()
    }
}

private class RecordingActions(
    private val onSave: () -> Unit = {},
    private val onAddedShown: () -> Unit = {},
    private val onParentChange: (TaskId?) -> Unit = {},
) : TaskEditorActions {
    override fun onTitleChange(title: String) = Unit
    override fun onSaveAndAddAnother() = Unit
    override fun onAddedShown() = onAddedShown.invoke()
    override fun onSeveralAtOnceChange(enabled: Boolean) = Unit
    override fun onDescriptionChange(description: String) = Unit
    override fun onPriorityChange(priority: Priority) = Unit
    override fun onCategoryChange(categoryId: CategoryId?) = Unit
    override fun onParentChange(parentId: TaskId?) = onParentChange.invoke(parentId)
    override fun onDeadlineToggle(enabled: Boolean) = Unit
    override fun onDateChange(date: LocalDate) = Unit
    override fun onTimeChange(time: LocalTime) = Unit
    override fun onReminderChange(reminder: Reminder) = Unit
    override fun onReminderTimeChange(date: LocalDate, time: LocalTime) = Unit
    override fun onSave() = onSave.invoke()
    override fun onDelete() = Unit
    override fun onNewCategoryClick() = Unit
    override fun onNewCategoryNameChange(name: String) = Unit
    override fun onNewCategoryConfirm() = Unit
    override fun onNewCategoryDismiss() = Unit
}

