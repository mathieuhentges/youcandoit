package dev.mathieu.youcandoit.domain.usecase

import dev.mathieu.youcandoit.domain.reminder.ReminderSync
import dev.mathieu.youcandoit.domain.repository.TaskRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Re-registers future reminders after a reboot, an app update, a clock change, or on app start.
 * Only ever schedules: it can run while a reminder is firing, and must not dismiss it.
 */
class RescheduleAllReminders @Inject constructor(
    private val tasks: TaskRepository,
    private val reminders: ReminderSync,
) {
    suspend operator fun invoke() {
        tasks.observeAll().first()
            .filter { it.isOpen }
            .forEach { reminders.restore(it) }
    }
}
