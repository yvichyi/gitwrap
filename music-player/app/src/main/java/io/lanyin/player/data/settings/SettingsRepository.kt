package io.lanyin.player.data.settings

import android.content.Context
import io.lanyin.player.core.model.Quality
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import io.lanyin.player.core.audio.EQ_FREQUENCIES
import io.lanyin.player.core.audio.EffectPreset
import io.lanyin.player.core.audio.EffectsSettings
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.lanyin.player.ui.theme.AppTheme
import io.lanyin.player.ui.theme.LyricSize
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
    val lyricSize: LyricSize = LyricSize.Default,
    /** The stream quality to ask NetEase for; it steps down when a song isn't available at it. */
    val quality: Quality = Quality.LOSSLESS,
    /** Downloads wait for Wi-Fi rather than use mobile data. */
    val downloadOnWifiOnly: Boolean = true,
    /** Tell NetEase what was listened to here, so the account's history and 听歌排行 include it. */
    val syncPlays: Boolean = true,
    /** 音效: off (原声) unless chosen. */
    val effects: EffectsSettings = EffectsSettings(),
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
                lyricSize = LyricSize.fromId(prefs[LYRIC_SIZE]),
                quality = Quality.fromLevel(prefs[QUALITY]) ?: Quality.LOSSLESS,
                downloadOnWifiOnly = prefs[DOWNLOAD_WIFI_ONLY] ?: true,
                syncPlays = prefs[SYNC_PLAYS] ?: true,
                effects = EffectsSettings(
                    preset = EffectPreset.fromId(prefs[FX_PRESET]),
                    custom = prefs[FX_CUSTOM]?.split(',')?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == EQ_FREQUENCIES.size }
                        ?: List(EQ_FREQUENCIES.size) { 0f },
                    bass = prefs[FX_BASS] ?: 0f,
                    width = prefs[FX_WIDTH] ?: 0f,
                    reverb = prefs[FX_REVERB] ?: 0f,
                ),
            )
        }

    suspend fun setTheme(theme: AppTheme) = dataStore.edit { it[THEME] = theme.id }

    suspend fun setPlayerSkin(skin: PlayerSkin) = dataStore.edit { it[PLAYER_SKIN] = skin.id }

    suspend fun setThemeMode(mode: ThemeMode) = dataStore.edit { it[THEME_MODE] = mode.id }

    suspend fun setAutoLandscape(enabled: Boolean) = dataStore.edit { it[AUTO_LANDSCAPE] = enabled }

    suspend fun setKeepScreenOnInLandscape(enabled: Boolean) = dataStore.edit { it[KEEP_SCREEN_ON] = enabled }

    suspend fun setLyricSize(size: LyricSize) = dataStore.edit { it[LYRIC_SIZE] = size.id }

    suspend fun setQuality(quality: Quality) = dataStore.edit { it[QUALITY] = quality.level }

    suspend fun setDownloadOnWifiOnly(enabled: Boolean) = dataStore.edit { it[DOWNLOAD_WIFI_ONLY] = enabled }

    suspend fun setSyncPlays(enabled: Boolean) = dataStore.edit { it[SYNC_PLAYS] = enabled }

    suspend fun setEffects(fx: EffectsSettings) = dataStore.edit {
        it[FX_PRESET] = fx.preset.id
        it[FX_CUSTOM] = fx.custom.joinToString(",")
        it[FX_BASS] = fx.bass
        it[FX_WIDTH] = fx.width
        it[FX_REVERB] = fx.reverb
    }

    private companion object {
        val THEME = stringPreferencesKey("app_skin")
        val PLAYER_SKIN = stringPreferencesKey("player_skin")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AUTO_LANDSCAPE = booleanPreferencesKey("auto_landscape")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on_landscape")
        val LYRIC_SIZE = stringPreferencesKey("lyric_size")
        val QUALITY = stringPreferencesKey("stream_quality")
        val DOWNLOAD_WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
        val SYNC_PLAYS = booleanPreferencesKey("sync_plays")
        val FX_PRESET = stringPreferencesKey("fx_preset")
        val FX_CUSTOM = stringPreferencesKey("fx_custom")
        val FX_BASS = floatPreferencesKey("fx_bass")
        val FX_WIDTH = floatPreferencesKey("fx_width")
        val FX_REVERB = floatPreferencesKey("fx_reverb")
    }
}
