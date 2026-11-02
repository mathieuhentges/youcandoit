package dev.mathieu.youcandoit.domain.model

import java.time.Instant

sealed class DomainError(message: String) : Exception(message) {
    class BlankTitle : DomainError("Task title must not be blank")
    class DeadlineNotInFuture(val deadline: Instant) : DomainError("Deadline $deadline must be in the future")
    class ReminderWithoutDeadline : DomainError("A reminder requires a deadline")
    class CategoryNotFound(val id: CategoryId) : DomainError("Category ${id.value} does not exist")
    class TaskNotFound(val id: TaskId) : DomainError("Task ${id.value} does not exist")
    class TaskArchived(val id: TaskId) : DomainError("Task ${id.value} is archived; restore it first")
    class TaskNotDone(val id: TaskId) : DomainError("Task ${id.value} must be done before it can be archived")

    /** The parent is done, archived, or itself a sub task (one level only). */
    class SubTaskNotAllowed(val parentId: TaskId) : DomainError("Task ${parentId.value} can't take a sub task")
    class BlankCategoryName : DomainError("Category name must not be blank")
    class DuplicateCategoryName(val name: String) : DomainError("A category named \"$name\" already exists")
}
