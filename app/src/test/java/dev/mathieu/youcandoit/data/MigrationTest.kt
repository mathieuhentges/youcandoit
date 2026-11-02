package dev.mathieu.youcandoit.data

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.mathieu.youcandoit.data.local.TodoDatabase
import java.io.File
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Opens a database written by version 1 of the app, as on a phone that already has tasks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    /** Creates the version 1 tables from the exported schema, so the test can't drift from what shipped. */
    private fun createVersion1(insert: SQLiteDatabase.() -> Unit) {
        val schema = JSONObject(File("schemas/${TodoDatabase::class.qualifiedName}/1.json").readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.version = 1
            db.insert()
        }
    }

    private fun SQLiteDatabase.task(id: String, priority: Int, category: String? = null, deadline: Long? = null, createdAt: Long = 0) {
        execSQL(
            "INSERT INTO tasks (id, title, description, priority, category_id, deadline, reminder_type, created_at) " +
                "VALUES (?, ?, '', ?, ?, ?, 'NONE', ?)",
            arrayOf(id, "Task $id", priority, category, deadline, createdAt),
        )
    }

    @Test
    fun `existing tasks keep the order the old automatic sort gave them, per category`() = runTest {
        createVersion1 {
            execSQL("INSERT INTO categories (id, name, created_at) VALUES ('work', 'Work', 0)")
            task("low", priority = 2)
            task("urgent", priority = 5)
            task("high-later", priority = 4, deadline = 2_000)
            task("high-sooner", priority = 4, deadline = 1_000)
            task("high-none-old", priority = 4, createdAt = 1)
            task("high-none-new", priority = 4, createdAt = 2)
            task("work-low", priority = 1, category = "work")
            task("work-urgent", priority = 5, category = "work")
        }

        val db = Room.databaseBuilder(context, TodoDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            val byCategory = db.taskDao().observeAll().first()
                .groupBy { it.categoryId }
                .mapValues { (_, rows) -> rows.sortedBy { it.position }.map { it.id } }

            assertEquals(
                listOf("urgent", "high-sooner", "high-later", "high-none-old", "high-none-new", "low"),
                byCategory[null],
            )
            assertEquals(listOf("work-urgent", "work-low"), byCategory["work"])
            assertEquals(listOf(0L, 1L), db.taskDao().observeAll().first().filter { it.categoryId == "work" }.map { it.position }.sorted())
        } finally {
            db.close()
        }
    }
}
