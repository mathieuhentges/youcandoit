package dev.mathieu.youcandoit.domain.repository

import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.Task
import dev.mathieu.youcandoit.domain.model.TaskId
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    suspend fun upsert(task: Task)

    /** Writes all tasks atomically. */
    suspend fun upsertAll(tasks: List<Task>)
    suspend fun findById(id: TaskId): Task?
    /** Deletes the task and its sub tasks, atomically. */
    suspend fun delete(id: TaskId)

    /** [delete] for several tasks, all in one go. */
    suspend fun deleteAll(ids: Set<TaskId>)

    /** Moves every task in [categoryId] to "Uncategorized" (categoryId = null). */
    suspend fun unassignCategory(categoryId: CategoryId)
    fun observeAll(): Flow<List<Task>>
}
