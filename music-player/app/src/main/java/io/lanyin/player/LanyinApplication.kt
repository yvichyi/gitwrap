package io.lanyin.player

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import io.lanyin.player.core.netease.NeteaseApi
import io.lanyin.player.core.netease.NeteaseClient
import io.lanyin.player.data.account.AccountRepository
import io.lanyin.player.data.library.LibraryRepository
import io.lanyin.player.playback.AudioPipeline
import io.lanyin.player.playback.MediaPlayerState
import io.lanyin.player.playback.StreamResolver
import kotlinx.coroutines.launch
import io.lanyin.player.data.secure.KeystoreSessionStore
import io.lanyin.player.data.settings.SettingsRepository
import io.lanyin.player.data.settings.settingsDataStore
import io.lanyin.player.ui.AppServices
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

class LanyinApplication : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.account.start()
        container.player.connect()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.http })) }
        .crossfade(true)
        .build()
}

/** Hand-wired dependencies: few enough that a DI framework would cost more than it saves. */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = SettingsRepository(context.settingsDataStore)
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    val netease = NeteaseApi(NeteaseClient(http, KeystoreSessionStore(File(context.noBackupFilesDir, "netease-session.bin"))))
    val account = AccountRepository(netease, scope)
    val library = LibraryRepository(netease, account, scope)
    val streams = StreamResolver(netease)
    val audio = AudioPipeline(context, http, streams)
    val player = MediaPlayerState(context, netease, streams, library, scope)
    val services = AppServices(netease, account, library, player)

    init {
        // The stream quality follows the setting.
        scope.launch { settings.settings.collect { audio.preferred = it.quality } }
    }
}
