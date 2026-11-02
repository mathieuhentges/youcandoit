package dev.mathieu.youcandoit.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.mathieu.youcandoit.ui.MainDispatcherRule
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `the welcome screen is remembered once seen`() = runTest {
        val settings = repository()
        assertFalse(settings.welcomeSeen.first())

        settings.markWelcomeSeen()

        assertTrue(settings.welcomeSeen.first())
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
}

class FakeSettings : SettingsRepository {
    override val theme = MutableStateFlow(ThemePreference.SYSTEM)
    override val welcomeSeen = MutableStateFlow(false)

    override suspend fun setTheme(theme: ThemePreference) {
        this.theme.value = theme
    }

    override suspend fun markWelcomeSeen() {
        welcomeSeen.value = true
    }
}
