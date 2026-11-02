package dev.mathieu.youcandoit.domain.usecase

import app.cash.turbine.test
import dev.mathieu.youcandoit.domain.fakes.NOW
import dev.mathieu.youcandoit.domain.fakes.TestEnv
import dev.mathieu.youcandoit.domain.fakes.aTask
import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.Priority.HIGH
import dev.mathieu.youcandoit.domain.model.Priority.LOW
import dev.mathieu.youcandoit.domain.model.StatsPeriod
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class ObserveProgressStatsTest {
    private val env = TestEnv()
    private val observeStats = ObserveProgressStats(env.tasks, env.clock)
    private val work = CategoryId("work")

    private suspend fun seed() {
        env.tasks.upsert(aTask("open"))
        env.tasks.upsert(aTask("overdue", deadline = NOW.minus(Duration.ofHours(1))))
        env.tasks.upsert(aTask("recent-done", priority = HIGH, categoryId = work).markDone(NOW.minus(Duration.ofDays(1))))
        // Archived later, but done within the window: still counts.
        env.tasks.upsert(aTask("recent-archived", priority = LOW).markDone(NOW.minus(Duration.ofDays(6))).archive(NOW))
        env.tasks.upsert(aTask("old", priority = HIGH).markDone(NOW.minus(Duration.ofDays(20))).archive(NOW.minus(Duration.ofDays(19))))
    }

    @Test
    fun `counts open, overdue and tasks done in the last 7 days`() = runTest {
        seed()

        observeStats(StatsPeriod.LAST_7_DAYS).test {
            val stats = awaitItem()
            assertEquals(2, stats.openCount)
            assertEquals(1, stats.overdueCount)
            assertEquals(2, stats.doneCount)
            assertEquals(mapOf(HIGH to 1, LOW to 1), stats.doneByPriority)
            assertEquals(mapOf(work to 1, null to 1), stats.doneByCategory)
        }
    }

    @Test
    fun `all time includes older done tasks`() = runTest {
        seed()

        observeStats(StatsPeriod.ALL_TIME).test {
            val stats = awaitItem()
            assertEquals(3, stats.doneCount)
            assertEquals(mapOf(HIGH to 2, LOW to 1), stats.doneByPriority)
        }
    }
}
