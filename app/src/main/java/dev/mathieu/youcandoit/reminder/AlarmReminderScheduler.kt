package dev.mathieu.youcandoit.reminder

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.reminder.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One alarm per task. Scheduling again replaces the previous alarm (same PendingIntent identity),
 * and cancelling also removes a notification that's already showing.
 */
@Singleton
class AlarmReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notifications: ReminderNotifications,
) : ReminderScheduler {
    private val alarms: AlarmManager = context.getSystemService(AlarmManager::class.java)

    @SuppressLint("ScheduleExactAlarm") // guarded by exactAlarmsAllowed()
    override suspend fun schedule(taskId: TaskId, at: Instant) {
        val operation = ReminderReceiver.fireIntent(context, taskId)
        val triggerAt = at.toEpochMilli()
        if (ReminderPermissions.exactAlarmsAllowed(context)) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        } else {
            alarms.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, INEXACT_WINDOW.toMillis(), operation)
        }
    }

    override suspend fun cancel(taskId: TaskId) {
        alarms.cancel(ReminderReceiver.fireIntent(context, taskId))
        notifications.cancel(taskId)
    }

    companion object {
        /** Without the exact-alarm permission, the reminder fires within this window after its time. */
        val INEXACT_WINDOW: Duration = Duration.ofMinutes(10)
    }
}
