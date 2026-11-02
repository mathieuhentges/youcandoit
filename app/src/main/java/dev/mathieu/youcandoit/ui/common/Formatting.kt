package dev.mathieu.youcandoit.ui.common

import android.text.format.DateFormat
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import dev.mathieu.youcandoit.R
import dev.mathieu.youcandoit.domain.model.DomainError
import dev.mathieu.youcandoit.domain.model.ListCounts
import dev.mathieu.youcandoit.domain.model.Reminder
import dev.mathieu.youcandoit.domain.model.StatsPeriod
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** How a date reads relative to today. Pure, so it's unit-tested. */
sealed interface RelativeDay {
    data object Today : RelativeDay
    data object Tomorrow : RelativeDay
    data object Yesterday : RelativeDay
    data class ThisWeek(val day: DayOfWeek) : RelativeDay
    data class Other(val date: LocalDate, val sameYear: Boolean) : RelativeDay
}

fun relativeDay(date: LocalDate, today: LocalDate): RelativeDay = when {
    date == today -> RelativeDay.Today
    date == today.plusDays(1) -> RelativeDay.Tomorrow
    date == today.minusDays(1) -> RelativeDay.Yesterday
    date.isAfter(today) && date.isBefore(today.plusDays(7)) -> RelativeDay.ThisWeek(date.dayOfWeek)
    else -> RelativeDay.Other(date, sameYear = date.year == today.year)
}

/** A date pattern in the order the user's language expects ("Nov 3" in English, "3 nov." in French). */
private fun localizedPattern(skeleton: String, locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

/** "Today, 17:00", "Wed, 10:00", "Oct 31, 9:00 AM" — in the device's zone and locale. */
@Composable
fun formatDateTime(instant: Instant): String {
    val zone = ZoneId.systemDefault()
    val local = instant.atZone(zone)
    val locale = LocalConfiguration.current.locales[0]
    val day = when (val r = relativeDay(local.toLocalDate(), LocalToday.current)) {
        RelativeDay.Today -> stringResource(R.string.day_today)
        RelativeDay.Tomorrow -> stringResource(R.string.day_tomorrow)
        RelativeDay.Yesterday -> stringResource(R.string.day_yesterday)
        is RelativeDay.ThisWeek -> r.day.getDisplayName(TextStyle.SHORT, locale)
        is RelativeDay.Other -> r.date.format(localizedPattern(if (r.sameYear) "MMMd" else "yMMMd", locale))
    }
    val time = local.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
    return "$day, $time"
}

@Composable
fun formatDate(date: LocalDate): String =
    date.format(localizedPattern("EEEMMMd", LocalConfiguration.current.locales[0]))

@Composable
fun formatTime(time: LocalTime): String =
    time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(LocalConfiguration.current.locales[0]))

/** "2 open · 1 overdue · 1 done", with the overdue part in the error color. */
@Composable
fun countsText(counts: ListCounts): AnnotatedString {
    val open = stringResource(R.string.count_open, counts.open)
    val overdue = stringResource(R.string.count_overdue, counts.overdue)
    val done = stringResource(R.string.count_done, counts.done)
    val empty = stringResource(R.string.count_empty)
    val errorColor = MaterialTheme.colorScheme.error
    return buildAnnotatedString {
        if (counts.open == 0 && counts.done == 0) {
            append(empty)
            return@buildAnnotatedString
        }
        val parts = buildList {
            if (counts.open > 0) add(open to false)
            if (counts.overdue > 0) add(overdue to true)
            if (counts.done > 0) add(done to false)
        }
        parts.forEachIndexed { index, (text, isOverdue) ->
            if (index > 0) append(" · ")
            if (isOverdue) withStyle(SpanStyle(color = errorColor, fontWeight = FontWeight.Medium)) { append(text) } else append(text)
        }
    }
}

@Composable
fun reminderLabel(reminder: Reminder): String = when (reminder) {
    Reminder.None -> stringResource(R.string.reminder_none)
    Reminder.AtDeadline -> stringResource(R.string.reminder_at_deadline)
    is Reminder.BeforeDeadline -> {
        val minutes = reminder.offset.toMinutes()
        when {
            minutes == 60L -> stringResource(R.string.reminder_hour_before)
            minutes == 1440L -> stringResource(R.string.reminder_day_before)
            minutes % 1440L == 0L -> stringResource(R.string.reminder_days_before, minutes / 1440L)
            minutes % 60L == 0L -> stringResource(R.string.reminder_hours_before, minutes / 60L)
            else -> stringResource(R.string.reminder_minutes_before, minutes)
        }
    }
    is Reminder.At -> formatDateTime(reminder.at)
}

@Composable
fun periodLabel(period: StatsPeriod): String = stringResource(
    when (period) {
        StatsPeriod.LAST_7_DAYS -> R.string.period_7_days
        StatsPeriod.LAST_30_DAYS -> R.string.period_30_days
        StatsPeriod.ALL_TIME -> R.string.period_all_time
    },
)

@Composable
fun DomainError.userMessage(): String = when (this) {
    is DomainError.BlankTitle -> stringResource(R.string.error_blank_title)
    is DomainError.DeadlineNotInFuture -> stringResource(R.string.error_deadline_past)
    is DomainError.ReminderWithoutDeadline -> stringResource(R.string.error_reminder_without_deadline)
    is DomainError.ReminderNotInFuture -> stringResource(R.string.error_reminder_past)
    is DomainError.CategoryNotFound -> stringResource(R.string.error_category_not_found)
    is DomainError.TaskNotFound -> stringResource(R.string.error_task_not_found)
    is DomainError.TaskArchived -> stringResource(R.string.error_task_archived)
    is DomainError.TaskNotDone -> stringResource(R.string.error_task_not_done)
    is DomainError.SubTaskNotAllowed -> stringResource(R.string.error_sub_task_not_allowed)
    is DomainError.HasSubTasks -> stringResource(R.string.error_has_sub_tasks)
    is DomainError.BlankCategoryName -> stringResource(R.string.error_blank_category)
    is DomainError.DuplicateCategoryName -> stringResource(R.string.error_duplicate_category, name)
}

@Composable
fun UserMessage.text(): String = when (this) {
    is UserMessage.Archived -> pluralStringResource(R.plurals.message_archived, count, count)
    is UserMessage.Deleted -> pluralStringResource(R.plurals.message_deleted, count, count)
    is UserMessage.Error -> error.userMessage()
}
