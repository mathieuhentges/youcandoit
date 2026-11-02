package dev.mathieu.youcandoit.update

import android.app.Application
import android.os.Looper
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.model.AppUpdateType
import dev.mathieu.youcandoit.domain.fakes.MutableClock
import dev.mathieu.youcandoit.domain.fakes.NOW
import dev.mathieu.youcandoit.settings.FakeSettings
import dev.mathieu.youcandoit.ui.MainDispatcherRule
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class UpdateViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val play = FakeAppUpdateManager(ApplicationProvider.getApplicationContext())
    private val settings = FakeSettings()
    private val clock = MutableClock(NOW) // 2026-11-01, 07:00 in Montreal

    /** Play's dialog is an activity; the fake manager only records that it was asked to show it. */
    private val launcher = object : ActivityResultLauncher<IntentSenderRequest>() {
        override val contract: ActivityResultContract<IntentSenderRequest, *> = ActivityResultContracts.StartIntentSenderForResult()
        override fun launch(input: IntentSenderRequest, options: ActivityOptionsCompat?) = Unit
        override fun unregister() = Unit
    }

    private fun viewModel() = UpdateViewModel(play, settings, clock, ZoneId.of("America/Montreal"))

    /** Play answers through the main thread. */
    private fun UpdateViewModel.appVisible() {
        onAppVisible()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `no update, nothing to show`() {
        play.setUpdateNotAvailable()
        val vm = viewModel()

        vm.appVisible()

        assertNull(vm.offer.value)
        assertFalse(vm.readyToInstall.value)
    }

    @Test
    fun `an update is offered through Play's dialog, downloads, then a restart installs it`() {
        play.setUpdateAvailable(2, AppUpdateType.FLEXIBLE)
        val vm = viewModel()

        vm.appVisible()
        assertNotNull(vm.offer.value)

        vm.onShowOffer(launcher)
        assertNull(vm.offer.value)
        assertTrue(play.isConfirmationDialogVisible)

        play.userAcceptsUpdate()
        play.downloadStarts()
        play.downloadCompletes()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(vm.readyToInstall.value)

        vm.onRestart()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(vm.readyToInstall.value)
        assertTrue(play.isInstallSplashScreenVisible)
    }

    @Test
    fun `offered once per launch, even though Play's dialog brings the app back to the screen`() {
        play.setUpdateAvailable(2, AppUpdateType.FLEXIBLE)
        val vm = viewModel()
        vm.appVisible()
        vm.onShowOffer(launcher)

        vm.appVisible()

        assertNull(vm.offer.value)
    }

    @Test
    fun `turned down, it is not offered again for three days`() {
        play.setUpdateAvailable(2, AppUpdateType.FLEXIBLE)
        viewModel().onOfferDeclined()
        assertEquals(LocalDate.of(2026, 11, 1), settings.updateDeclinedOn.value)

        clock.advanceBy(Duration.ofDays(2))
        val nextLaunch = viewModel()
        nextLaunch.appVisible()
        assertNull(nextLaunch.offer.value)

        clock.advanceBy(Duration.ofDays(1))
        val later = viewModel()
        later.appVisible()
        assertNotNull(later.offer.value)
    }

    @Test
    fun `a download that finished while the app was closed offers the restart again`() {
        play.setUpdateAvailable(2, AppUpdateType.FLEXIBLE)
        val first = viewModel()
        first.appVisible()
        first.onShowOffer(launcher)
        play.userAcceptsUpdate()
        play.downloadStarts()
        play.downloadCompletes()
        first.onRestartLater()

        val next = viewModel()
        next.appVisible()

        assertTrue(next.readyToInstall.value)
    }
}
