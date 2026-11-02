package dev.mathieu.youcandoit.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SettingsRepository {
    val theme: Flow<ThemePreference>
    suspend fun setTheme(theme: ThemePreference)

    /** False until the welcome screen has been dismissed once. */
    val welcomeSeen: Flow<Boolean>
    suspend fun markWelcomeSeen()
}

class DataStoreSettingsRepository @Inject constructor(
    private val store: DataStore<Preferences>,
) : SettingsRepository {
    override val theme: Flow<ThemePreference> = store.data.map { ThemePreference.fromKey(it[THEME]) }

    override suspend fun setTheme(theme: ThemePreference) {
        store.edit { it[THEME] = theme.key }
    }

    override val welcomeSeen: Flow<Boolean> = store.data.map { it[WELCOME_SEEN] ?: false }

    override suspend fun markWelcomeSeen() {
        store.edit { it[WELCOME_SEEN] = true }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val WELCOME_SEEN = booleanPreferencesKey("welcome_seen")
    }
}
