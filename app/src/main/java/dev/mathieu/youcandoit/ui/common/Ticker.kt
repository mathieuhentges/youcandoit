package dev.mathieu.youcandoit.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Emits the current time now and at the start of every minute, so "overdue" stays true to the clock. */
interface Ticker {
    val minutes: Flow<Instant>
}

class MinuteTicker @Inject constructor(private val clock: Clock) : Ticker {
    override val minutes: Flow<Instant> = flow {
        while (true) {
            val now = clock.instant()
            emit(now)
            delay(MINUTE_MS - now.toEpochMilli() % MINUTE_MS)
        }
    }

    private companion object {
        const val MINUTE_MS = 60_000L
    }
}

/** Today's date for "Today / Tomorrow / weekday" labels. Provided by [TodoApp][dev.mathieu.youcandoit.ui.TodoApp]. */
val LocalToday = compositionLocalOf { LocalDate.now() }

/** Re-checks the date every minute; only changes (and recomposes readers) at midnight. */
@Composable
fun rememberToday(): LocalDate {
    val today by produceState(initialValue = LocalDate.now()) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            value = LocalDate.now()
        }
    }
    return today
}
