package dev.mathieu.youcandoit.ui

import dev.mathieu.youcandoit.settings.FakeSettings
import dev.mathieu.youcandoit.ui.welcome.WelcomeViewModel
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class WelcomeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `shows the welcome screen once, until the user starts`() = runTest {
        val settings = FakeSettings()
        val vm = WelcomeViewModel(settings)
        assertEquals(true, vm.showWelcome.value)

        vm.onStart()

        assertEquals(false, vm.showWelcome.value)
        assertEquals(true, settings.welcomeSeen.value)
    }

    @Test
    fun `stays hidden when it was already seen`() = runTest {
        val settings = FakeSettings().apply { welcomeSeen.value = true }

        assertEquals(false, WelcomeViewModel(settings).showWelcome.value)
    }
}
