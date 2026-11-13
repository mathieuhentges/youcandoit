package dev.mathieu.youcandoit.ui.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mathieu.youcandoit.domain.model.Category
import dev.mathieu.youcandoit.domain.model.CategorySummary
import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.ListCounts
import dev.mathieu.youcandoit.domain.model.Priority
import dev.mathieu.youcandoit.domain.usecase.CreateCategory
import dev.mathieu.youcandoit.domain.usecase.DeleteCategory
import dev.mathieu.youcandoit.domain.usecase.ObserveListsOverview
import dev.mathieu.youcandoit.domain.usecase.RenameCategory
import dev.mathieu.youcandoit.ui.common.Ticker
import dev.mathieu.youcandoit.ui.common.asDomainError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CategoryDialog {
    data class Create(val name: String = "", val error: DomainError? = null) : CategoryDialog
    data class Rename(val category: Category, val name: String, val error: DomainError? = null) : CategoryDialog
    data class ConfirmDelete(val category: Category) : CategoryDialog
}

data class ListsUiState(
    val isLoading: Boolean = true,
    val categories: List<CategorySummary> = emptyList(),
    val uncategorized: ListCounts = ListCounts.EMPTY,
    val uncategorizedByPriority: Map<Priority, Int> = Priority.entries.associateWith { 0 },
    val dialog: CategoryDialog? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ListsViewModel @Inject constructor(
    observeListsOverview: ObserveListsOverview,
    ticker: Ticker,
    private val createCategory: CreateCategory,
    private val renameCategory: RenameCategory,
    private val deleteCategory: DeleteCategory,
) : ViewModel() {
    private val dialog = MutableStateFlow<CategoryDialog?>(null)

    /** Ignores a second tap on Create/Rename/Delete while the first is still being saved. */
    private var submitting = false

    val uiState: StateFlow<ListsUiState> =
        // Re-reads the overview every minute so overdue counts follow the clock.
        combine(ticker.minutes.flatMapLatest { observeListsOverview() }, dialog) { overview, currentDialog ->
            ListsUiState(
                isLoading = false,
                categories = overview.categories,
                uncategorized = overview.uncategorized,
                uncategorizedByPriority = overview.uncategorizedByPriority,
                dialog = currentDialog,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListsUiState())

    fun onNewCategory() {
        dialog.value = CategoryDialog.Create()
    }

    fun onRename(category: Category) {
        dialog.value = CategoryDialog.Rename(category, category.name)
    }

    fun onDelete(category: Category) {
        dialog.value = CategoryDialog.ConfirmDelete(category)
    }

    fun onDialogNameChange(name: String) {
        dialog.update { current ->
            when (current) {
                is CategoryDialog.Create -> current.copy(name = name, error = null)
                is CategoryDialog.Rename -> current.copy(name = name, error = null)
                else -> current
            }
        }
    }

    fun onDialogDismiss() {
        dialog.value = null
    }

    fun onDialogConfirm() {
        val current = dialog.value ?: return
        if (submitting) return
        submitting = true
        viewModelScope.launch {
            try {
                confirm(current)
            } finally {
                submitting = false
            }
        }
    }

    private suspend fun confirm(current: CategoryDialog) {
        when (current) {
            is CategoryDialog.Create -> createCategory(current.name).fold(
                onSuccess = { dialog.value = null },
                onFailure = { dialog.value = current.copy(error = it.asDomainError()) },
            )
            is CategoryDialog.Rename -> renameCategory(current.category.id, current.name).fold(
                onSuccess = { dialog.value = null },
                onFailure = { dialog.value = current.copy(error = it.asDomainError()) },
            )
            is CategoryDialog.ConfirmDelete -> {
                deleteCategory(current.category.id)
                dialog.value = null
            }
        }
    }
}
