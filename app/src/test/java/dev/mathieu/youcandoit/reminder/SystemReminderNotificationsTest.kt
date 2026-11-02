package dev.mathieu.youcandoit.reminder

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import dev.mathieu.youcandoit.domain.model.Priority
import dev.mathieu.youcandoit.domain.model.Reminder
import dev.mathieu.youcandoit.domain.model.Task
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.model.TaskStatus
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SystemReminderNotificationsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = shadowOf(app.getSystemService(NotificationManager::class.java))
    private val notifications = SystemReminderNotifications(app)
    private val task = Task(
        id = TaskId("a"),
        title = "Submit expense report",
        description = "",
        priority = Priority.URGENT,
        categoryId = null,
        deadline = Instant.parse("2026-11-03T22:00:00Z"),
        reminder = Reminder.AtDeadline,
        createdAt = Instant.parse("2026-11-01T12:00:00Z"),
        status = TaskStatus.Open,
    )

    @Before
    fun setUp() {
        SystemReminderNotifications.createChannel(app)
    }

    @Test
    fun `shows the task with a mark-done action when allowed`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        notifications.show(task)

        val posted = manager.allNotifications.single()
        assertEquals("Submit expense report", posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(SystemReminderNotifications.CHANNEL_ID, posted.channelId)
        assertEquals("Mark done", posted.actions.single().title.toString())
    }

    @Test
    fun `stays silent without the notification permission`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        notifications.show(task)

        assertTrue(manager.allNotifications.isEmpty())
    }

    @Test
    fun `cancel removes the task's notification`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifications.show(task)

        notifications.cancel(task.id)

        assertTrue(manager.allNotifications.isEmpty())
    }
}
