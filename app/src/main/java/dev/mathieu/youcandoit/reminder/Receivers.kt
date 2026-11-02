package dev.mathieu.youcandoit.reminder

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dev.mathieu.youcandoit.di.ApplicationScope
import dev.mathieu.youcandoit.domain.model.TaskId
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun handler(): ReminderHandler

    @ApplicationScope
    fun scope(): CoroutineScope
}

/** Runs [block] off the main thread while keeping the receiver alive until it's done. */
private fun BroadcastReceiver.handleAsync(context: Context, block: suspend (ReminderHandler) -> Unit) {
    val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, ReminderEntryPoint::class.java)
    val pending = goAsync()
    entryPoint.scope().launch {
        try {
            block(entryPoint.handler())
        } finally {
            pending.finish()
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_TASK_ID)?.let { TaskId(it) } ?: return
        when (intent.action) {
            ACTION_FIRE -> handleAsync(context) { it.onReminderDue(id) }
            ACTION_MARK_DONE -> handleAsync(context) { it.onMarkDone(id) }
        }
    }

    companion object {
        const val ACTION_FIRE = "dev.mathieu.youcandoit.reminder.FIRE"
        const val ACTION_MARK_DONE = "dev.mathieu.youcandoit.reminder.MARK_DONE"
        const val EXTRA_TASK_ID = "taskId"

        fun fireIntent(context: Context, id: TaskId): PendingIntent = pendingIntent(context, id, ACTION_FIRE)

        fun markDoneIntent(context: Context, id: TaskId): PendingIntent = pendingIntent(context, id, ACTION_MARK_DONE)

        /** The data URI makes each task's intent distinct, so one task's alarm never replaces another's. */
        private fun pendingIntent(context: Context, id: TaskId, action: String): PendingIntent {
            val intent = Intent(context, ReminderReceiver::class.java)
                .setAction(action)
                .setData("todo://reminder/${id.value}".toUri())
                .putExtra(EXTRA_TASK_ID, id.value)
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        handleAsync(context) { it.onRescheduleNeeded() }
    }

    companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
