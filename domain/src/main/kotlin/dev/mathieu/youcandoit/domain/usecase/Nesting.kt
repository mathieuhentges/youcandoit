package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.model.TaskOrder
import dev.mathieu.youcandoit.domain.model.Task
import dev.mathieu.youcandoit.domain.repository.CategoryRepository
import dev.mathieu.youcandoit.domain.repository.TaskRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * An open task goes under [parentId] as its last open sub task, taking the parent's category (a drag
 * to the right, or picked in the editor). A sub task can change parents this way. One level only:
 * a task that has sub tasks of its own can't.
 */
class MakeSubTask @Inject constructor(
    private val tasks: TaskRepository,
) {
    suspend operator fun invoke(taskId: TaskId, parentId: TaskId): Result<Unit> {
        val parent = tasks.parentFor(parentId).getOrElse { return Result.failure(it) }!!
        val task = tasks.findById(taskId) ?: return Result.failure(DomainError.TaskNotFound(taskId))
        if (task.id == parentId || !task.isOpen) return Result.failure(DomainError.SubTaskNotAllowed(parentId))
        if (task.parentId == parentId) return Result.success(Unit)
        if (tasks.subTasksOf(taskId).isNotEmpty()) return Result.failure(DomainError.HasSubTasks(taskId))
        val last = tasks.subTasksOf(parentId).maxOfOrNull { it.position }?.plus(1) ?: 0
        tasks.upsert(task.copy(parentId = parentId, categoryId = parent.categoryId, position = last))
        return Result.success(Unit)
    }
}

/** A sub task leaves its parent and goes just below it, in the same category (a drag to the left). */
class MoveOutOfParent @Inject constructor(
    private val tasks: TaskRepository,
) {
    suspend operator fun invoke(taskId: TaskId): Result<Unit> {
        val task = tasks.findById(taskId) ?: return Result.failure(DomainError.TaskNotFound(taskId))
        val parentId = task.parentId ?: return Result.success(Unit)
        if (task.isArchived) return Result.failure(DomainError.TaskArchived(taskId))
        val siblings = tasks.observeAll().first()
            .filter { it.isOpen && !it.isSubTask && it.categoryId == task.categoryId }
            .sortedWith(ObserveTaskList.comparatorFor(TaskOrder.MY_ORDER))
        val below = siblings.indexOfFirst { it.id == parentId } + 1 // 0 if the parent is gone: on top
        val ordered = siblings.toMutableList().apply { add(below, task.copy(parentId = null)) }
        tasks.upsertAll(ordered.mapIndexed { index, it -> it.copy(position = index.toLong()) })
        return Result.success(Unit)
    }
}

/**
 * The tasks [taskId] could go under: open, top-level, other than itself. Sorted by category name
 * (Uncategorized last), then in My order, as on Home.
 */
class ParentChoices @Inject constructor(
    private val tasks: TaskRepository,
    private val categories: CategoryRepository,
) {
    suspend operator fun invoke(taskId: TaskId): List<Task> =
        tasks.observeAll().first()
            .filter { it.isOpen && !it.isSubTask && it.id != taskId }
            .sortedWith(ObserveTaskList.byCategoryName(categories.observeAll().first()).then(ObserveTaskList.comparatorFor(TaskOrder.MY_ORDER)))
}
