package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.model.Category
import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.Task
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.model.TaskOrder
import dev.mathieu.youcandoit.domain.repository.CategoryRepository
import dev.mathieu.youcandoit.domain.repository.TaskRepository
import java.text.Collator
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Categories in alphabetical order for the device's language: case and accents don't push a name to the end. */
class ObserveCategories @Inject constructor(
    private val categories: CategoryRepository,
) {
    operator fun invoke(): Flow<List<Category>> =
        categories.observeAll().map { all -> all.sortedWith(categoryOrder()) }
}

internal fun categoryOrder(): Comparator<Category> {
    val collator = Collator.getInstance() // not thread-safe to share: one per sort
    return compareBy(collator) { it.name }
}

class GetTask @Inject constructor(
    private val tasks: TaskRepository,
) {
    suspend operator fun invoke(id: TaskId): Result<Task> =
        tasks.findById(id)?.let { Result.success(it) } ?: Result.failure(DomainError.TaskNotFound(id))
}

/** A parent's sub tasks for its editor: open ones in drag order, then done ones; archived left out. */
class ObserveSubTasks @Inject constructor(
    private val tasks: TaskRepository,
) {
    operator fun invoke(parentId: TaskId): Flow<List<Task>> =
        tasks.observeAll().map { all ->
            val children = all.filter { it.parentId == parentId && !it.isArchived }
            children.filter { it.isOpen }.sortedWith(ObserveTaskList.comparatorFor(TaskOrder.MY_ORDER)) +
                children.filter { it.isDone }.sortedByDescending { it.doneAt }
        }
}
