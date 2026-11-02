package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.Reminder
import dev.mathieu.youcandoit.domain.repository.CategoryRepository
import java.time.Instant

/**
 * Shared rules for creating and editing a task. [previousDeadline] lets an overdue
 * task be edited (e.g. its title) without forcing a new deadline.
 */
internal suspend fun validateTaskInput(
    title: String,
    deadline: Instant?,
    reminder: Reminder,
    categoryId: CategoryId?,
    now: Instant,
    categories: CategoryRepository,
    previousDeadline: Instant? = null,
): DomainError? {
    if (title.isBlank()) return DomainError.BlankTitle()
    if (deadline != null && deadline != previousDeadline && !deadline.isAfter(now)) {
        return DomainError.DeadlineNotInFuture(deadline)
    }
    if (reminder != Reminder.None && deadline == null) return DomainError.ReminderWithoutDeadline()
    if (categoryId != null && categories.findById(categoryId) == null) {
        return DomainError.CategoryNotFound(categoryId)
    }
    return null
}
