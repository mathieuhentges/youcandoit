package dev.mathieu.youcandoit.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.mathieu.youcandoit.data.local.TodoDatabase
import dev.mathieu.youcandoit.domain.model.Category
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.Priority
import dev.mathieu.youcandoit.domain.model.Reminder
import dev.mathieu.youcandoit.domain.model.Task
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.model.TaskStatus
import java.time.Instant

val NOW: Instant = Instant.parse("2026-11-01T12:00:00Z")

fun inMemoryDatabase(): TodoDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TodoDatabase::class.java)
        .allowMainThreadQueries()
        .build()

fun aTask(
    id: String,
    categoryId: CategoryId? = null,
    priority: Priority = Priority.MEDIUM,
) = Task(
    id = TaskId(id),
    title = "Task $id",
    description = "",
    priority = priority,
    categoryId = categoryId,
    deadline = null,
    reminder = Reminder.None,
    createdAt = NOW,
    status = TaskStatus.Open,
)

fun aCategory(id: String, name: String) = Category(CategoryId(id), name, NOW)
