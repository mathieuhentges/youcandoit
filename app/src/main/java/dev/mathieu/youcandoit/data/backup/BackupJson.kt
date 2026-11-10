package dev.mathieu.youcandoit.data.backup

import dev.mathieu.youcandoit.domain.model.Category
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.Priority
import dev.mathieu.youcandoit.domain.model.Reminder
import dev.mathieu.youcandoit.domain.model.Task
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.model.TaskStatus
import dev.mathieu.youcandoit.domain.usecase.Backup
import java.time.Duration
import java.time.Instant
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** The file is something else, or from a newer version of the app than this one. */
class NotABackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The export file: plain JSON, times in epoch milliseconds, priority by its level. Its own stable
 * names rather than the database's, so the file stays readable whatever the database becomes.
 */
object BackupJson {
    const val FORMAT = "you-can-do-it-backup"
    const val VERSION = 1

    fun encode(backup: Backup, exportedAt: Instant): String = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("exportedAt", exportedAt.toEpochMilli())
        .put("categories", JSONArray(backup.categories.map { it.toJson() }))
        .put("tasks", JSONArray(backup.tasks.map { it.toJson() }))
        .toString(2)

    fun decode(text: String): Backup = try {
        val root = JSONObject(text)
        if (root.optString("format") != FORMAT) throw NotABackupException("Not a backup file")
        val version = root.getInt("version")
        if (version > VERSION) throw NotABackupException("Backup version $version is newer than $VERSION")
        Backup(
            categories = root.getJSONArray("categories").objects().map { it.toCategory() },
            tasks = root.getJSONArray("tasks").objects().map { it.toTask() },
        )
    } catch (e: JSONException) {
        throw NotABackupException("Unreadable backup file", e)
    } catch (e: IllegalArgumentException) {
        throw NotABackupException("Invalid value in backup file", e)
    }

    private fun Category.toJson() = JSONObject()
        .put("id", id.value)
        .put("name", name)
        .put("createdAt", createdAt.toEpochMilli())

    private fun JSONObject.toCategory() = Category(
        id = CategoryId(getString("id")),
        name = getString("name"),
        createdAt = requiredInstant("createdAt"),
    )

    private fun Task.toJson() = JSONObject()
        .put("id", id.value)
        .put("title", title)
        .put("description", description)
        .put("priority", priority.level)
        .putOpt("categoryId", categoryId?.value)
        .putOpt("parentId", parentId?.value)
        .put("position", position)
        .putOpt("deadline", deadline?.toEpochMilli())
        .put("reminder", reminder.toJson())
        .put("createdAt", createdAt.toEpochMilli())
        .putOpt("doneAt", doneAt?.toEpochMilli())
        .putOpt("archivedAt", archivedAt?.toEpochMilli())

    private fun JSONObject.toTask(): Task {
        val doneAt = instant("doneAt")
        val archivedAt = instant("archivedAt")
        val level = getInt("priority")
        return Task(
            id = TaskId(getString("id")),
            title = getString("title"),
            description = optString("description"),
            priority = requireNotNull(Priority.entries.firstOrNull { it.level == level }) { "Unknown priority $level" },
            categoryId = string("categoryId")?.let { CategoryId(it) },
            deadline = instant("deadline"),
            reminder = getJSONObject("reminder").toReminder(),
            createdAt = requiredInstant("createdAt"),
            status = when {
                archivedAt != null -> TaskStatus.Archived(requireNotNull(doneAt) { "Archived without a done time" }, archivedAt)
                doneAt != null -> TaskStatus.Done(doneAt)
                else -> TaskStatus.Open
            },
            position = optLong("position"),
            parentId = string("parentId")?.let { TaskId(it) },
        )
    }

    private fun Reminder.toJson(): JSONObject = when (this) {
        Reminder.None -> JSONObject().put("type", "none")
        Reminder.AtDeadline -> JSONObject().put("type", "at_deadline")
        is Reminder.BeforeDeadline -> JSONObject().put("type", "before_deadline").put("offsetSeconds", offset.seconds)
        is Reminder.At -> JSONObject().put("type", "at").put("at", at.toEpochMilli())
    }

    private fun JSONObject.toReminder(): Reminder = when (val type = getString("type")) {
        "none" -> Reminder.None
        "at_deadline" -> Reminder.AtDeadline
        "before_deadline" -> Reminder.BeforeDeadline(Duration.ofSeconds(getLong("offsetSeconds")))
        "at" -> Reminder.At(requiredInstant("at"))
        else -> throw IllegalArgumentException("Unknown reminder type $type")
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun JSONObject.string(key: String): String? = if (isNull(key)) null else getString(key)

    private fun JSONObject.instant(key: String): Instant? = if (isNull(key)) null else Instant.ofEpochMilli(getLong(key))

    private fun JSONObject.requiredInstant(key: String): Instant = Instant.ofEpochMilli(getLong(key))
}
