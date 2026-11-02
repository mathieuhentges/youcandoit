package dev.mathieu.youcandoit.domain.repository

import dev.mathieu.youcandoit.domain.model.Category
import dev.mathieu.youcandoit.domain.model.CategoryId
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    suspend fun upsert(category: Category)
    suspend fun findById(id: CategoryId): Category?

    /** Case-insensitive lookup. */
    suspend fun findByName(name: String): Category?
    suspend fun delete(id: CategoryId)
    fun observeAll(): Flow<List<Category>>
}
