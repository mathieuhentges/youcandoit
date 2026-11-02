package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.model.ProgressStats
import dev.mathieu.youcandoit.domain.model.StatsPeriod
import dev.mathieu.youcandoit.domain.repository.TaskRepository
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveProgressStats @Inject constructor(
    private val tasks: TaskRepository,
    private val clock: Clock,
) {
    operator fun invoke(period: StatsPeriod): Flow<ProgressStats> =
        tasks.observeAll().map { all ->
            val now = clock.instant()
            val since = period.length?.let { now.minus(it) }
            val open = all.filter { it.isOpen }
            // Done and archived alike: progress counts from when the task was done.
            val done = all.filter { task ->
                task.doneAt?.let { since == null || !it.isBefore(since) } == true
            }
            ProgressStats(
                openCount = open.size,
                overdueCount = open.count { it.isOverdue(now) },
                doneCount = done.size,
                doneByPriority = done.groupingBy { it.priority }.eachCount(),
                doneByCategory = done.groupingBy { it.categoryId }.eachCount(),
            )
        }
}
