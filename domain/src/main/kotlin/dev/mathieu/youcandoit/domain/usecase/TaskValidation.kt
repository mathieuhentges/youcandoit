package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.Reminder
import dev.mathieu.youcandoit.domain.repository.CategoryRepository
import java.time.Instant

/**
 * Shared rules for creating and editing a task. [previousDeadline] and [previousReminder] let a task
 * whose deadline or reminder time has passed be edited (e.g. its title) without forcing new ones.
 */
internal suspend fun validateTaskInput(
    title: String,
    deadline: Instant?,
    reminder: Reminder,
    categoryId: CategoryId?,
    now: Instant,
    categories: CategoryRepository,
    previousDeadline: Instant? = null,
    previousReminder: Reminder? = null,
): DomainError? {
    if (title.isBlank()) return DomainError.BlankTitle()
    if (deadline != null && deadline != previousDeadline && !deadline.isAfter(now)) {
        return DomainError.DeadlineNotInFuture(deadline)
    }
    if (reminder.needsDeadline && deadline == null) return DomainError.ReminderWithoutDeadline()
    // As for the deadline: a chosen time that has passed can stay on an edited task, but not be newly set.
    if (reminder is Reminder.At && reminder != previousReminder && !reminder.at.isAfter(now)) {
        return DomainError.ReminderNotInFuture(reminder.at)
    }
    if (categoryId != null && categories.findById(categoryId) == null) {
        return DomainError.CategoryNotFound(categoryId)
    }
    return null
}
