package dev.mathieu.youcandoit.ui.common

import dev.mathieu.youcandoit.domain.model.Category
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.Task
import java.time.Instant

/**
 * A task as a row shows it. [category] is null when the screen already shows one category.
 * [subTasks] is set on a parent: how many of its sub tasks are done.
 */
data class TaskItem(
    val task: Task,
    val category: CategoryLabel?,
    val isOverdue: Boolean,
    val subTasks: SubTaskCount? = null,
)

data class SubTaskCount(val done: Int, val total: Int)

sealed interface CategoryLabel {
    data class Named(val name: String) : CategoryLabel
    data object Uncategorized : CategoryLabel
}

fun Task.toItem(categoriesById: Map<CategoryId, Category>, showCategory: Boolean, now: Instant): TaskItem {
    val id = categoryId
    val label: CategoryLabel? = when {
        !showCategory -> null
        id == null -> CategoryLabel.Uncategorized
        else -> categoriesById[id]?.let { CategoryLabel.Named(it.name) } ?: CategoryLabel.Uncategorized
    }
    return TaskItem(this, label, isOverdue(now))
}

/** One-off message for a snackbar. */
sealed interface UserMessage {
    data class Archived(val count: Int) : UserMessage
    data class Deleted(val count: Int) : UserMessage
    data class Error(val error: DomainError) : UserMessage
}

/** Domain errors are expected and shown to the user; anything else is a bug and is rethrown. */
fun Throwable.asDomainError(): DomainError = this as? DomainError ?: throw this
