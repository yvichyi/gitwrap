package io.lanyin.player.playback

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.media3.exoplayer.scheduler.Scheduler
import io.lanyin.player.LanyinApplication
import io.lanyin.player.MainActivity
import io.lanyin.player.R
import io.lanyin.player.core.model.Song
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** Where a download stands. */
enum class DownloadState { Queued, Downloading, Done, Failed }

/** A song being downloaded, or downloaded. [progress] is 0..1. */
data class DownloadItem(val song: Song, val state: DownloadState, val progress: Float, val bytes: Long, val startedAt: Long)

/** How [DownloadItem.state] reads in lists. [waitingForWifi] when Wi-Fi is required and absent. */
fun DownloadItem.label(waitingForWifi: Boolean): String = when (state) {
    DownloadState.Queued -> if (waitingForWifi) "等待 Wi-Fi" else "等待下载"
    DownloadState.Downloading -> "${(progress * 100).toInt()}%"
    DownloadState.Done -> ""
    DownloadState.Failed -> "下载失败"
}

/**
 * Songs kept on the phone, through Media3's download manager: a foreground service with a
 * progress notification runs them, so they carry on in the background and resume after a
 * reboot. The audio goes into [AudioPipeline.downloadCache], where playback finds it.
 */
@OptIn(UnstableApi::class)
class DownloadRepository(context: Context, private val audio: AudioPipeline, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())

    /** Everything downloaded or on its way, newest first. */
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    private val _done = MutableStateFlow<Set<Long>>(emptySet())
    val done: StateFlow<Set<Long>> = _done.asStateFlow()

    private val _waitingForWifi = MutableStateFlow(false)
    val waitingForWifi: StateFlow<Boolean> = _waitingForWifi.asStateFlow()

    private val byId = LinkedHashMap<String, Download>()
    /** Each download's song, decoded once: publishing runs on the main thread every 700 ms while downloading. */
    private val songs = HashMap<String, Song?>()
    private var polling: Job? = null

    /** Created on first use, on the main thread; the service shares it. */
    val manager: DownloadManager by lazy {
        DownloadManager(appContext, audio.database, audio.downloadCache, audio.downloadFactory, Executors.newFixedThreadPool(2)).apply {
            maxParallelDownloads = 2
            addListener(listener)
        }
    }

    private val listener = object : DownloadManager.Listener {
        override fun onInitialized(downloadManager: DownloadManager) = reload()

        override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) {
            byId[download.request.id] = download
            publish()
        }

        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
            byId.remove(download.request.id)
            songs.remove(download.request.id)
            publish()
        }

        override fun onWaitingForRequirementsChanged(downloadManager: DownloadManager, waitingForRequirements: Boolean) = publish()
    }

    /** Loads what was downloaded before; call once at startup, on the main thread. */
    fun start() {
        if (manager.isInitialized) reload()
    }

    /** Wi-Fi is required for downloads and there is none right now. */
    fun wifiMissing(): Boolean = manager.notMetRequirements and Requirements.NETWORK_UNMETERED != 0

    fun setWifiOnly(wifiOnly: Boolean) {
        manager.requirements = Requirements(if (wifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK)
        publish()
    }

    /** Queues [songs] that aren't downloaded or on their way already; returns how many were queued. */
    fun download(songs: List<Song>): Int {
        val fresh = songs.filter { s -> s.id != 0L && byId[s.id.toString()]?.state.let { it == null || it == Download.STATE_FAILED } }
        if (fresh.isEmpty()) return 0
        // Straight to the manager, then one start of the service to carry them on in the background
        // (a start per song sent the service one intent per song for 下载全部).
        fresh.forEach { manager.addDownload(request(it)) }
        runCatching { DownloadService.start(appContext, DownloadsService::class.java) }
        return fresh.size
    }

    fun remove(songId: Long) {
        DownloadService.sendRemoveDownload(appContext, DownloadsService::class.java, songId.toString(), false)
    }

    /** Removes every download, finished or not, files and records alike. */
    fun removeAll() {
        DownloadService.sendRemoveAllDownloads(appContext, DownloadsService::class.java, false)
    }

    private fun request(song: Song): DownloadRequest =
        DownloadRequest.Builder(song.id.toString(), AudioPipeline.uriFor(song.id.toString()))
            .setCustomCacheKey(AudioPipeline.downloadKey(song.id))
            .setData(json.encodeToString(Song.serializer(), song).toByteArray())
            .build()

    private fun reload() {
        byId.clear()
        manager.downloadIndex.getDownloads().use { cursor ->
            while (cursor.moveToNext()) cursor.download.let { byId[it.request.id] = it }
        }
        publish()
    }

    private fun publish() {
        // The manager's live copies carry the latest progress.
        manager.currentDownloads.forEach { byId[it.request.id] = it }
        val items = byId.values.mapNotNull { it.toItem() }.sortedByDescending { it.startedAt }
        _items.value = items
        _done.value = items.filter { it.state == DownloadState.Done }.mapTo(HashSet()) { it.song.id }
        _waitingForWifi.value = manager.isWaitingForRequirements && manager.notMetRequirements and Requirements.NETWORK_UNMETERED != 0
        // Progress isn't announced; look again while something is downloading.
        if (items.any { it.state == DownloadState.Downloading }) {
            if (polling?.isActive != true) {
                polling = scope.launch {
                    while (isActive) {
                        delay(700)
                        publish()
                    }
                }
            }
        } else {
            polling?.cancel()
            polling = null
        }
    }

    private fun Download.toItem(): DownloadItem? {
        val song = songs.getOrPut(request.id) { runCatching { json.decodeFromString(Song.serializer(), String(request.data)) }.getOrNull() } ?: return null
        val state = when (state) {
            Download.STATE_DOWNLOADING -> DownloadState.Downloading
            Download.STATE_COMPLETED -> DownloadState.Done
            Download.STATE_FAILED -> DownloadState.Failed
            Download.STATE_REMOVING -> return null
            else -> DownloadState.Queued
        }
        val progress = if (state == DownloadState.Done) 1f else (percentDownloaded.takeIf { it >= 0 } ?: 0f) / 100f
        return DownloadItem(song, state, progress, bytesDownloaded, startTimeMs)
    }
}

/** Runs downloads in the foreground, with their progress in the notification shade. */
@OptIn(UnstableApi::class)
class DownloadsService : DownloadService(
    NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.downloads_channel,
    0,
) {
    private val notifications by lazy { DownloadNotificationHelper(this, CHANNEL_ID) }

    override fun getDownloadManager(): DownloadManager = (application as LanyinApplication).container.downloads.manager

    override fun getScheduler(): Scheduler = PlatformScheduler(this, JOB_ID)

    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val active = downloads.count { it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_QUEUED }
        return notifications.buildProgressNotification(
            this, R.drawable.ic_notify_download, open,
            if (active > 0) "正在下载 $active 首" else null,
            downloads, notMetRequirements,
        )
    }

    private companion object {
        const val NOTIFICATION_ID = 2
        const val JOB_ID = 7
        const val CHANNEL_ID = "downloads"
    }
}
