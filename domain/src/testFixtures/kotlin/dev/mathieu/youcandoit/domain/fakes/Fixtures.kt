package dev.mathieu.youcandoit.domain.fakes

import dev.mathieu.youcandoit.domain.model.Category
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.Priority
import dev.mathieu.youcandoit.domain.model.Reminder
import dev.mathieu.youcandoit.domain.model.Task
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.model.TaskStatus
import java.time.Instant

val NOW: Instant = Instant.parse("2026-11-01T12:00:00Z")

fun aTask(
    id: String,
    priority: Priority = Priority.MEDIUM,
    deadline: Instant? = null,
    reminder: Reminder = Reminder.None,
    createdAt: Instant = NOW,
    categoryId: CategoryId? = null,
    status: TaskStatus = TaskStatus.Open,
    position: Long = 0,
    parentId: TaskId? = null,
) = Task(
    id = TaskId(id),
    title = "Task $id",
    description = "",
    priority = priority,
    categoryId = categoryId,
    deadline = deadline,
    reminder = reminder,
    createdAt = createdAt,
    status = status,
    position = position,
    parentId = parentId,
)

fun aCategory(id: String, name: String = id.replaceFirstChar { it.uppercase() }) =
    Category(CategoryId(id), name, NOW)
