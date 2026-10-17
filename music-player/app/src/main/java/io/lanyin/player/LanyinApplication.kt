package io.lanyin.player

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import io.lanyin.player.core.netease.NeteaseApi
import io.lanyin.player.core.netease.NeteaseClient
import io.lanyin.player.data.account.AccountRepository
import io.lanyin.player.data.library.LibraryRepository
import io.lanyin.player.data.library.RecentRepository
import io.lanyin.player.playback.AudioEngine
import io.lanyin.player.playback.AudioPipeline
import io.lanyin.player.playback.DownloadRepository
import io.lanyin.player.ui.Notices
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient

class LanyinApplication : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.account.start()
        container.player.connect()
        container.downloads.start()
        // Whether any of the app's screens is showing; 桌面歌词 stays away while one is.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var started = 0

            override fun onActivityStarted(activity: Activity) {
                started++
                container.appVisible.value = true
            }

            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0)
                container.appVisible.value = started > 0
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        container.desktopLyrics.start()
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
    val netease = NeteaseApi(
        NeteaseClient(http, KeystoreSessionStore(File(context.noBackupFilesDir, "netease-session.bin"))),
        io.lanyin.player.core.netease.NosUploader(http),
    )
    val account = AccountRepository(netease, scope)
    val library = LibraryRepository(netease, account, scope)
    val streams = StreamResolver(netease)
    val audio = AudioPipeline(context, http, streams)
    val engine = AudioEngine()
    val recent = RecentRepository(File(context.filesDir, "recent.json"), scope)
    val searchHistory = io.lanyin.player.data.library.SearchHistory(File(context.filesDir, "search-history.json"), scope)
    val downloads = DownloadRepository(context, audio, scope)
    val notices = Notices()
    val nowLyrics = io.lanyin.player.playback.NowPlayingLyrics()

    /** The 状态栏歌词 settings, read by the playback service as it shows each line. */
    @Volatile
    var tickerLyrics = true
        private set

    @Volatile
    var capsuleLyrics = true
        private set
    val uploads = io.lanyin.player.data.library.CloudUploads(scope, netease::uploadToCloud, onUploaded = library::cloudChanged)
    val storage = io.lanyin.player.playback.StorageManager(
        audio, downloads,
        object : io.lanyin.player.playback.ImageStore {
            override fun size() = SingletonImageLoader.get(context).diskCache?.size ?: 0L
            override fun clear() {
                val loader = SingletonImageLoader.get(context)
                loader.memoryCache?.clear()
                loader.diskCache?.clear()
            }
        },
    )

    @Volatile
    private var syncPlays = true
    val player = MediaPlayerState(
        context, netease, streams, library, scope, recent,
        syncPlays = { syncPlays },
        feed = engine.analyzer,
        setPreferred = { q ->
            audio.preferred = q
            scope.launch { settings.setQuality(q) }
        },
        nowLyrics = nowLyrics,
    )
    val services = AppServices(netease, account, library, player, recent, downloads, notices, engine, uploads, storage, searchHistory)

    /** Whether one of the app's screens is showing. */
    val appVisible = MutableStateFlow(false)
    val desktopLyrics = io.lanyin.player.ui.desktop.DesktopLyricsWindow(context, scope, player, settings, appVisible)

    init {
        // Stream quality, downloads over Wi-Fi only and play records follow the settings.
        scope.launch {
            settings.settings.collect {
                audio.preferred = it.quality
                syncPlays = it.syncPlays
                engine.effects.settings = it.effects
                downloads.setWifiOnly(it.downloadOnWifiOnly)
                tickerLyrics = it.statusBarLyrics
                capsuleLyrics = it.statusBarLyrics && it.lyricCapsule
            }
        }
        scope.launch {
            settings.settings.map { it.cacheLimit }.distinctUntilChanged().collect { limit ->
                withContext(Dispatchers.IO) { audio.setCacheLimit(limit) }
            }
        }
    }
}
