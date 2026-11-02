package dev.mathieu.youcandoit.domain.model

import java.time.Duration

/** Notification choice for a task. Any reminder other than [None] requires a deadline. */
sealed interface Reminder {
    data object None : Reminder
    data object AtDeadline : Reminder
    data class BeforeDeadline(val offset: Duration) : Reminder {
        init {
            require(!offset.isNegative && !offset.isZero) { "Reminder offset must be positive, was $offset" }
        }
    }
}
