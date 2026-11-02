package dev.mathieu.youcandoit.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TaskEntity::class, CategoryEntity::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = TodoDatabase.SeedPositions::class),
        // Version 3 adds sub tasks (parent_id); every existing task is top-level.
        AutoMigration(from = 2, to = 3),
    ],
)
abstract class TodoDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun categoryDao(): CategoryDao

    /**
     * Version 2 adds drag order. Existing tasks keep the order they had under the old automatic
     * sort: highest priority, then nearest deadline (none last), then oldest; per category.
     */
    class SeedPositions : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL(SEED_POSITIONS)
        }
    }

    companion object {
        const val NAME = "todo.db"

        // A row's position = how many tasks in its category sort before it. A correlated
        // subquery rather than ROW_NUMBER(): window functions need SQLite 3.25 (API 30), minSdk is 26.
        internal val SEED_POSITIONS = """
            UPDATE tasks SET position = (
                SELECT COUNT(*) FROM tasks AS o
                WHERE o.category_id IS tasks.category_id AND (
                    o.priority > tasks.priority OR (o.priority = tasks.priority AND (
                        IFNULL(o.deadline, ${Long.MAX_VALUE}) < IFNULL(tasks.deadline, ${Long.MAX_VALUE}) OR (
                            IFNULL(o.deadline, ${Long.MAX_VALUE}) = IFNULL(tasks.deadline, ${Long.MAX_VALUE}) AND (
                                o.created_at < tasks.created_at OR (o.created_at = tasks.created_at AND o.id < tasks.id)
                            )
                        )
                    ))
                )
            )
        """.trimIndent()
    }
}
