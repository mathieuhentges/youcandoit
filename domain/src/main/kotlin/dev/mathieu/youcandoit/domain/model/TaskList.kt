package dev.mathieu.youcandoit.domain.model

/** Which tasks a screen shows: Home (all), one category, or the tasks without one. */
sealed interface TaskList {
    data object All : TaskList
    data object Uncategorized : TaskList
    data class InCategory(val id: CategoryId) : TaskList

    fun contains(task: Task): Boolean = when (this) {
        All -> true
        Uncategorized -> task.categoryId == null
        is InCategory -> task.categoryId == id
    }
}

/** How a list orders its open tasks. Done tasks follow a [DoneOrder]. */
enum class TaskOrder {
    /** As the user dragged them. */
    MY_ORDER,

    /** Most important first, then nearest deadline (none last), then oldest. */
    PRIORITY,

    /** Nearest deadline first (none last), then most important, then oldest. */
    DEADLINE,

    /** Newest first, then most important. */
    DATE_ADDED,
}

/** How a list orders its done (not yet archived) tasks. Ties go to the most recently done. */
enum class DoneOrder {
    RECENTLY_DONE,

    /** Most important first. */
    PRIORITY,

    /** Categories A to Z, Uncategorized last. Only differs from [RECENTLY_DONE] on All tasks. */
    CATEGORY,
}

/** How History orders archived tasks. Ties go to the most recently archived. */
enum class HistoryOrder {
    RECENTLY_ARCHIVED,
    RECENTLY_DONE,

    /** Most important first. */
    PRIORITY,

    /** Categories A to Z, Uncategorized last. */
    CATEGORY,
}

/**
 * A list screen's content: open tasks first, done tasks in their own section below. Both hold
 * top-level tasks only; [subTasks] has each parent's sub tasks (open ones first, then done).
 */
data class TaskListView(
    val open: List<Task>,
    val done: List<Task>,
    val subTasks: Map<TaskId, List<Task>> = emptyMap(),
)

data class ListCounts(val open: Int, val overdue: Int, val done: Int) {
    companion object {
        val EMPTY = ListCounts(open = 0, overdue = 0, done = 0)
    }
}

/** [openByPriority]: how many open tasks of each priority, every priority listed (0 included). */
data class CategorySummary(
    val category: Category,
    val counts: ListCounts,
    val openByPriority: Map<Priority, Int> = Priority.entries.associateWith { 0 },
)

/** Everything the Lists screen shows. Archived tasks are not counted. */
data class ListsOverview(
    val all: ListCounts,
    val categories: List<CategorySummary>,
    val uncategorized: ListCounts,
    val uncategorizedByPriority: Map<Priority, Int> = Priority.entries.associateWith { 0 },
)
