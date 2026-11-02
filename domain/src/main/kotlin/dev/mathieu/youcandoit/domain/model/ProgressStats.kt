package dev.mathieu.youcandoit.domain.model

import java.time.Duration

/** Rolling windows, so the result doesn't depend on which day a week starts in the user's locale. */
enum class StatsPeriod(val length: Duration?) {
    LAST_7_DAYS(Duration.ofDays(7)),
    LAST_30_DAYS(Duration.ofDays(30)),
    ALL_TIME(null),
}

/** Done counts include archived tasks: progress is measured from when a task was done. */
data class ProgressStats(
    val openCount: Int,
    val overdueCount: Int,
    val doneCount: Int,
    val doneByPriority: Map<Priority, Int>,
    /** Key `null` means "Uncategorized". */
    val doneByCategory: Map<CategoryId?, Int>,
)
