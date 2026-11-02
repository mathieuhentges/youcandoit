package dev.mathieu.youcandoit.domain.model

import java.time.Duration
import java.time.Instant

/** Notification choice for a task. [AtDeadline] and [BeforeDeadline] require a deadline; [At] doesn't. */
sealed interface Reminder {
    data object None : Reminder
    data object AtDeadline : Reminder
    data class BeforeDeadline(val offset: Duration) : Reminder {
        init {
            require(!offset.isNegative && !offset.isZero) { "Reminder offset must be positive, was $offset" }
        }
    }

    /** A date and time of the user's choosing, with or without a deadline. */
    data class At(val at: Instant) : Reminder

    /** Set relative to the deadline, so it needs one. */
    val needsDeadline: Boolean get() = this == AtDeadline || this is BeforeDeadline
}
