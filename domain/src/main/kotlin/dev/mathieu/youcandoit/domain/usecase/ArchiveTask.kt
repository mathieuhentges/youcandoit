package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.Task
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.model.TaskList
import dev.mathieu.youcandoit.domain.repository.TaskRepository
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Swipe-to-archive on a single done task, with its done sub tasks. */
class ArchiveTask @Inject constructor(
    private val tasks: TaskRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(id: TaskId): Result<Task> {
        val task = tasks.findById(id) ?: return Result.failure(DomainError.TaskNotFound(id))
        if (task.isArchived) return Result.success(task)
        if (task.isOpen) return Result.failure(DomainError.TaskNotDone(id))

        val now = clock.instant()
        val archived = task.archive(now)
        val children = tasks.subTasksOf(id).filter { it.isDone }.map { it.archive(now) }
        tasks.upsertAll(listOf(archived) + children)
        return Result.success(archived)
    }
}

/** The "Archive done" button: archives every done task in [TaskList] at once. Returns how many. */
class ArchiveDoneTasks @Inject constructor(
    private val tasks: TaskRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(list: TaskList): Int {
        val now = clock.instant()
        val archived = tasks.observeAll().first()
            .filter { it.isDone && list.contains(it) }
            .map { it.archive(now) }
        if (archived.isNotEmpty()) tasks.upsertAll(archived)
        return archived.size
    }
}

/**
 * Brings an archived task back to its list as done: a parent with its archived sub tasks, a sub task
 * with its archived parent (it needs one to show under).
 */
class RestoreTask @Inject constructor(
    private val tasks: TaskRepository,
) {
    suspend operator fun invoke(id: TaskId): Result<Task> {
        val task = tasks.findById(id) ?: return Result.failure(DomainError.TaskNotFound(id))
        if (!task.isArchived) return Result.success(task)

        val restored = task.restore()
        val parent = task.parentId?.let { tasks.findById(it) }?.takeIf { it.isArchived }?.restore()
        val children = if (task.isSubTask) emptyList() else tasks.subTasksOf(id).filter { it.isArchived }.map { it.restore() }
        tasks.upsertAll(listOfNotNull(restored, parent) + children)
        return Result.success(restored)
    }
}
