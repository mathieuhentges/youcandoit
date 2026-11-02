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

/** A list screen's content: open tasks first, done tasks in their own section below. */
data class TaskListView(
    val open: List<Task>,
    val done: List<Task>,
)

data class ListCounts(val open: Int, val overdue: Int, val done: Int) {
    companion object {
        val EMPTY = ListCounts(open = 0, overdue = 0, done = 0)
    }
}

data class CategorySummary(val category: Category, val counts: ListCounts)

/** Everything the Lists screen shows. Archived tasks are not counted. */
data class ListsOverview(
    val all: ListCounts,
    val categories: List<CategorySummary>,
    val uncategorized: ListCounts,
)
