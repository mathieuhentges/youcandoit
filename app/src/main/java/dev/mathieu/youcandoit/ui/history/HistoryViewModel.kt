package dev.mathieu.youcandoit.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mathieu.youcandoit.domain.model.ProgressStats
import dev.mathieu.youcandoit.domain.model.StatsPeriod
import dev.mathieu.youcandoit.domain.model.TaskId
import dev.mathieu.youcandoit.domain.usecase.ObserveArchivedTasks
import dev.mathieu.youcandoit.domain.usecase.ObserveCategories
import dev.mathieu.youcandoit.domain.usecase.ObserveProgressStats
import dev.mathieu.youcandoit.domain.usecase.RestoreTask
import dev.mathieu.youcandoit.ui.common.TaskItem
import dev.mathieu.youcandoit.ui.common.Ticker
import dev.mathieu.youcandoit.ui.common.toItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    val isLoading: Boolean = true,
    val period: StatsPeriod = StatsPeriod.LAST_7_DAYS,
    val stats: ProgressStats? = null,
    val archived: List<TaskItem> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    observeProgressStats: ObserveProgressStats,
    observeArchivedTasks: ObserveArchivedTasks,
    observeCategories: ObserveCategories,
    private val restoreTask: RestoreTask,
    ticker: Ticker,
) : ViewModel() {
    private val period = MutableStateFlow(StatsPeriod.LAST_7_DAYS)

    val uiState: StateFlow<HistoryUiState> =
        combine(
            period,
            // "Last 7 days" moves with time: recompute every minute.
            combine(period, ticker.minutes) { p, _ -> p }.flatMapLatest { observeProgressStats(it) },
            observeArchivedTasks(),
            observeCategories(),
            ticker.minutes,
        ) { selected, stats, archived, categories, now ->
            val byId = categories.associateBy { it.id }
            HistoryUiState(
                isLoading = false,
                period = selected,
                stats = stats,
                archived = archived.map { it.toItem(byId, showCategory = true, now = now) },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun onPeriodSelected(selected: StatsPeriod) {
        period.value = selected
    }

    fun onRestore(id: TaskId) {
        viewModelScope.launch { restoreTask(id) }
    }
}
