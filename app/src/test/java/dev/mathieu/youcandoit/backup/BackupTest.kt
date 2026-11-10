package dev.mathieu.youcandoit.backup

import android.app.Application
import android.net.Uri
import dev.mathieu.youcandoit.data.backup.BackupFiles
import dev.mathieu.youcandoit.data.backup.BackupJson
import dev.mathieu.youcandoit.data.backup.NotABackupException
import dev.mathieu.youcandoit.domain.fakes.NOW
import dev.mathieu.youcandoit.domain.fakes.TestEnv
import dev.mathieu.youcandoit.domain.fakes.aCategory
import dev.mathieu.youcandoit.domain.fakes.aTask
import dev.mathieu.youcandoit.domain.model.Priority
import dev.mathieu.youcandoit.domain.model.Reminder
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.usecase.Backup
import dev.mathieu.youcandoit.domain.usecase.ExportBackup
import dev.mathieu.youcandoit.domain.usecase.ImportBackup
import dev.mathieu.youcandoit.ui.MainDispatcherRule
import dev.mathieu.youcandoit.ui.backup.BackupMessage
import dev.mathieu.youcandoit.ui.backup.BackupViewModel
import java.io.IOException
import java.time.Duration
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** org.json is part of Android, hence Robolectric even for the file format. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BackupTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val everyKind = Backup(
        categories = listOf(aCategory("work", "Work")),
        tasks = listOf(
            aTask("p", priority = Priority.URGENT, categoryId = aCategory("work").id, deadline = NOW.plus(Duration.ofDays(2)), reminder = Reminder.AtDeadline)
                .copy(description = "Line one\nLine \"two\""),
            aTask("c", parentId = TaskId("p"), categoryId = aCategory("work").id, position = 4, reminder = Reminder.At(NOW.plus(Duration.ofHours(3)))),
            aTask("b", deadline = NOW.plus(Duration.ofDays(1)), reminder = Reminder.BeforeDeadline(Duration.ofMinutes(90))),
            aTask("done", priority = Priority.MINIMAL).markDone(NOW),
            aTask("old").markDone(NOW).archive(NOW.plus(Duration.ofMinutes(1))),
        ),
    )

    @Test
    fun `every kind of task and reminder survives the file unchanged`() {
        val text = BackupJson.encode(everyKind, NOW)

        assertEquals(everyKind, BackupJson.decode(text))
    }

    @Test
    fun `other files are refused, including exports from a newer version`() {
        assertFailsWith<NotABackupException> { BackupJson.decode("not json at all") }
        assertFailsWith<NotABackupException> { BackupJson.decode("""{"name": "something else"}""") }
        assertFailsWith<NotABackupException> {
            BackupJson.decode("""{"format": "${BackupJson.FORMAT}", "version": ${BackupJson.VERSION + 1}, "categories": [], "tasks": []}""")
        }
        assertFailsWith<NotABackupException> {
            BackupJson.decode("""{"format": "${BackupJson.FORMAT}", "version": 1, "categories": [], "tasks": [{"id": "a"}]}""")
        }
    }

    private class MemoryFiles : BackupFiles {
        val files = mutableMapOf<Uri, String>()
        var broken = false
        override suspend fun write(uri: Uri, text: String) {
            if (broken) throw IOException("disk full")
            files[uri] = text
        }
        override suspend fun read(uri: Uri): String = files[uri] ?: throw IOException("no such file")
    }

    private val zone = ZoneId.of("America/Montreal")

    private fun viewModel(env: TestEnv, files: BackupFiles) = BackupViewModel(
        exportBackup = ExportBackup(env.tasks, env.categories),
        importBackup = ImportBackup(env.tasks, env.categories, env.reminders),
        files = files,
        clock = env.clock,
        zone = zone,
    )

    @Test
    fun `tasks exported on the old app can be imported on the new one`() = kotlinx.coroutines.test.runTest {
        val files = MemoryFiles()
        val old = TestEnv()
        everyKind.categories.forEach { old.categories.upsert(it) }
        old.tasks.upsertAll(everyKind.tasks)
        val uri = Uri.parse("content://files/backup.json")

        val oldApp = viewModel(old, files)
        assertEquals("you-can-do-it-2026-11-01.json", oldApp.exportFileName())
        oldApp.onExport(uri)
        assertEquals(BackupMessage.Exported, oldApp.message.value)

        val new = TestEnv()
        val newApp = viewModel(new, files)
        newApp.onImport(uri)

        assertEquals(BackupMessage.Imported(5), newApp.message.value)
        assertEquals(everyKind.tasks.toSet(), new.tasks.all.toSet())
        newApp.onMessageShown()
        assertNull(newApp.message.value)
    }

    @Test
    fun `a wrong file or a failing one is reported, not a crash`() = kotlinx.coroutines.test.runTest {
        val files = MemoryFiles()
        val notOurs = Uri.parse("content://files/photo.jpg")
        files.files[notOurs] = "ÿØ binary"
        val app = viewModel(TestEnv(), files)

        app.onImport(notOurs)
        assertEquals(BackupMessage.NotABackup, app.message.value)

        app.onImport(Uri.parse("content://files/gone.json"))
        assertEquals(BackupMessage.Failed, app.message.value)

        files.broken = true
        app.onExport(Uri.parse("content://files/new.json"))
        assertEquals(BackupMessage.Failed, app.message.value)
    }
}
