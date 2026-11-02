package dev.mathieu.youcandoit.settings

import dev.mathieu.youcandoit.domain.model.DoneOrder
import dev.mathieu.youcandoit.domain.model.TaskOrder

/**
 * The sort menus' choices, shared by every list. Drag order is kept per category, so "My order"
 * always shows category headers; [groupByCategory] only applies to the other orders.
 * [doneOrder] is the Done section's own menu.
 */
data class ListSort(
    val order: TaskOrder = TaskOrder.MY_ORDER,
    val groupByCategory: Boolean = true,
    val doneOrder: DoneOrder = DoneOrder.RECENTLY_DONE,
) {
    val isGrouped: Boolean get() = order == TaskOrder.MY_ORDER || groupByCategory
    val canDrag: Boolean get() = order == TaskOrder.MY_ORDER

    companion object {
        /** What's stored: fixed strings, safe from renaming the enum. */
        internal fun keyOf(order: TaskOrder): String = when (order) {
            TaskOrder.MY_ORDER -> "my_order"
            TaskOrder.PRIORITY -> "priority"
            TaskOrder.DEADLINE -> "deadline"
            TaskOrder.DATE_ADDED -> "date_added"
        }

        internal fun orderFromKey(key: String?): TaskOrder = TaskOrder.entries.firstOrNull { keyOf(it) == key } ?: TaskOrder.MY_ORDER

        internal fun keyOf(order: DoneOrder): String = when (order) {
            DoneOrder.RECENTLY_DONE -> "recently_done"
            DoneOrder.PRIORITY -> "priority"
            DoneOrder.CATEGORY -> "category"
        }

        internal fun doneOrderFromKey(key: String?): DoneOrder =
            DoneOrder.entries.firstOrNull { keyOf(it) == key } ?: DoneOrder.RECENTLY_DONE
    }
}
