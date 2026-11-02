package dev.mathieu.youcandoit.ui

import androidx.lifecycle.SavedStateHandle
import dev.mathieu.youcandoit.domain.fakes.SequentialIds
import dev.mathieu.youcandoit.domain.fakes.TestEnv
import dev.mathieu.youcandoit.domain.usecase.AddTask
import dev.mathieu.youcandoit.domain.usecase.AddTasks
import dev.mathieu.youcandoit.domain.usecase.CreateCategory
import dev.mathieu.youcandoit.domain.usecase.DeleteCategory
import dev.mathieu.youcandoit.domain.usecase.DeleteTask
import dev.mathieu.youcandoit.domain.usecase.GetTask
import dev.mathieu.youcandoit.domain.usecase.ObserveCategories
import dev.mathieu.youcandoit.domain.usecase.ObserveSubTasks
import dev.mathieu.youcandoit.domain.usecase.ObserveListsOverview
import dev.mathieu.youcandoit.domain.usecase.RenameCategory
import dev.mathieu.youcandoit.domain.usecase.UpdateTask
import dev.mathieu.youcandoit.ui.editor.TaskEditorViewModel
import dev.mathieu.youcandoit.ui.lists.ListsViewModel
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * A real save takes a moment (database write), so a second tap lands before the first finishes.
 * StandardTestDispatcher reproduces that: launched work waits until the test lets it run.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DoubleTapTest {
    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    private val env = TestEnv()

    @Test
    fun `tapping Create twice creates one category and closes the dialog`() = runTest {
        val vm = ListsViewModel(
            observeListsOverview = ObserveListsOverview(env.tasks, env.categories, env.clock),
            ticker = FakeTicker(env.clock),
            createCategory = CreateCategory(env.categories, env.clock, SequentialIds("cat")),
            renameCategory = RenameCategory(env.categories),
            deleteCategory = DeleteCategory(env.categories, env.tasks),
        )
        keepCollecting(vm.uiState)
        vm.onNewCategory()
        vm.onDialogNameChange("Garden")

        vm.onDialogConfirm()
        vm.onDialogConfirm()
        advanceUntilIdle()

        assertEquals(1, env.categories.all.size)
        assertNull(vm.uiState.value.dialog)
    }

    @Test
    fun `tapping Create twice in the editor's category dialog creates one category`() = runTest {
        val vm = TaskEditorViewModel(
            savedStateHandle = SavedStateHandle(),
            observeCategories = ObserveCategories(env.categories),
            getTask = GetTask(env.tasks),
            addTask = AddTask(env.tasks, env.categories, env.reminders, env.clock, SequentialIds("task")),
            addTasks = AddTasks(env.tasks, env.categories, env.reminders, env.clock, SequentialIds("tasks")),
            updateTask = UpdateTask(env.tasks, env.categories, env.reminders, env.clock),
            deleteTask = DeleteTask(env.tasks, env.reminders),
            createCategory = CreateCategory(env.categories, env.clock, SequentialIds("cat")),
            observeSubTasks = ObserveSubTasks(env.tasks),
            clock = env.clock,
            zone = ZoneId.of("America/Montreal"),
        )
        vm.onNewCategoryClick()
        vm.onNewCategoryNameChange("Garden")

        vm.onNewCategoryConfirm()
        vm.onNewCategoryConfirm()
        advanceUntilIdle()

        assertEquals(1, env.categories.all.size)
        assertNull(vm.uiState.value.newCategory)
    }
}
