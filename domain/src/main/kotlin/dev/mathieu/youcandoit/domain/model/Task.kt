package dev.mathieu.youcandoit.domain.model

import java.time.Instant

data class Task(
    val id: TaskId,
    val title: String,
    val description: String,
    val priority: Priority,
    val categoryId: CategoryId?,
    val deadline: Instant?,
    val reminder: Reminder,
    val createdAt: Instant,
    val status: TaskStatus,
    /** Where the user dragged it: lower comes first. Only compared among siblings (same category, same parent). */
    val position: Long,
    /** Set for a sub task: one level only, always in its parent's category. */
    val parentId: TaskId?,
) {
    val isSubTask: Boolean get() = parentId != null

    val isOpen: Boolean get() = status == TaskStatus.Open
    val isDone: Boolean get() = status is TaskStatus.Done
    val isArchived: Boolean get() = status is TaskStatus.Archived

    /** When the task was ticked; kept through archiving. Null while open. */
    val doneAt: Instant?
        get() = when (val s = status) {
            TaskStatus.Open -> null
            is TaskStatus.Done -> s.doneAt
            is TaskStatus.Archived -> s.doneAt
        }

    val archivedAt: Instant? get() = (status as? TaskStatus.Archived)?.archivedAt

    fun markDone(at: Instant): Task {
        check(isOpen) { "Only an open task can be marked done (task ${id.value} is $status)" }
        return copy(status = TaskStatus.Done(at))
    }

    fun markNotDone(): Task {
        check(isDone) { "Only a done task can be unticked (task ${id.value} is $status)" }
        return copy(status = TaskStatus.Open)
    }

    fun archive(at: Instant): Task {
        val s = status
        check(s is TaskStatus.Done) { "Only a done task can be archived (task ${id.value} is $status)" }
        return copy(status = TaskStatus.Archived(doneAt = s.doneAt, archivedAt = at))
    }

    /** Back to its list as a done task, keeping the original done time. */
    fun restore(): Task {
        val s = status
        check(s is TaskStatus.Archived) { "Only an archived task can be restored (task ${id.value} is $status)" }
        return copy(status = TaskStatus.Done(s.doneAt))
    }

    fun isOverdue(now: Instant): Boolean = isOpen && deadline != null && now.isAfter(deadline)
}

/**
 * Open → Done (ticked, still shown at the bottom of its list) → Archived (moved to History).
 * Done can go back to Open; Archived can be restored to Done.
 */
sealed interface TaskStatus {
    data object Open : TaskStatus
    data class Done(val doneAt: Instant) : TaskStatus
    data class Archived(val doneAt: Instant, val archivedAt: Instant) : TaskStatus
}
