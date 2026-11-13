package dev.mathieu.youcandoit.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.mathieu.youcandoit.domain.model.DoneOrder
import dev.mathieu.youcandoit.domain.model.HistoryOrder
import dev.mathieu.youcandoit.domain.model.Priority
import dev.mathieu.youcandoit.domain.model.TaskOrder
import dev.mathieu.youcandoit.ui.MainDispatcherRule
import java.io.File
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.repository() = DataStoreSettingsRepository(
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(folder.root, "settings.preferences_pb")
        },
    )

    @Test
    fun `the theme follows the phone until the user picks one`() = runTest {
        val settings = repository()
        assertEquals(ThemePreference.SYSTEM, settings.theme.first())

        settings.setTheme(ThemePreference.DARK)

        assertEquals(ThemePreference.DARK, settings.theme.first())
    }

    @Test
    fun `the day the welcome screen was seen is remembered`() = runTest {
        val settings = repository()
        assertNull(settings.welcomeSeenOn.first())

        settings.markWelcomeSeen(LocalDate.of(2026, 11, 2))

        assertEquals(LocalDate.of(2026, 11, 2), settings.welcomeSeenOn.first())
    }

    @Test
    fun `the day an update was declined is remembered`() = runTest {
        val settings = repository()
        assertNull(settings.updateDeclinedOn.first())

        settings.markUpdateDeclined(LocalDate.of(2026, 11, 6))

        assertEquals(LocalDate.of(2026, 11, 6), settings.updateDeclinedOn.first())
    }

    @Test
    fun `the list sort starts as my order with headers and is remembered`() = runTest {
        val settings = repository()
        assertEquals(ListSort(TaskOrder.MY_ORDER, groupByCategory = true), settings.listSort.first())

        settings.setListSort(ListSort(TaskOrder.DEADLINE, groupByCategory = false, doneOrder = DoneOrder.CATEGORY))

        assertEquals(ListSort(TaskOrder.DEADLINE, groupByCategory = false, doneOrder = DoneOrder.CATEGORY), settings.listSort.first())
    }

    @Test
    fun `the history order starts as recently archived and is remembered`() = runTest {
        val settings = repository()
        assertEquals(HistoryOrder.RECENTLY_ARCHIVED, settings.historyOrder.first())

        settings.setHistoryOrder(HistoryOrder.CATEGORY)

        assertEquals(HistoryOrder.CATEGORY, settings.historyOrder.first())
    }

    @Test
    fun `my order always has headers and is the only draggable order`() {
        assertTrue(ListSort(TaskOrder.MY_ORDER, groupByCategory = false).isGrouped)
        assertFalse(ListSort(TaskOrder.PRIORITY, groupByCategory = false).isGrouped)
        assertEquals(listOf(true, false, false, false), TaskOrder.entries.map { ListSort(it).canDrag })
        TaskOrder.entries.forEach { assertEquals(it, ListSort.orderFromKey(ListSort.keyOf(it))) }
        assertEquals(TaskOrder.MY_ORDER, ListSort.orderFromKey("alphabetical"))
        DoneOrder.entries.forEach { assertEquals(it, ListSort.doneOrderFromKey(ListSort.keyOf(it))) }
        assertEquals(DoneOrder.RECENTLY_DONE, ListSort.doneOrderFromKey(null))
    }

    @Test
    fun `unknown stored values fall back to following the phone`() {
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromKey(null))
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromKey("sepia"))
        ThemePreference.entries.forEach { assertEquals(it, ThemePreference.fromKey(it.key)) }
    }

    @Test
    fun `each choice decides dark mode`() {
        assertTrue(ThemePreference.SYSTEM.isDark(systemIsDark = true))
        assertFalse(ThemePreference.SYSTEM.isDark(systemIsDark = false))
        assertFalse(ThemePreference.LIGHT.isDark(systemIsDark = true))
        assertTrue(ThemePreference.DARK.isDark(systemIsDark = false))
    }

    @Test
    fun `the view model exposes and saves the choice`() = runTest {
        val fake = FakeSettings()
        val vm = AppearanceViewModel(fake)

        vm.onThemeSelected(ThemePreference.LIGHT)

        assertEquals(ThemePreference.LIGHT, vm.theme.value)
    }

    @Test
    fun `priority colors start as the originals and each pick is remembered`() = runTest {
        val settings = repository()
        assertTrue(settings.priorityColors.first().isDefault)

        settings.setPriorityColors(PriorityColors.DEFAULT.with(Priority.LOW, 0xFF123456.toInt()))

        val stored = settings.priorityColors.first()
        assertEquals(0xFF123456.toInt(), stored.customOf(Priority.LOW))
        assertNull(stored.customOf(Priority.URGENT))

        settings.setPriorityColors(stored.with(Priority.LOW, null))

        assertTrue(settings.priorityColors.first().isDefault)
    }

    @Test
    fun `folded parents are remembered`() = runTest {
        val settings = repository()
        assertEquals(emptySet(), settings.collapsedParents.first())

        settings.setCollapsedParents(setOf("p1", "p2"))

        assertEquals(setOf("p1", "p2"), settings.collapsedParents.first())
    }

    @Test
    fun `the view model changes one priority's color and resets them all`() = runTest {
        val fake = FakeSettings()
        val vm = AppearanceViewModel(fake)

        vm.onPriorityColorSelected(Priority.URGENT, 0xFFE52592.toInt())
        vm.onPriorityColorSelected(Priority.HIGH, 0xFFF9AB00.toInt())

        assertEquals(0xFFE52592.toInt(), fake.priorityColors.value.customOf(Priority.URGENT))
        assertEquals(0xFFF9AB00.toInt(), vm.priorityColors.value.customOf(Priority.HIGH))

        vm.onResetPriorityColors()

        assertTrue(vm.priorityColors.value.isDefault)
    }
}

class FakeSettings : SettingsRepository {
    override val theme = MutableStateFlow(ThemePreference.SYSTEM)
    override val welcomeSeenOn = MutableStateFlow<LocalDate?>(null)

    override suspend fun setTheme(theme: ThemePreference) {
        this.theme.value = theme
    }

    override suspend fun markWelcomeSeen(on: LocalDate) {
        welcomeSeenOn.value = on
    }

    override val listSort = MutableStateFlow(ListSort())

    override suspend fun setListSort(sort: ListSort) {
        listSort.value = sort
    }

    override val historyOrder = MutableStateFlow(HistoryOrder.RECENTLY_ARCHIVED)

    override suspend fun setHistoryOrder(order: HistoryOrder) {
        historyOrder.value = order
    }

    override val updateDeclinedOn = MutableStateFlow<LocalDate?>(null)

    override suspend fun markUpdateDeclined(on: LocalDate) {
        updateDeclinedOn.value = on
    }

    override val priorityColors = MutableStateFlow(PriorityColors.DEFAULT)

    override val collapsedParents = MutableStateFlow(emptySet<String>())

    override suspend fun setCollapsedParents(ids: Set<String>) {
        collapsedParents.value = ids
    }

    override suspend fun setPriorityColors(colors: PriorityColors) {
        priorityColors.value = colors
    }
}
