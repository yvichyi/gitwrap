package io.lanyin.player

import android.app.Application
import android.content.Context
import io.lanyin.player.data.settings.SettingsRepository
import io.lanyin.player.data.settings.settingsDataStore

class LanyinApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Hand-wired dependencies; swapped for Hilt once the network and playback layers land. */
class AppContainer(context: Context) {
    val settings = SettingsRepository(context.settingsDataStore)
}
