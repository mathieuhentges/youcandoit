package dev.mathieu.youcandoit.data.local

import dev.mathieu.youcandoit.domain.model.Category
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.Priority
import dev.mathieu.youcandoit.domain.model.Reminder
import dev.mathieu.youcandoit.domain.model.Task
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.model.TaskStatus
import java.time.Duration
import java.time.Instant

internal object ReminderColumns {
    const val NONE = "NONE"
    const val AT_DEADLINE = "AT_DEADLINE"
    const val BEFORE_DEADLINE = "BEFORE_DEADLINE"
}

internal fun Task.toEntity(): TaskEntity {
    val (reminderType, reminderOffset) = when (val r = reminder) {
        Reminder.None -> ReminderColumns.NONE to null
        Reminder.AtDeadline -> ReminderColumns.AT_DEADLINE to null
        is Reminder.BeforeDeadline -> ReminderColumns.BEFORE_DEADLINE to r.offset.seconds
    }
    return TaskEntity(
        id = id.value,
        title = title,
        description = description,
        priority = priority.level,
        categoryId = categoryId?.value,
        deadline = deadline?.toEpochMilli(),
        reminderType = reminderType,
        reminderOffsetSeconds = reminderOffset,
        createdAt = createdAt.toEpochMilli(),
        doneAt = doneAt?.toEpochMilli(),
        archivedAt = archivedAt?.toEpochMilli(),
        position = position,
        parentId = parentId?.value,
    )
}

internal fun TaskEntity.toDomain(): Task = Task(
    id = TaskId(id),
    title = title,
    description = description,
    priority = Priority.fromLevel(priority),
    categoryId = categoryId?.let { CategoryId(it) },
    deadline = deadline?.let { Instant.ofEpochMilli(it) },
    reminder = reminderFrom(reminderType, reminderOffsetSeconds),
    createdAt = Instant.ofEpochMilli(createdAt),
    status = statusFrom(doneAt, archivedAt),
    position = position,
    parentId = parentId?.let { TaskId(it) },
)

internal fun statusFrom(doneAt: Long?, archivedAt: Long?): TaskStatus = when {
    archivedAt != null -> TaskStatus.Archived(
        doneAt = Instant.ofEpochMilli(requireNotNull(doneAt) { "Archived task without a done time" }),
        archivedAt = Instant.ofEpochMilli(archivedAt),
    )
    doneAt != null -> TaskStatus.Done(Instant.ofEpochMilli(doneAt))
    else -> TaskStatus.Open
}

internal fun reminderFrom(type: String, offsetSeconds: Long?): Reminder = when (type) {
    ReminderColumns.NONE -> Reminder.None
    ReminderColumns.AT_DEADLINE -> Reminder.AtDeadline
    ReminderColumns.BEFORE_DEADLINE -> Reminder.BeforeDeadline(
        Duration.ofSeconds(requireNotNull(offsetSeconds) { "BEFORE_DEADLINE reminder without an offset" }),
    )
    else -> error("Unknown reminder type in database: $type")
}

internal fun Category.toEntity() = CategoryEntity(id = id.value, name = name, createdAt = createdAt.toEpochMilli())

internal fun CategoryEntity.toDomain() = Category(CategoryId(id), name, Instant.ofEpochMilli(createdAt))
