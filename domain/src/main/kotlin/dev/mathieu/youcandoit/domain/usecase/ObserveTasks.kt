package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.model.CategorySummary
import dev.mathieu.youcandoit.domain.model.ListCounts
import dev.mathieu.youcandoit.domain.model.ListsOverview
import dev.mathieu.youcandoit.domain.model.Task
import dev.mathieu.youcandoit.domain.model.TaskList
import dev.mathieu.youcandoit.domain.model.TaskListView
import dev.mathieu.youcandoit.domain.repository.CategoryRepository
import dev.mathieu.youcandoit.domain.repository.TaskRepository
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Home (All tasks) or one category. Open tasks in the order the user dragged them into.
 * Done tasks below them, most recently done first.
 */
class ObserveTaskList @Inject constructor(
    private val tasks: TaskRepository,
) {
    operator fun invoke(list: TaskList = TaskList.All): Flow<TaskListView> =
        tasks.observeAll().map { all ->
            val inList = all.filter { list.contains(it) }
            TaskListView(
                open = inList.filter { it.isOpen }.sortedWith(OPEN_ORDER),
                done = inList.filter { it.isDone }.sortedByDescending { it.doneAt },
            )
        }

    companion object {
        val OPEN_ORDER: Comparator<Task> =
            compareBy<Task> { it.position }
                .thenBy { it.createdAt }
                .thenBy { it.id.value }
    }
}

/** History: archived tasks, most recently archived first. */
class ObserveArchivedTasks @Inject constructor(
    private val tasks: TaskRepository,
) {
    operator fun invoke(): Flow<List<Task>> =
        tasks.observeAll().map { all ->
            all.filter { it.isArchived }.sortedByDescending { it.archivedAt }
        }
}

/** The Lists screen: counts for every category (alphabetical) and for uncategorized tasks. */
class ObserveListsOverview @Inject constructor(
    private val tasks: TaskRepository,
    private val categories: CategoryRepository,
    private val clock: Clock,
) {
    operator fun invoke(): Flow<ListsOverview> =
        combine(tasks.observeAll(), categories.observeAll()) { allTasks, allCategories ->
            val now = clock.instant()
            fun countsFor(list: TaskList): ListCounts {
                val inList = allTasks.filter { list.contains(it) }
                return ListCounts(
                    open = inList.count { it.isOpen },
                    overdue = inList.count { it.isOverdue(now) },
                    done = inList.count { it.isDone },
                )
            }
            ListsOverview(
                all = countsFor(TaskList.All),
                categories = allCategories
                    .sortedWith(categoryOrder())
                    .map { CategorySummary(it, countsFor(TaskList.InCategory(it.id))) },
                uncategorized = countsFor(TaskList.Uncategorized),
            )
        }
}
