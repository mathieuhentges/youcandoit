package dev.mathieu.youcandoit

import dev.mathieu.youcandoit.di.ApplicationScope
import dev.mathieu.youcandoit.domain.usecase.RescheduleAllReminders
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Safety net on every start: re-registers reminders, covering cases no broadcast tells us about
 * (e.g. the app was force-stopped, which clears its alarms).
 */
class AppStartup @Inject constructor(
    private val rescheduleAllReminders: RescheduleAllReminders,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun run() {
        scope.launch { rescheduleAllReminders() }
    }
}
