package dev.mathieu.youcandoit.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// @Upsert rather than @Insert(REPLACE): REPLACE deletes then re-inserts the row, which would
// fire ON DELETE SET NULL and silently detach every task from a category on rename.

@Dao
interface TaskDao {
    @Upsert
    suspend fun upsert(task: TaskEntity)

    /** Runs in a single transaction. */
    @Upsert
    suspend fun upsertAll(tasks: List<TaskEntity>)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun findById(id: String): TaskEntity?

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE tasks SET category_id = NULL WHERE category_id = :categoryId")
    suspend fun unassignCategory(categoryId: String)

    @Query("SELECT * FROM tasks")
    fun observeAll(): Flow<List<TaskEntity>>
}

@Dao
interface CategoryDao {
    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun findById(id: String): CategoryEntity?

    @Query("SELECT * FROM categories")
    suspend fun all(): List<CategoryEntity>

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM categories ORDER BY name")
    fun observeAll(): Flow<List<CategoryEntity>>
}
