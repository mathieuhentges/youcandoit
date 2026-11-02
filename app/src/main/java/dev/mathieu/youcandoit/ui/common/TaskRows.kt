@file:OptIn(ExperimentalMaterial3Api::class)

package dev.mathieu.youcandoit.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mathieu.youcandoit.R
import dev.mathieu.youcandoit.domain.model.Priority
import dev.mathieu.youcandoit.domain.model.Reminder

val RowShape = RoundedCornerShape(16.dp)

@Composable
fun PriorityChip(priority: Priority, modifier: Modifier = Modifier) {
    val style = priority.style()
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(style.container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).background(style.dot, CircleShape))
        Text(stringResource(priority.labelRes()), color = style.content, style = MaterialTheme.typography.labelMedium)
    }
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

@Composable
fun OpenTaskRow(item: TaskItem, onToggle: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val task = item.task
    Surface(onClick = onClick, shape = RowShape, color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.heightIn(min = 64.dp).padding(start = 4.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DoneCheck(done = false, color = task.priority.style().dot, label = stringResource(R.string.action_mark_done, task.title), onClick = onToggle)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                OpenTaskMeta(item)
            }
            PriorityChip(task.priority)
        }
    }
}

@Composable
private fun OpenTaskMeta(item: TaskItem) {
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
        if (deadline != null && task.reminder != Reminder.None) {
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
    Surface(onClick = onClick, shape = RowShape, color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = modifier.fillMaxWidth().then(actions)) {
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

/** "Done Today, 14:32 · Work" */
@Composable
fun doneMeta(item: TaskItem): String {
    val doneAt = item.task.doneAt
    val done = if (doneAt != null) stringResource(R.string.meta_done, formatDateTime(doneAt)) else ""
    val category = item.category?.label()
    return if (category != null) "$done · $category" else done
}
