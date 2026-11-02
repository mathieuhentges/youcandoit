package dev.mathieu.youcandoit

import dev.mathieu.youcandoit.domain.model.TaskId

/** URIs other parts of the app (notifications) use to open a screen. */
object DeepLinks {
    const val TASK_ID_ARG = "taskId"
    const val TASK_PATTERN = "todo://task/{$TASK_ID_ARG}"

    fun task(id: TaskId): String = "todo://task/${id.value}"
}
