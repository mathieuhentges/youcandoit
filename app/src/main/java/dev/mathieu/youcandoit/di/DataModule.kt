package dev.mathieu.youcandoit.di

import android.content.Context
import androidx.room.Room
import dev.mathieu.youcandoit.data.local.CategoryDao
import dev.mathieu.youcandoit.data.local.TaskDao
import dev.mathieu.youcandoit.data.local.TodoDatabase
import dev.mathieu.youcandoit.data.repository.RoomCategoryRepository
import dev.mathieu.youcandoit.data.repository.RoomTaskRepository
import dev.mathieu.youcandoit.domain.reminder.ReminderScheduler
import dev.mathieu.youcandoit.domain.repository.CategoryRepository
import dev.mathieu.youcandoit.domain.repository.TaskRepository
import dev.mathieu.youcandoit.reminder.AlarmReminderScheduler
import dev.mathieu.youcandoit.reminder.ReminderNotifications
import dev.mathieu.youcandoit.reminder.SystemReminderNotifications
import dev.mathieu.youcandoit.ui.common.MinuteTicker
import dev.mathieu.youcandoit.ui.common.Ticker
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): TodoDatabase =
        Room.databaseBuilder(context, TodoDatabase::class.java, TodoDatabase.NAME).build()

    @Provides
    fun taskDao(db: TodoDatabase): TaskDao = db.taskDao()

    @Provides
    fun categoryDao(db: TodoDatabase): CategoryDao = db.categoryDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun taskRepository(impl: RoomTaskRepository): TaskRepository

    @Binds
    abstract fun categoryRepository(impl: RoomCategoryRepository): CategoryRepository

    @Binds
    abstract fun reminderScheduler(impl: AlarmReminderScheduler): ReminderScheduler

    @Binds
    abstract fun reminderNotifications(impl: SystemReminderNotifications): ReminderNotifications

    @Binds
    abstract fun ticker(impl: MinuteTicker): Ticker
}
