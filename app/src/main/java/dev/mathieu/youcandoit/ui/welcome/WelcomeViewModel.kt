package dev.mathieu.youcandoit.ui.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mathieu.youcandoit.settings.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Whether to show the welcome screen: null while the saved setting is still loading. */
@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {
    val showWelcome: StateFlow<Boolean?> =
        settings.welcomeSeen.map { seen -> !seen }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun onStart() {
        viewModelScope.launch { settings.markWelcomeSeen() }
    }
}
