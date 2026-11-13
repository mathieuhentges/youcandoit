package dev.mathieu.youcandoit.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.mathieu.youcandoit.domain.model.HistoryOrder
import dev.mathieu.youcandoit.domain.model.Priority
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SettingsRepository {
    val theme: Flow<ThemePreference>
    suspend fun setTheme(theme: ThemePreference)

    /** The day the welcome screen was last dismissed; null if never. It shows once a day. */
    val welcomeSeenOn: Flow<LocalDate?>
    suspend fun markWelcomeSeen(on: LocalDate)

    val listSort: Flow<ListSort>
    suspend fun setListSort(sort: ListSort)

    val historyOrder: Flow<HistoryOrder>
    suspend fun setHistoryOrder(order: HistoryOrder)

    /** The day the user last turned down an update; null if never. */
    val updateDeclinedOn: Flow<LocalDate?>
    suspend fun markUpdateDeclined(on: LocalDate)

    val priorityColors: Flow<PriorityColors>
    suspend fun setPriorityColors(colors: PriorityColors)

    /** The ids of the parent tasks whose sub tasks are folded away. */
    val collapsedParents: Flow<Set<String>>
    suspend fun setCollapsedParents(ids: Set<String>)
}

class DataStoreSettingsRepository @Inject constructor(
    private val store: DataStore<Preferences>,
) : SettingsRepository {
    override val theme: Flow<ThemePreference> = store.data.map { ThemePreference.fromKey(it[THEME]) }

    override suspend fun setTheme(theme: ThemePreference) {
        store.edit { it[THEME] = theme.key }
    }

    override val welcomeSeenOn: Flow<LocalDate?> = store.data.map { prefs -> prefs[WELCOME_SEEN_ON]?.let { LocalDate.ofEpochDay(it) } }

    override suspend fun markWelcomeSeen(on: LocalDate) {
        store.edit { it[WELCOME_SEEN_ON] = on.toEpochDay() }
    }

    override val listSort: Flow<ListSort> = store.data.map {
        ListSort(
            order = ListSort.orderFromKey(it[LIST_ORDER]),
            groupByCategory = it[GROUP_BY_CATEGORY] ?: true,
            doneOrder = ListSort.doneOrderFromKey(it[DONE_ORDER]),
        )
    }

    override suspend fun setListSort(sort: ListSort) {
        store.edit {
            it[LIST_ORDER] = ListSort.keyOf(sort.order)
            it[GROUP_BY_CATEGORY] = sort.groupByCategory
            it[DONE_ORDER] = ListSort.keyOf(sort.doneOrder)
        }
    }

    override val historyOrder: Flow<HistoryOrder> = store.data.map { prefs ->
        HistoryOrder.entries.firstOrNull { keyOf(it) == prefs[HISTORY_ORDER] } ?: HistoryOrder.RECENTLY_ARCHIVED
    }

    override suspend fun setHistoryOrder(order: HistoryOrder) {
        store.edit { it[HISTORY_ORDER] = keyOf(order) }
    }

    override val updateDeclinedOn: Flow<LocalDate?> = store.data.map { prefs -> prefs[UPDATE_DECLINED_ON]?.let { LocalDate.ofEpochDay(it) } }

    override suspend fun markUpdateDeclined(on: LocalDate) {
        store.edit { it[UPDATE_DECLINED_ON] = on.toEpochDay() }
    }

    override val priorityColors: Flow<PriorityColors> = store.data.map { prefs ->
        PriorityColors(Priority.entries.mapNotNull { priority -> prefs[colorKey(priority)]?.let { priority to it } }.toMap())
    }

    override suspend fun setPriorityColors(colors: PriorityColors) {
        store.edit { prefs ->
            Priority.entries.forEach { priority ->
                val argb = colors.customOf(priority)
                if (argb != null) prefs[colorKey(priority)] = argb else prefs.remove(colorKey(priority))
            }
        }
    }

    override val collapsedParents: Flow<Set<String>> = store.data.map { it[COLLAPSED_PARENTS].orEmpty() }

    override suspend fun setCollapsedParents(ids: Set<String>) {
        store.edit { it[COLLAPSED_PARENTS] = ids }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        // Replaces the "welcome_seen" flag of release 1, which is left unread: the welcome shows again once.
        val WELCOME_SEEN_ON = longPreferencesKey("welcome_seen_on")
        val LIST_ORDER = stringPreferencesKey("list_order")
        val GROUP_BY_CATEGORY = booleanPreferencesKey("group_by_category")
        val DONE_ORDER = stringPreferencesKey("done_order")
        val HISTORY_ORDER = stringPreferencesKey("history_order")
        val UPDATE_DECLINED_ON = longPreferencesKey("update_declined_on")
        val COLLAPSED_PARENTS = stringSetPreferencesKey("collapsed_parents")

        /** What's stored: fixed strings, safe from renaming the enums. */
        fun colorKey(priority: Priority) = intPreferencesKey(
            when (priority) {
                Priority.URGENT -> "priority_color_urgent"
                Priority.HIGH -> "priority_color_high"
                Priority.MEDIUM -> "priority_color_medium"
                Priority.LOW -> "priority_color_low"
                Priority.MINIMAL -> "priority_color_minimal"
            },
        )

        fun keyOf(order: HistoryOrder): String = when (order) {
            HistoryOrder.RECENTLY_ARCHIVED -> "recently_archived"
            HistoryOrder.RECENTLY_DONE -> "recently_done"
            HistoryOrder.PRIORITY -> "priority"
            HistoryOrder.CATEGORY -> "category"
        }
    }
}
