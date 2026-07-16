package net.igng.mcstatus.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

class SettingsRepository(
    private val context: Context,
) {
    private val sessions = SecureSessionStore(context)
    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { preferences ->
        val accentId = preferences[ACCENT_KEY]
        val active = sessions.activeAccount()
        AppSettings(
            vibrationEnabled = preferences[VIBRATION_ENABLED_KEY] ?: true,
            useSystemAccent = preferences[USE_SYSTEM_ACCENT_KEY] ?: true,
            accent = ThemeAccent.entries.firstOrNull { it.id == accentId } ?: ThemeAccent.TEAL,
            sessionToken = active?.token ?: preferences[LEGACY_SESSION_TOKEN_KEY],
            accountName = active?.displayName ?: preferences[LEGACY_ACCOUNT_NAME_KEY],
            accountUsername = active?.username,
            accounts = sessions.accounts(),
        )
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[VIBRATION_ENABLED_KEY] = enabled
        }
    }

    suspend fun setUseSystemAccent(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[USE_SYSTEM_ACCENT_KEY] = enabled
        }
    }

    suspend fun setAccent(accent: ThemeAccent) {
        context.settingsDataStore.edit { preferences ->
            preferences[ACCENT_KEY] = accent.id
        }
    }

    suspend fun saveAccount(account: SavedAccount) { sessions.save(account); notifyAccountChange() }
    suspend fun switchAccount(userId: Int) { sessions.switchTo(userId); notifyAccountChange() }
    suspend fun clearActiveAccount() { sessions.removeActive(); notifyAccountChange() }
    suspend fun migrateLegacyAccount(account: SavedAccount) { sessions.save(account); context.settingsDataStore.edit { it.remove(LEGACY_SESSION_TOKEN_KEY); it.remove(LEGACY_ACCOUNT_NAME_KEY); it[ACCOUNT_EPOCH_KEY] = (it[ACCOUNT_EPOCH_KEY] ?: 0) + 1 } }
    private suspend fun notifyAccountChange() { context.settingsDataStore.edit { it[ACCOUNT_EPOCH_KEY] = (it[ACCOUNT_EPOCH_KEY] ?: 0) + 1 } }

    private companion object {
        val VIBRATION_ENABLED_KEY = booleanPreferencesKey("vibration_enabled")
        val USE_SYSTEM_ACCENT_KEY = booleanPreferencesKey("use_system_accent")
        val ACCENT_KEY = stringPreferencesKey("accent")
        val LEGACY_SESSION_TOKEN_KEY = stringPreferencesKey("igng_session_token")
        val LEGACY_ACCOUNT_NAME_KEY = stringPreferencesKey("igng_account_name")
        val ACCOUNT_EPOCH_KEY = androidx.datastore.preferences.core.intPreferencesKey("account_epoch")
    }
}
