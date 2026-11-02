package dev.mathieu.youcandoit.ui

import dev.mathieu.youcandoit.domain.fakes.MutableClock
import dev.mathieu.youcandoit.domain.fakes.NOW
import dev.mathieu.youcandoit.settings.FakeSettings
import dev.mathieu.youcandoit.ui.welcome.WelcomeViewModel
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class WelcomeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val settings = FakeSettings()
    private val clock = MutableClock(NOW) // 2026-11-01, 07:00 in Montreal

    private fun viewModel() = WelcomeViewModel(settings, clock, ZoneId.of("America/Montreal"))

    @Test
    fun `shows the welcome screen until the user starts, and remembers the day`() = runTest {
        val vm = viewModel()
        assertEquals(true, vm.showWelcome.value)

        vm.onStart()

        assertEquals(false, vm.showWelcome.value)
        assertEquals(LocalDate.of(2026, 11, 1), settings.welcomeSeenOn.value)
    }

    @Test
    fun `stays hidden for the rest of the day it was seen`() = runTest {
        settings.welcomeSeenOn.value = LocalDate.of(2026, 11, 1)
        clock.advanceBy(Duration.ofHours(16)) // 23:00 local

        assertEquals(false, viewModel().showWelcome.value)
    }

    @Test
    fun `can be shown again on request, the same day, until the user starts again`() = runTest {
        settings.welcomeSeenOn.value = LocalDate.of(2026, 11, 1)
        val vm = viewModel()
        assertEquals(false, vm.showWelcome.value)

        vm.onShowAgain()
        assertEquals(true, vm.showWelcome.value)

        vm.onStart()
        assertEquals(false, vm.showWelcome.value)
    }

    @Test
    fun `shows again the next day, even if the app stayed open in the background`() = runTest {
        settings.welcomeSeenOn.value = LocalDate.of(2026, 11, 1)
        val vm = viewModel()
        assertEquals(false, vm.showWelcome.value)

        clock.advanceBy(Duration.ofHours(18)) // 01:00 local, Nov 2
        vm.onAppVisible()

        assertEquals(true, vm.showWelcome.value)
    }
}
