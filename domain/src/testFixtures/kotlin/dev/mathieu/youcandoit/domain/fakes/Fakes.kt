package dev.mathieu.youcandoit.domain.fakes

import dev.mathieu.youcandoit.domain.model.Category
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.IdGenerator
import dev.mathieu.youcandoit.domain.model.Task
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.reminder.ReminderScheduler
import dev.mathieu.youcandoit.domain.reminder.ReminderSync
import dev.mathieu.youcandoit.domain.repository.CategoryRepository
import dev.mathieu.youcandoit.domain.repository.TaskRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeTaskRepository : TaskRepository {
    private val state = MutableStateFlow<Map<TaskId, Task>>(emptyMap())
    val all: List<Task> get() = state.value.values.toList()

    override suspend fun upsert(task: Task) = state.update { it + (task.id to task) }
    override suspend fun upsertAll(tasks: List<Task>) = state.update { current -> current + tasks.associateBy { it.id } }
    override suspend fun findById(id: TaskId): Task? = state.value[id]
    override suspend fun delete(id: TaskId) = state.update { it - id }
    override suspend fun unassignCategory(categoryId: CategoryId) = state.update { tasks ->
        tasks.mapValues { (_, task) -> if (task.categoryId == categoryId) task.copy(categoryId = null) else task }
    }
    override fun observeAll(): Flow<List<Task>> = state.map { it.values.toList() }
}

class FakeCategoryRepository : CategoryRepository {
    private val state = MutableStateFlow<Map<CategoryId, Category>>(emptyMap())
    val all: List<Category> get() = state.value.values.toList()

    override suspend fun upsert(category: Category) = state.update { it + (category.id to category) }
    override suspend fun findById(id: CategoryId): Category? = state.value[id]
    override suspend fun findByName(name: String): Category? =
        state.value.values.firstOrNull { it.name.equals(name, ignoreCase = true) }
    override suspend fun delete(id: CategoryId) = state.update { it - id }
    override fun observeAll(): Flow<List<Category>> = state.map { it.values.toList() }
}

class FakeReminderScheduler : ReminderScheduler {
    val scheduled = mutableMapOf<TaskId, Instant>()
    val cancelled = mutableListOf<TaskId>()

    override suspend fun schedule(taskId: TaskId, at: Instant) {
        scheduled[taskId] = at
    }

    override suspend fun cancel(taskId: TaskId) {
        scheduled.remove(taskId)
        cancelled += taskId
    }
}

class SequentialIds(private val prefix: String) : IdGenerator {
    private var next = 0
    override fun newId(): String = "$prefix-${++next}"
}

class MutableClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now
    fun advanceBy(duration: Duration) {
        now = now.plus(duration)
    }
}

/** Everything a use case test usually needs, wired together. */
class TestEnv {
    val clock = MutableClock(NOW)
    val tasks = FakeTaskRepository()
    val categories = FakeCategoryRepository()
    val scheduler = FakeReminderScheduler()
    val reminders = ReminderSync(scheduler, clock)
}
