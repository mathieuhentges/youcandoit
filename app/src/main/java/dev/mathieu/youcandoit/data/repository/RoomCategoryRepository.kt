package dev.mathieu.youcandoit.data.repository

import dev.mathieu.youcandoit.data.local.CategoryDao
import dev.mathieu.youcandoit.data.local.toDomain
import dev.mathieu.youcandoit.data.local.toEntity
import dev.mathieu.youcandoit.domain.model.Category
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.repository.CategoryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomCategoryRepository @Inject constructor(
    private val dao: CategoryDao,
) : CategoryRepository {
    override suspend fun upsert(category: Category) = dao.upsert(category.toEntity())
    override suspend fun findById(id: CategoryId): Category? = dao.findById(id.value)?.toDomain()
    /**
     * Compared in Kotlin, not SQL: SQLite's NOCASE only folds A–Z, so "Épicerie" and "épicerie"
     * would count as different names. A personal list has few categories, so loading them is cheap.
     */
    override suspend fun findByName(name: String): Category? =
        dao.all().firstOrNull { it.name.equals(name, ignoreCase = true) }?.toDomain()
    override suspend fun delete(id: CategoryId) = dao.delete(id.value)
    override fun observeAll(): Flow<List<Category>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }
}
