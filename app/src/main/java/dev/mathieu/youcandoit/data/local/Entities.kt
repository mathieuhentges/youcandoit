package dev.mathieu.youcandoit.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        // Deleting a parent deletes its sub tasks. The app does it itself first (to cancel their
        // reminders); this keeps the database consistent regardless.
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("category_id"), Index("parent_id")],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    /** Priority level 1..5. */
    val priority: Int,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    /** Epoch millis. */
    val deadline: Long?,
    /** One of [ReminderColumns]; stored values must never be renamed. */
    @ColumnInfo(name = "reminder_type") val reminderType: String,
    @ColumnInfo(name = "reminder_offset_seconds") val reminderOffsetSeconds: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** Epoch millis; null while open. Kept when the task is archived. */
    @ColumnInfo(name = "done_at") val doneAt: Long?,
    /** Epoch millis; non-null only for archived tasks (which always have [doneAt]). */
    @ColumnInfo(name = "archived_at") val archivedAt: Long?,
    /** Drag order within the task's category; lower first. Added in version 2. */
    @ColumnInfo(defaultValue = "0") val position: Long,
    /** Set for a sub task. Added in version 3. */
    @ColumnInfo(name = "parent_id") val parentId: String?,
)
