package dev.mathieu.youcandoit

import android.app.Application
import dev.mathieu.youcandoit.reminder.SystemReminderNotifications
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TodoApplication : Application() {
    @Inject
    lateinit var startup: AppStartup

    override fun onCreate() {
        super.onCreate()
        SystemReminderNotifications.createChannel(this)
        startup.run()
    }
}
