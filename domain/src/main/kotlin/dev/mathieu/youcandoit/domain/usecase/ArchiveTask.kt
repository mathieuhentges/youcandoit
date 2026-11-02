package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.Task
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.model.TaskList
import dev.mathieu.youcandoit.domain.repository.TaskRepository
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Swipe-to-archive on a single done task. */
class ArchiveTask @Inject constructor(
    private val tasks: TaskRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(id: TaskId): Result<Task> {
        val task = tasks.findById(id) ?: return Result.failure(DomainError.TaskNotFound(id))
        if (task.isArchived) return Result.success(task)
        if (task.isOpen) return Result.failure(DomainError.TaskNotDone(id))

        val archived = task.archive(clock.instant())
        tasks.upsert(archived)
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

/** Brings an archived task back to its list as done. */
class RestoreTask @Inject constructor(
    private val tasks: TaskRepository,
) {
    suspend operator fun invoke(id: TaskId): Result<Task> {
        val task = tasks.findById(id) ?: return Result.failure(DomainError.TaskNotFound(id))
        if (!task.isArchived) return Result.success(task)

        val restored = task.restore()
        tasks.upsert(restored)
        return Result.success(restored)
    }
}
