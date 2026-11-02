package dev.mathieu.youcandoit.ui

import dev.mathieu.youcandoit.domain.fakes.NOW
import dev.mathieu.youcandoit.domain.fakes.SequentialIds
import dev.mathieu.youcandoit.domain.fakes.TestEnv
import dev.mathieu.youcandoit.domain.fakes.aCategory
import dev.mathieu.youcandoit.domain.fakes.aTask
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.ListCounts
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.usecase.CreateCategory
import dev.mathieu.youcandoit.domain.usecase.DeleteCategory
import dev.mathieu.youcandoit.domain.usecase.ObserveListsOverview
import dev.mathieu.youcandoit.domain.usecase.RenameCategory
import dev.mathieu.youcandoit.ui.lists.CategoryDialog
import dev.mathieu.youcandoit.ui.lists.ListsViewModel
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ListsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val env = TestEnv()
    private val work = aCategory("work", "Work")

    private fun viewModel() = ListsViewModel(
        observeListsOverview = ObserveListsOverview(env.tasks, env.categories, env.clock),
        ticker = FakeTicker(env.clock),
        createCategory = CreateCategory(env.categories, env.clock, SequentialIds("cat")),
        renameCategory = RenameCategory(env.categories),
        deleteCategory = DeleteCategory(env.categories, env.tasks),
    )

    @Test
    fun `shows each category with its counts, and the uncategorized tasks`() = runTest {
        env.categories.upsert(work)
        env.tasks.upsert(aTask("a", categoryId = work.id))
        env.tasks.upsert(aTask("b", categoryId = work.id).markDone(NOW))
        env.tasks.upsert(aTask("c"))
        val vm = viewModel()
        keepCollecting(vm.uiState)

        val state = vm.uiState.value
        assertEquals(listOf("Work"), state.categories.map { it.category.name })
        assertEquals(ListCounts(open = 1, overdue = 0, done = 1), state.categories.single().counts)
        assertEquals(ListCounts(open = 1, overdue = 0, done = 0), state.uncategorized)
    }

    @Test
    fun `creating a category through the dialog`() = runTest {
        val vm = viewModel()
        keepCollecting(vm.uiState)

        vm.onNewCategory()
        assertEquals(CategoryDialog.Create(), vm.uiState.value.dialog)
        vm.onDialogNameChange("Garden")
        vm.onDialogConfirm()

        assertNull(vm.uiState.value.dialog)
        assertEquals(listOf("Garden"), vm.uiState.value.categories.map { it.category.name })
    }

    @Test
    fun `a duplicate name keeps the dialog open with the error`() = runTest {
        env.categories.upsert(work)
        val vm = viewModel()
        keepCollecting(vm.uiState)

        vm.onNewCategory()
        vm.onDialogNameChange("work")
        vm.onDialogConfirm()

        val dialog = assertIs<CategoryDialog.Create>(vm.uiState.value.dialog)
        assertIs<DomainError.DuplicateCategoryName>(dialog.error)

        vm.onDialogNameChange("Workshop")
        assertNull(assertIs<CategoryDialog.Create>(vm.uiState.value.dialog).error)
    }

    @Test
    fun `renaming starts from the current name`() = runTest {
        env.categories.upsert(work)
        val vm = viewModel()
        keepCollecting(vm.uiState)

        vm.onRename(work)
        assertEquals(CategoryDialog.Rename(work, "Work"), vm.uiState.value.dialog)
        vm.onDialogNameChange("Job")
        vm.onDialogConfirm()

        assertNull(vm.uiState.value.dialog)
        assertEquals("Job", env.categories.findById(work.id)?.name)
    }

    @Test
    fun `deleting asks first, then keeps the tasks as uncategorized`() = runTest {
        env.categories.upsert(work)
        env.tasks.upsert(aTask("a", categoryId = work.id))
        val vm = viewModel()
        keepCollecting(vm.uiState)

        vm.onDelete(work)
        assertEquals(CategoryDialog.ConfirmDelete(work), vm.uiState.value.dialog)
        vm.onDialogConfirm()

        assertNull(vm.uiState.value.dialog)
        assertNull(env.categories.findById(CategoryId("work")))
        assertNull(env.tasks.findById(TaskId("a"))?.categoryId)
        assertEquals(ListCounts(open = 1, overdue = 0, done = 0), vm.uiState.value.uncategorized)
    }
}
