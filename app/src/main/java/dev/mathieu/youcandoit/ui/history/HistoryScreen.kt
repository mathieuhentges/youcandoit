@file:OptIn(ExperimentalMaterial3Api::class)

package dev.mathieu.youcandoit.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mathieu.youcandoit.R
import dev.mathieu.youcandoit.domain.model.HistoryOrder
import dev.mathieu.youcandoit.domain.model.Priority
import dev.mathieu.youcandoit.domain.model.ProgressStats
import dev.mathieu.youcandoit.domain.model.StatsPeriod
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.ui.common.RowShape
import dev.mathieu.youcandoit.ui.common.TaskItem
import dev.mathieu.youcandoit.ui.common.doneMeta
import dev.mathieu.youcandoit.ui.common.labelRes
import dev.mathieu.youcandoit.ui.common.periodLabel
import dev.mathieu.youcandoit.ui.common.style

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryContent(
        state = state,
        onPeriodSelected = viewModel::onPeriodSelected,
        onRestore = viewModel::onRestore,
        onOrderSelected = viewModel::onOrderSelected,
    )
}

@Composable
fun HistoryContent(
    state: HistoryUiState,
    onPeriodSelected: (StatsPeriod) -> Unit,
    onRestore: (TaskId) -> Unit,
    onOrderSelected: (HistoryOrder) -> Unit,
) {
    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 20.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                Column(Modifier.padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.title_history), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.subtitle_history),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "period") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    StatsPeriod.entries.forEachIndexed { index, period ->
                        SegmentedButton(
                            selected = period == state.period,
                            onClick = { onPeriodSelected(period) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = StatsPeriod.entries.size),
                        ) { Text(periodLabel(period)) }
                    }
                }
            }
            state.stats?.let { stats ->
                item(key = "stats") { StatCards(stats) }
                item(key = "bars") { PriorityBars(stats) }
            }
            item(key = "archived-header") {
                Row(Modifier.padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.history_archived),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    OrderMenu(order = state.order, onOrderSelected = onOrderSelected)
                }
            }
            if (!state.isLoading && state.archived.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.empty_history),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            items(state.archived, key = { it.task.id.value }) { item ->
                ArchivedRow(item = item, onRestore = { onRestore(item.task.id) }, modifier = Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun OrderMenu(order: HistoryOrder, onOrderSelected: (HistoryOrder) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(order.labelRes()))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            HistoryOrder.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes())) },
                    leadingIcon = { RadioButton(selected = option == order, onClick = null) },
                    onClick = {
                        onOrderSelected(option)
                        open = false
                    },
                    modifier = Modifier.semantics { selected = option == order },
                )
            }
        }
    }
}

private fun HistoryOrder.labelRes(): Int = when (this) {
    HistoryOrder.RECENTLY_ARCHIVED -> R.string.sort_recently_archived
    HistoryOrder.RECENTLY_DONE -> R.string.sort_recently_done
    HistoryOrder.PRIORITY -> R.string.sort_priority
    HistoryOrder.CATEGORY -> R.string.sort_category
}

@Composable
private fun StatCards(stats: ProgressStats) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(stats.doneCount, stringResource(R.string.stat_done), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        StatCard(stats.openCount, stringResource(R.string.stat_open), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        StatCard(stats.overdueCount, stringResource(R.string.stat_overdue), MaterialTheme.colorScheme.error, Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(value: Int, label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(shape = RowShape, color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("$value", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PriorityBars(stats: ProgressStats) {
    val max = (stats.doneByPriority.values.maxOrNull() ?: 0).coerceAtLeast(1)
    Surface(shape = RowShape, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.history_by_priority), style = MaterialTheme.typography.titleSmall)
            Priority.entries.reversed().forEach { priority ->
                val count = stats.doneByPriority[priority] ?: 0
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(priority.labelRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(64.dp),
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(count.toFloat() / max)
                                .clip(RoundedCornerShape(5.dp))
                                .background(priority.style().dot),
                        )
                    }
                    Text(
                        "$count",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(28.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchivedRow(item: TaskItem, onRestore: () -> Unit, modifier: Modifier = Modifier) {
    val task = item.task
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(shape = RowShape, color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(22.dp).background(task.priority.style().dot, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(task.title, style = MaterialTheme.typography.bodyLarge, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(doneMeta(item), style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onRestore) {
                Icon(Icons.Outlined.Unarchive, contentDescription = stringResource(R.string.action_restore, task.title), tint = muted)
            }
        }
    }
}
