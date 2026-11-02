package dev.mathieu.youcandoit.ui.navigation

import dev.mathieu.youcandoit.DeepLinks
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.model.TaskList

object NavArgs {
    const val LIST = "list"
    const val TASK_ID = DeepLinks.TASK_ID_ARG // same name, so the deep link fills the editor's argument
}

object Routes {
    const val HOME = "home"
    const val LISTS = "lists"
    const val HISTORY = "history"
    const val LIST = "list/{${NavArgs.LIST}}"
    const val NEW_TASK = "task/new?${NavArgs.LIST}={${NavArgs.LIST}}"
    const val EDIT_TASK = "task/edit/{${NavArgs.TASK_ID}}"

    fun list(list: TaskList) = "list/${ListKey.encode(list)}"
    fun newTask(list: TaskList) = "task/new?${NavArgs.LIST}=${ListKey.encode(list)}"
    fun editTask(id: TaskId) = "task/edit/${id.value}"
}

/** Encodes a [TaskList] as a route-safe string and back. */
object ListKey {
    private const val ALL = "all"
    private const val NONE = "none"
    private const val CATEGORY_PREFIX = "c_"

    fun encode(list: TaskList): String = when (list) {
        TaskList.All -> ALL
        TaskList.Uncategorized -> NONE
        is TaskList.InCategory -> CATEGORY_PREFIX + list.id.value
    }

    fun decode(key: String?): TaskList = when {
        key == null || key == ALL -> TaskList.All
        key == NONE -> TaskList.Uncategorized
        key.startsWith(CATEGORY_PREFIX) -> TaskList.InCategory(CategoryId(key.removePrefix(CATEGORY_PREFIX)))
        else -> TaskList.All
    }
}
