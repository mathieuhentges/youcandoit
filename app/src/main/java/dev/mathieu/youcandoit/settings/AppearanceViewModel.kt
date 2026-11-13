package dev.mathieu.youcandoit.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mathieu.youcandoit.domain.model.Priority
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {
    val theme: StateFlow<ThemePreference> =
        settings.theme.stateIn(viewModelScope, SharingStarted.Eagerly, ThemePreference.SYSTEM)

    val priorityColors: StateFlow<PriorityColors> =
        settings.priorityColors.stateIn(viewModelScope, SharingStarted.Eagerly, PriorityColors.DEFAULT)

    fun onThemeSelected(theme: ThemePreference) {
        viewModelScope.launch { settings.setTheme(theme) }
    }

    /** [argb] null puts the priority's original color back. */
    fun onPriorityColorSelected(priority: Priority, argb: Int?) {
        viewModelScope.launch { settings.setPriorityColors(priorityColors.value.with(priority, argb)) }
    }

    fun onResetPriorityColors() {
        viewModelScope.launch { settings.setPriorityColors(PriorityColors.DEFAULT) }
    }
}
