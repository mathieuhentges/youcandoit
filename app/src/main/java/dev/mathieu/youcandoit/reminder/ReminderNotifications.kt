package dev.mathieu.youcandoit.reminder

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import dev.mathieu.youcandoit.DeepLinks
import dev.mathieu.youcandoit.MainActivity
import dev.mathieu.youcandoit.R
import dev.mathieu.youcandoit.domain.model.Priority
import dev.mathieu.youcandoit.domain.model.Task
import dev.mathieu.youcandoit.domain.model.TaskId
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Shows and removes reminder notifications. An interface so the reminder logic is testable on the JVM. */
interface ReminderNotifications {
    fun show(task: Task)
    fun cancel(taskId: TaskId)
}

class SystemReminderNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderNotifications {
    private val manager = NotificationManagerCompat.from(context)

    @SuppressLint("MissingPermission") // checked just below
    override fun show(task: Task) {
        if (!ReminderPermissions.notificationsAllowed(context)) return

        val deadline = task.deadline
        val text = deadline?.let {
            val flags = DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL
            context.getString(R.string.notification_due, DateUtils.formatDateTime(context, it.toEpochMilli(), flags))
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title)
            .setContentText(text)
            .setColor(task.priority.notificationColor())
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openTaskIntent(task.id))
            .addAction(0, context.getString(R.string.notification_mark_done), ReminderReceiver.markDoneIntent(context, task.id))
            .build()
        manager.notify(notificationId(task.id), notification)
    }

    override fun cancel(taskId: TaskId) {
        manager.cancel(notificationId(taskId))
    }

    /** Tapping the notification opens the task's editor (a deep link into the nav graph). */
    private fun openTaskIntent(id: TaskId): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, DeepLinks.task(id).toUri(), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return PendingIntent.getActivity(
            context,
            notificationId(id),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val CHANNEL_ID = "reminders"

        fun notificationId(id: TaskId): Int = id.value.hashCode()

        /** Safe to call on every start: creating an existing channel is a no-op. */
        fun createChannel(context: Context) {
            val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.channel_reminders))
                .setDescription(context.getString(R.string.channel_reminders_description))
                .build()
            NotificationManagerCompat.from(context).createNotificationChannel(channel)
        }
    }
}

/** Same hues as the UI's priority dots (light theme). */
private fun Priority.notificationColor(): Int = when (this) {
    Priority.URGENT -> 0xFFD93025
    Priority.HIGH -> 0xFFF57C00
    Priority.MEDIUM -> 0xFFE0A100
    Priority.LOW -> 0xFF4285F4
    Priority.MINIMAL -> 0xFF9AA0A6
}.toInt()
