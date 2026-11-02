package dev.mathieu.youcandoit.update

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mathieu.youcandoit.settings.SettingsRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Google Play's flexible in-app update: offer a new version through Play's own dialog, let it download
 * in the background while the app stays usable, then offer a restart. Only works for installs from Play;
 * anywhere else the check fails quietly and nothing is shown.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val manager: AppUpdateManager,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val zone: ZoneId,
) : ViewModel() {
    private val _offer = MutableStateFlow<AppUpdateInfo?>(null)

    /** An update to offer now; the activity hands it to Play with [onShowOffer]. */
    val offer: StateFlow<AppUpdateInfo?> = _offer.asStateFlow()

    private val _readyToInstall = MutableStateFlow(false)

    /** Downloaded: a restart installs it. */
    val readyToInstall: StateFlow<Boolean> = _readyToInstall.asStateFlow()

    // Play's dialog pauses and resumes the app: offer at most once per launch.
    private var offered = false

    private val listener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) _readyToInstall.value = true
    }

    init {
        manager.registerListener(listener)
    }

    /** The app came to the screen: look for an update, or one that finished downloading meanwhile. */
    fun onAppVisible() {
        viewModelScope.launch {
            val info = try {
                manager.requestAppUpdateInfo()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch // not installed from Play, offline, or no Play Store
            }
            when {
                info.installStatus() == InstallStatus.DOWNLOADED -> _readyToInstall.value = true
                !offered &&
                    info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) &&
                    !declinedRecently() -> {
                    offered = true
                    _offer.value = info
                }
            }
        }
    }

    fun onShowOffer(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        val info = _offer.value ?: return
        _offer.value = null
        manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE))
    }

    /** "No thanks" in Play's dialog: don't ask again for a few days. */
    fun onOfferDeclined() {
        viewModelScope.launch { settings.markUpdateDeclined(today()) }
    }

    fun onRestart() {
        _readyToInstall.value = false
        manager.completeUpdate()
    }

    /** The "ready" message was dismissed: it comes back the next time the app is opened. */
    fun onRestartLater() {
        _readyToInstall.value = false
    }

    override fun onCleared() {
        manager.unregisterListener(listener)
    }

    private suspend fun declinedRecently(): Boolean {
        val declinedOn = settings.updateDeclinedOn.first() ?: return false
        return today() < declinedOn.plusDays(DAYS_BETWEEN_OFFERS)
    }

    private fun today(): LocalDate = clock.instant().atZone(zone).toLocalDate()

    private companion object {
        const val DAYS_BETWEEN_OFFERS = 3L
    }
}
