package io.lanyin.player.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.lanyin.player.ui.theme.AppTheme
import io.lanyin.player.ui.theme.PlayerSkin
import io.lanyin.player.ui.theme.ThemeMode
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class UiSettings(
    val theme: AppTheme = AppTheme.Default,
    val playerSkin: PlayerSkin = PlayerSkin.Default,
    val themeMode: ThemeMode = ThemeMode.Default,
    /** Rotate to landscape whenever the player opens. */
    val autoLandscape: Boolean = false,
    val keepScreenOnInLandscape: Boolean = true,
)

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<UiSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            UiSettings(
                // Stored under the old "app_skin" key so choices made in M0 carry over.
                theme = AppTheme.fromId(prefs[THEME]),
                playerSkin = PlayerSkin.fromId(prefs[PLAYER_SKIN]),
                themeMode = ThemeMode.fromId(prefs[THEME_MODE]),
                autoLandscape = prefs[AUTO_LANDSCAPE] ?: false,
                keepScreenOnInLandscape = prefs[KEEP_SCREEN_ON] ?: true,
            )
        }

    suspend fun setTheme(theme: AppTheme) = dataStore.edit { it[THEME] = theme.id }

    suspend fun setPlayerSkin(skin: PlayerSkin) = dataStore.edit { it[PLAYER_SKIN] = skin.id }

    suspend fun setThemeMode(mode: ThemeMode) = dataStore.edit { it[THEME_MODE] = mode.id }

    suspend fun setAutoLandscape(enabled: Boolean) = dataStore.edit { it[AUTO_LANDSCAPE] = enabled }

    suspend fun setKeepScreenOnInLandscape(enabled: Boolean) = dataStore.edit { it[KEEP_SCREEN_ON] = enabled }

    private companion object {
        val THEME = stringPreferencesKey("app_skin")
        val PLAYER_SKIN = stringPreferencesKey("player_skin")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AUTO_LANDSCAPE = booleanPreferencesKey("auto_landscape")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on_landscape")
    }
}
