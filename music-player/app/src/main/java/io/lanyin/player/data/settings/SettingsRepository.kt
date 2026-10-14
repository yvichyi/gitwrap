package io.lanyin.player.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.lanyin.player.ui.theme.AppSkin
import io.lanyin.player.ui.theme.PlayerSkin
import io.lanyin.player.ui.theme.ThemeMode
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class UiSettings(
    val appSkin: AppSkin = AppSkin.Default,
    val playerSkin: PlayerSkin = PlayerSkin.Default,
    val themeMode: ThemeMode = ThemeMode.Default,
)

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<UiSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            UiSettings(
                appSkin = AppSkin.fromId(prefs[APP_SKIN]),
                playerSkin = PlayerSkin.fromId(prefs[PLAYER_SKIN]),
                themeMode = ThemeMode.fromId(prefs[THEME_MODE]),
            )
        }

    suspend fun setAppSkin(skin: AppSkin) = dataStore.edit { it[APP_SKIN] = skin.id }

    suspend fun setPlayerSkin(skin: PlayerSkin) = dataStore.edit { it[PLAYER_SKIN] = skin.id }

    suspend fun setThemeMode(mode: ThemeMode) = dataStore.edit { it[THEME_MODE] = mode.id }

    private companion object {
        val APP_SKIN = stringPreferencesKey("app_skin")
        val PLAYER_SKIN = stringPreferencesKey("player_skin")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
