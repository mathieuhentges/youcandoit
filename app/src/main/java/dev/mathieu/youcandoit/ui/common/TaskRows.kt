@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package dev.mathieu.youcandoit.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mathieu.youcandoit.R
import dev.mathieu.youcandoit.domain.model.Priority
import dev.mathieu.youcandoit.domain.reminder.ReminderPolicy

val RowShape = RoundedCornerShape(16.dp)

/** Just the priority's color, so the title keeps the room; its name is read out by screen readers. */
@Composable
fun PriorityDot(priority: Priority, modifier: Modifier = Modifier) {
    val label = stringResource(priority.labelRes())
    Box(
        modifier
            .size(10.dp)
            .background(priority.style().dot, CircleShape)
            .semantics { contentDescription = label },
    )
}

/** The round checkbox: a ring in the priority color when open, filled with a check when done. */
@Composable
fun DoneCheck(done: Boolean, color: Color, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .then(if (done) Modifier.background(color, CircleShape) else Modifier.border(2.5.dp, color, CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
fun CategoryLabel.label(): String = when (this) {
    is CategoryLabel.Named -> name
    CategoryLabel.Uncategorized -> stringResource(R.string.title_uncategorized)
}

/**
 * [dragHandle] is shown at the row's end, after the priority, where the list allows reordering.
 * A parent ([TaskItem.subTasks] set) gets an arrow to show or hide its sub tasks.
 */
@Composable
fun OpenTaskRow(
    item: TaskItem,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandle: (@Composable () -> Unit)? = null,
    expanded: Boolean = true,
    onToggleExpanded: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
) {
    val task = item.task
    RowSurface(onClick = onClick, onLongClick = onLongClick, selected = selected, modifier = modifier) {
        Row(
            modifier = Modifier.heightIn(min = 64.dp).padding(start = 4.dp, end = if (dragHandle != null) 0.dp else 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DoneCheck(done = false, color = task.priority.style().dot, label = stringResource(R.string.action_mark_done, task.title), onClick = onToggle)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                OpenTaskMeta(item, showCount = onToggleExpanded == null)
            }
            PriorityDot(task.priority)
            val count = item.subTasks
            if (count != null && onToggleExpanded != null) SubTaskToggle(count, expanded, onToggleExpanded)
            dragHandle?.invoke()
        }
    }
}

@Composable
private fun OpenTaskMeta(item: TaskItem, showCount: Boolean) {
    val task = item.task
    val deadline = task.deadline
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val small = MaterialTheme.typography.bodySmall
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            deadline == null -> Text(stringResource(R.string.meta_no_deadline), style = small, color = muted)
            item.isOverdue -> Text(
                stringResource(R.string.meta_overdue, formatDateTime(deadline)),
                style = small,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium,
            )
            else -> Text(formatDateTime(deadline), style = small, color = muted)
        }
        item.subTasks?.takeIf { showCount }?.let {
            Text(stringResource(R.string.sub_task_count, it.done, it.total), style = small, color = muted, fontWeight = FontWeight.Medium)
        }
        if (ReminderPolicy.hasReminder(task)) {
            Icon(Icons.Outlined.Notifications, contentDescription = stringResource(R.string.reminder_set), tint = muted, modifier = Modifier.size(14.dp))
        }
        item.category?.let { Text("· ${it.label()}", style = small, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/**
 * [onArchive] is offered as an accessibility action ("Archive" in TalkBack's actions menu), because
 * swiping, the visual way to archive one task, isn't available to screen reader users.
 */
@Composable
fun DoneTaskRow(
    item: TaskItem,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onArchive: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
) {
    val task = item.task
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val archiveLabel = stringResource(R.string.action_archive)
    val actions = if (onArchive != null) {
        Modifier.semantics {
            customActions = listOf(CustomAccessibilityAction(archiveLabel) { onArchive(); true })
        }
    } else {
        Modifier
    }
    RowSurface(onClick = onClick, onLongClick = onLongClick, selected = selected, modifier = modifier.then(actions)) {
        Row(
            modifier = Modifier.heightIn(min = 56.dp).padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DoneCheck(done = true, color = task.priority.style().dot, label = stringResource(R.string.action_mark_not_done, task.title), onClick = onToggle)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = muted,
                    textDecoration = TextDecoration.LineThrough,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(doneMeta(item), style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * The parent's "1/3" with an arrow: how many of its sub tasks are done, and a tap folds them away or
 * shows them again. Filled while folded, so a hidden list stands out.
 */
@Composable
private fun SubTaskToggle(count: SubTaskCount, expanded: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(if (expanded) R.string.action_hide_sub_tasks else R.string.action_show_sub_tasks) +
        ", " + pluralStringResource(R.plurals.sub_task_count_description, count.total, count.done, count.total)
    Surface(
        onClick = onToggle,
        shape = CircleShape,
        color = if (expanded) colors.surfaceContainerHigh else colors.secondaryContainer,
        contentColor = if (expanded) colors.onSurfaceVariant else colors.onSecondaryContainer,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
                onClick { onToggle(); true }
            },
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(stringResource(R.string.sub_task_count, count.done, count.total), style = MaterialTheme.typography.labelLarge)
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

/** A task row's card. [onLongClick] starts selection mode; a [selected] row is highlighted. */
@Composable
private fun RowSurface(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    selected: Boolean,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RowShape,
        color = if (selected) colors.secondaryContainer else colors.surfaceContainerLowest,
        border = if (selected) BorderStroke(2.dp, colors.primary) else null,
        modifier = modifier
            .fillMaxWidth()
            .clip(RowShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = stringResource(R.string.action_select))
            .semantics { if (onLongClick != null) this.selected = selected },
        content = content,
    )
}

/** "Done Today, 14:32 · Work" */
@Composable
fun doneMeta(item: TaskItem): String {
    val doneAt = item.task.doneAt
    val done = if (doneAt != null) stringResource(R.string.meta_done, formatDateTime(doneAt)) else ""
    val category = item.category?.label()
    return if (category != null) "$done · $category" else done
}
