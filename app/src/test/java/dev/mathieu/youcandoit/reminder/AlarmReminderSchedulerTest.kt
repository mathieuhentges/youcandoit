package dev.mathieu.youcandoit.reminder

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.mathieu.youcandoit.domain.model.TaskId
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AlarmReminderSchedulerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val notifications = FakeReminderNotifications()
    private val scheduler = AlarmReminderScheduler(context, notifications)
    private val at = Instant.parse("2026-11-03T22:00:00Z")

    @Test
    fun `schedules an exact alarm when allowed`() = runTest {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)

        scheduler.schedule(TaskId("a"), at)

        val alarm = alarms.peekNextScheduledAlarm()!!
        assertEquals(at.toEpochMilli(), alarm.triggerAtMs)
        assertTrue(alarm.isAllowWhileIdle)
    }

    @Test
    fun `falls back to a short window without the exact-alarm permission`() = runTest {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        scheduler.schedule(TaskId("a"), at)

        val alarm = alarms.peekNextScheduledAlarm()!!
        assertEquals(at.toEpochMilli(), alarm.triggerAtMs)
        assertEquals(AlarmReminderScheduler.INEXACT_WINDOW.toMillis(), alarm.windowLengthMs)
    }

    @Test
    fun `scheduling a task again replaces its alarm, other tasks keep theirs`() = runTest {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)

        scheduler.schedule(TaskId("a"), at)
        scheduler.schedule(TaskId("b"), at)
        scheduler.schedule(TaskId("a"), at.plusSeconds(3600))

        assertEquals(2, alarms.scheduledAlarms.size)
    }

    @Test
    fun `cancelling removes the alarm and any notification already showing`() = runTest {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        scheduler.schedule(TaskId("a"), at)

        scheduler.cancel(TaskId("a"))

        assertTrue(alarms.scheduledAlarms.isEmpty())
        assertEquals(listOf(TaskId("a")), notifications.cancelled)
    }
}
