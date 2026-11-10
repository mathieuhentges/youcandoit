package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.model.Category
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.Task
import dev.mathieu.youcandoit.domain.reminder.ReminderSync
import dev.mathieu.youcandoit.domain.repository.CategoryRepository
import dev.mathieu.youcandoit.domain.repository.TaskRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Everything the user made: categories and every task, archived ones included. Settings aren't part of it. */
data class Backup(val categories: List<Category>, val tasks: List<Task>)

class ExportBackup @Inject constructor(
    private val tasks: TaskRepository,
    private val categories: CategoryRepository,
) {
    suspend operator fun invoke(): Backup = Backup(categories.observeAll().first(), tasks.observeAll().first())
}

/** [tasks] read from the file; [newCategories] of them weren't here yet (by id or name). */
data class ImportResult(val tasks: Int, val newCategories: Int)

/**
 * Adds a [Backup] to what's already here, never deleting anything: a task with the same id is
 * replaced by the file's version, a category with the same id or name (ignoring case) is reused.
 * Reminders are scheduled again, for the tasks that still have one to come.
 */
class ImportBackup @Inject constructor(
    private val tasks: TaskRepository,
    private val categories: CategoryRepository,
    private val reminders: ReminderSync,
) {
    suspend operator fun invoke(backup: Backup): ImportResult {
        val existing = categories.observeAll().first()
        val byId = existing.associateBy { it.id }
        val byName = existing.associateBy { it.name.lowercase() }
        val categoryIds = mutableMapOf<CategoryId, CategoryId>()
        var added = 0
        backup.categories.forEach { category ->
            val same = byId[category.id] ?: byName[category.name.lowercase()]
            if (same != null) {
                categoryIds[category.id] = same.id
            } else {
                categories.upsert(category)
                categoryIds[category.id] = category.id
                added++
            }
        }

        val known = backup.tasks.map { it.id }.toSet() + tasks.observeAll().first().map { it.id }
        val imported = backup.tasks.map { task ->
            task.copy(
                // A category missing from the file means Uncategorized, as when one is deleted.
                categoryId = task.categoryId?.let { categoryIds[it] },
                // A sub task whose parent isn't anywhere becomes a main task rather than disappearing.
                parentId = task.parentId?.takeIf { it in known },
            )
        }
        tasks.upsertAll(imported)
        imported.forEach { reminders.sync(it) }
        return ImportResult(tasks = imported.size, newCategories = added)
    }
}
