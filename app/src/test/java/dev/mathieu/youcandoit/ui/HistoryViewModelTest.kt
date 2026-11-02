package dev.mathieu.youcandoit.ui

import dev.mathieu.youcandoit.domain.fakes.NOW
import dev.mathieu.youcandoit.domain.fakes.TestEnv
import dev.mathieu.youcandoit.domain.fakes.aCategory
import dev.mathieu.youcandoit.domain.fakes.aTask
import dev.mathieu.youcandoit.domain.model.HistoryOrder
import dev.mathieu.youcandoit.domain.model.Priority
import dev.mathieu.youcandoit.domain.model.StatsPeriod
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.model.TaskStatus
import dev.mathieu.youcandoit.domain.usecase.ObserveArchivedTasks
import dev.mathieu.youcandoit.domain.usecase.ObserveCategories
import dev.mathieu.youcandoit.domain.usecase.ObserveProgressStats
import dev.mathieu.youcandoit.domain.usecase.RestoreTask
import dev.mathieu.youcandoit.settings.FakeSettings
import dev.mathieu.youcandoit.ui.common.CategoryLabel
import dev.mathieu.youcandoit.ui.history.HistoryViewModel
import java.time.Duration
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class HistoryViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val env = TestEnv()
    private val work = aCategory("work", "Work")
    private val settings = FakeSettings()

    private fun viewModel() = HistoryViewModel(
        observeProgressStats = ObserveProgressStats(env.tasks, env.clock),
        observeArchivedTasks = ObserveArchivedTasks(env.tasks, env.categories),
        observeCategories = ObserveCategories(env.categories),
        restoreTask = RestoreTask(env.tasks),
        settings = settings,
        ticker = FakeTicker(env.clock),
    )

    private suspend fun seed() {
        env.categories.upsert(work)
        env.tasks.upsert(aTask("recent", categoryId = work.id).markDone(NOW.minus(Duration.ofDays(1))).archive(NOW))
        env.tasks.upsert(aTask("old").markDone(NOW.minus(Duration.ofDays(20))).archive(NOW.minus(Duration.ofDays(19))))
        env.tasks.upsert(aTask("open"))
    }

    @Test
    fun `shows archived tasks and the stats for the selected period`() = runTest {
        seed()
        val vm = viewModel()
        keepCollecting(vm.uiState)

        val week = vm.uiState.value
        assertEquals(StatsPeriod.LAST_7_DAYS, week.period)
        assertEquals(1, week.stats?.doneCount)
        assertEquals(1, week.stats?.openCount)
        assertEquals(listOf("recent", "old"), week.archived.map { it.task.id.value })
        assertEquals(CategoryLabel.Named("Work"), week.archived.first().category)

        vm.onPeriodSelected(StatsPeriod.ALL_TIME)

        assertEquals(StatsPeriod.ALL_TIME, vm.uiState.value.period)
        assertEquals(2, vm.uiState.value.stats?.doneCount)
    }

    @Test
    fun `the archived list follows the chosen order, and the choice is saved`() = runTest {
        seed()
        env.tasks.upsert(aTask("urgent", priority = Priority.URGENT).markDone(NOW.minus(Duration.ofDays(30))).archive(NOW.minus(Duration.ofDays(30))))
        val vm = viewModel()
        keepCollecting(vm.uiState)
        assertEquals(listOf("recent", "old", "urgent"), vm.uiState.value.archived.map { it.task.id.value })

        vm.onOrderSelected(HistoryOrder.PRIORITY)

        assertEquals(HistoryOrder.PRIORITY, settings.historyOrder.value)
        assertEquals(HistoryOrder.PRIORITY, vm.uiState.value.order)
        assertEquals(listOf("urgent", "recent", "old"), vm.uiState.value.archived.map { it.task.id.value })
    }

    @Test
    fun `restoring sends a task back to its list as done`() = runTest {
        seed()
        val vm = viewModel()
        keepCollecting(vm.uiState)

        vm.onRestore(TaskId("recent"))

        assertEquals(listOf("old"), vm.uiState.value.archived.map { it.task.id.value })
        assertEquals(TaskStatus.Done(NOW.minus(Duration.ofDays(1))), env.tasks.findById(TaskId("recent"))?.status)
    }
}
