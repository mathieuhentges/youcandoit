package dev.mathieu.youcandoit.ui.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mathieu.youcandoit.settings.SettingsRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Whether to show the welcome screen: once a day, or on request. Null while the saved setting is still loading. */
@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val zone: ZoneId,
) : ViewModel() {
    private val today = MutableStateFlow(today())
    private val replay = MutableStateFlow(false)

    val showWelcome: StateFlow<Boolean?> =
        combine(settings.welcomeSeenOn, today, replay) { seenOn, today, replay -> replay || seenOn != today }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The app came back to the screen: it may be a new day since it was last opened. */
    fun onAppVisible() {
        today.value = today()
    }

    /** The user asked to see the encouragement again. */
    fun onShowAgain() {
        replay.value = true
    }

    fun onStart() {
        replay.value = false
        viewModelScope.launch { settings.markWelcomeSeen(today.value) }
    }

    private fun today(): LocalDate = clock.instant().atZone(zone).toLocalDate()
}
