package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.model.Category
import dev.mathieu.youcandoit.domain.model.CategorySummary
import dev.mathieu.youcandoit.domain.model.DoneOrder
import dev.mathieu.youcandoit.domain.model.HistoryOrder
import dev.mathieu.youcandoit.domain.model.ListCounts
import dev.mathieu.youcandoit.domain.model.ListsOverview
import dev.mathieu.youcandoit.domain.model.Task
import dev.mathieu.youcandoit.domain.model.TaskList
import dev.mathieu.youcandoit.domain.model.TaskOrder
import dev.mathieu.youcandoit.domain.model.TaskListView
import dev.mathieu.youcandoit.domain.repository.CategoryRepository
import dev.mathieu.youcandoit.domain.repository.TaskRepository
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Home (All tasks) or one category. Open tasks in the chosen [TaskOrder] (the user's drag order by
 * default). Done tasks below them in the chosen [DoneOrder] (most recently done by default).
 * Sub tasks stay under their parent: open ones in [order], then done ones most recently done first.
 */
class ObserveTaskList @Inject constructor(
    private val tasks: TaskRepository,
    private val categories: CategoryRepository,
) {
    operator fun invoke(
        list: TaskList = TaskList.All,
        order: TaskOrder = TaskOrder.MY_ORDER,
        doneOrder: DoneOrder = DoneOrder.RECENTLY_DONE,
    ): Flow<TaskListView> =
        combine(tasks.observeAll(), categories.observeAll()) { all, categories ->
            val inList = all.filter { list.contains(it) && !it.isArchived }
            val (children, topLevel) = inList.partition { it.isSubTask }
            val first: Comparator<Task> = when (doneOrder) {
                DoneOrder.RECENTLY_DONE -> compareBy { 0 }
                DoneOrder.PRIORITY -> MOST_IMPORTANT
                DoneOrder.CATEGORY -> byCategoryName(categories)
            }
            TaskListView(
                open = topLevel.filter { it.isOpen }.sortedWith(comparatorFor(order)),
                done = topLevel.filter { it.isDone }.sortedWith(first.thenByDescending { it.doneAt }.thenBy { it.id.value }),
                subTasks = children.groupBy { it.parentId!! }.mapValues { (_, siblings) ->
                    siblings.filter { it.isOpen }.sortedWith(comparatorFor(order)) +
                        siblings.filter { it.isDone }.sortedByDescending { it.doneAt }
                },
            )
        }

    companion object {
        private val OLDEST_FIRST: Comparator<Task> = compareBy<Task> { it.createdAt }.thenBy { it.id.value }
        private val MOST_IMPORTANT: Comparator<Task> = compareByDescending { it.priority.level }
        private val NEAREST_DEADLINE: Comparator<Task> = compareBy(nullsLast<Instant>()) { it.deadline }

        /** Categories A to Z (ignoring case), uncategorized tasks last. */
        internal fun byCategoryName(categories: List<Category>): Comparator<Task> {
            val names = categories.associate { it.id to it.name.lowercase() }
            return compareBy(nullsLast<String>()) { task -> task.categoryId?.let { names[it] } }
        }

        fun comparatorFor(order: TaskOrder): Comparator<Task> = when (order) {
            TaskOrder.MY_ORDER -> compareBy<Task> { it.position }.then(OLDEST_FIRST)
            TaskOrder.PRIORITY -> MOST_IMPORTANT.then(NEAREST_DEADLINE).then(OLDEST_FIRST)
            TaskOrder.DEADLINE -> NEAREST_DEADLINE.then(MOST_IMPORTANT).then(OLDEST_FIRST)
            TaskOrder.DATE_ADDED -> compareByDescending<Task> { it.createdAt }.then(MOST_IMPORTANT).thenBy { it.id.value }
        }
    }
}

/** History: archived tasks in the chosen [HistoryOrder]. */
class ObserveArchivedTasks @Inject constructor(
    private val tasks: TaskRepository,
    private val categories: CategoryRepository,
) {
    operator fun invoke(order: HistoryOrder = HistoryOrder.RECENTLY_ARCHIVED): Flow<List<Task>> =
        combine(tasks.observeAll(), categories.observeAll()) { all, categories ->
            val first: Comparator<Task> = when (order) {
                HistoryOrder.RECENTLY_ARCHIVED -> compareBy { 0 }
                HistoryOrder.RECENTLY_DONE -> compareByDescending { it.doneAt }
                HistoryOrder.PRIORITY -> compareByDescending { it.priority.level }
                HistoryOrder.CATEGORY -> ObserveTaskList.byCategoryName(categories)
            }
            all.filter { it.isArchived }
                .sortedWith(first.thenByDescending { it.archivedAt }.thenBy { it.id.value })
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
