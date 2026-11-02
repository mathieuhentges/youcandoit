package dev.mathieu.youcandoit.data.repository

import dev.mathieu.youcandoit.data.local.TaskDao
import dev.mathieu.youcandoit.data.local.toDomain
import dev.mathieu.youcandoit.data.local.toEntity
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.Task
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.repository.TaskRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTaskRepository @Inject constructor(
    private val dao: TaskDao,
) : TaskRepository {
    override suspend fun upsert(task: Task) = dao.upsert(task.toEntity())
    override suspend fun upsertAll(tasks: List<Task>) = dao.upsertAll(tasks.map { it.toEntity() })
    override suspend fun findById(id: TaskId): Task? = dao.findById(id.value)?.toDomain()
    override suspend fun delete(id: TaskId) = dao.delete(id.value)
    override suspend fun deleteAll(ids: Set<TaskId>) = dao.deleteAll(ids.map { it.value })
    override suspend fun unassignCategory(categoryId: CategoryId) = dao.unassignCategory(categoryId.value)
    override fun observeAll(): Flow<List<Task>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }
}
