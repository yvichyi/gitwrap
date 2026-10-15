package io.lanyin.player.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.github.takahirom.roborazzi.captureRoboImage
import io.lanyin.player.data.settings.SettingsRepository
import io.lanyin.player.data.settings.UiSettings
import io.lanyin.player.ui.LanyinApp
import io.lanyin.player.ui.login.LoginScreen
import io.lanyin.player.ui.login.QrMatrix
import io.lanyin.player.ui.login.QrPhase
import io.lanyin.player.core.netease.QrStatus
import io.lanyin.player.core.model.UserProfile
import io.lanyin.player.data.account.AccountState
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import io.lanyin.player.ui.player.DemoPlayerState
import io.lanyin.player.ui.player.PlayerScreen
import io.lanyin.player.ui.player.rememberSpinState
import io.lanyin.player.ui.theme.AppTheme
import io.lanyin.player.ui.theme.LanyinTheme
import io.lanyin.player.ui.theme.PlayerSkin
import io.lanyin.player.ui.theme.ThemeMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real app on the JVM so the UI can be reviewed without an emulator.
 * Record with `./gradlew recordRoborazziDebug`; images land in build/outputs/roborazzi.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PORTRAIT)
class ScreenshotTest {

    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()

    private fun repo() = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.Unconfined)) {
            File(tmp.root, "settings.preferences_pb")
        },
    )

    private fun capture(name: String, theme: AppTheme, mode: ThemeMode, settle: Long = 1_500, content: @Composable () -> Unit) {
        // Spinning records and drifting backdrops never go idle, so drive the clock by hand.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LanyinTheme(theme = theme, mode = mode) {
                // Pages are transparent and sit on the app's backdrop, as in LanyinApp.
                androidx.compose.foundation.layout.Box {
                    io.lanyin.player.ui.design.Backdrop(androidx.compose.ui.Modifier.matchParentSize())
                    content()
                }
            }
        }
        compose.mainClock.advanceTimeBy(settle)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun playingAt(ms: Long, track: Int = 0) = DemoPlayerState().apply {
        repeat(track) { skipNext() }
        togglePlay()
        seekTo(ms)
    }

    @Composable
    private fun Player(skin: PlayerSkin, state: DemoPlayerState = playingAt(14_000)) {
        PlayerScreen(state, skin, rememberSpinState(state.isPlaying), keepScreenOnInLandscape = true, onCollapse = {})
    }

    private fun app(theme: AppTheme, mode: ThemeMode, skin: PlayerSkin = PlayerSkin.VINYL) {
        val repo = repo()
        capture("home_${theme.name.lowercase()}_${mode.id}", theme, mode) {
            LanyinApp(UiSettings(theme = theme, themeMode = mode, playerSkin = skin), repo, player = playingAt(14_000), homePreview = homePreview(signedIn = false))
        }
    }

    /** What the home page shows once its shelves have loaded. */
    private fun homePreview(signedIn: Boolean) = io.lanyin.player.ui.home.HomePreview(
        recommended = samplePlaylists.drop(1),
        charts = listOf("飙升榜" to "刚刚更新", "新歌榜" to "刚刚更新", "原创榜" to "每周四更新", "热歌榜" to "每周四更新").mapIndexed { i, (name, note) ->
            io.lanyin.player.data.library.ChartPreview(
                io.lanyin.player.core.model.Playlist(800L + i, name, null, 100, updateNote = note),
                sampleSongs.drop(i).take(3),
            )
        },
        picks = if (signedIn) io.lanyin.player.data.library.Picks("今日推荐", "按你的口味挑的", sampleSongs.take(6))
        else io.lanyin.player.data.library.Picks("热歌榜", "大家都在听", sampleSongs.take(6)),
    )

    // 墨, the flagship, page by page in light and dark.
    @Test fun ink_home_light() = app(AppTheme.INK, ThemeMode.LIGHT)
    @Test fun ink_search_light() = capture("ink_search_light", AppTheme.INK, ThemeMode.LIGHT) { io.lanyin.player.ui.search.SearchScreen() }
    @Test fun ink_library_light() = capture("ink_library_light", AppTheme.INK, ThemeMode.LIGHT) { io.lanyin.player.ui.library.LibraryScreen(onLogin = {}) }
    @Test fun ink_library_dark() = capture("ink_library_dark", AppTheme.INK, ThemeMode.DARK) { io.lanyin.player.ui.library.LibraryScreen(onLogin = {}) }
    @Test fun ink_login_light() = capture("ink_login_light", AppTheme.INK, ThemeMode.LIGHT) { LoginScreen(showingQr(), onBack = {}) }
    @Test fun ink_settings_light() {
        val repo = repo()
        capture("ink_settings_light", AppTheme.INK, ThemeMode.LIGHT) {
            io.lanyin.player.ui.settings.SettingsScreen(UiSettings(theme = AppTheme.INK, themeMode = ThemeMode.LIGHT), repo, onBack = {})
        }
    }
    @Config(qualifiers = "w392dp-h1500dp-port-xhdpi")
    @Test fun ink_settings_full() {
        val repo = repo()
        capture("ink_settings_full", AppTheme.INK, ThemeMode.LIGHT) {
            io.lanyin.player.ui.settings.SettingsScreen(UiSettings(theme = AppTheme.INK, themeMode = ThemeMode.LIGHT), repo, onBack = {})
        }
    }
    @Test fun ink_port_vinyl_light() = capture("ink_port_vinyl_light", AppTheme.INK, ThemeMode.LIGHT) { Player(PlayerSkin.VINYL) }
    @Test fun ink_port_cover_dark() = capture("ink_port_cover_dark", AppTheme.INK, ThemeMode.DARK) { Player(PlayerSkin.COVER) }

    @Config(qualifiers = LANDSCAPE)
    @Test fun ink_land_vinyl_dark() = capture("ink_land_vinyl_dark", AppTheme.INK, ThemeMode.DARK) { Player(PlayerSkin.VINYL) }

    @Config(qualifiers = LANDSCAPE)
    @Test fun ink_land_lyrics_light() = capture("ink_land_lyrics_light", AppTheme.INK, ThemeMode.LIGHT) { Player(PlayerSkin.LYRICS) }

    @Test fun ink_transition_light() {
        val repo = repo()
        capture("ink_transition_0", AppTheme.INK, ThemeMode.LIGHT) {
            LanyinApp(UiSettings(theme = AppTheme.INK, themeMode = ThemeMode.LIGHT), repo, player = playingAt(14_000))
        }
        compose.onNodeWithTag("miniPlayer").performClick()
        compose.mainClock.advanceTimeBy(140)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/ink_transition_1.png")
    }

    // 暮, page by page.
    @Test fun dusk_home_dark() = app(AppTheme.DUSK, ThemeMode.DARK)
    @Test fun dusk_search_light() = capture("dusk_search_light", AppTheme.DUSK, ThemeMode.LIGHT) { io.lanyin.player.ui.search.SearchScreen() }
    @Test fun dusk_library_light() = capture("dusk_library_light", AppTheme.DUSK, ThemeMode.LIGHT) { io.lanyin.player.ui.library.LibraryScreen(onLogin = {}) }
    @Test fun dusk_login_dark() = capture("dusk_login_dark", AppTheme.DUSK, ThemeMode.DARK) { LoginScreen(showingQr(), onBack = {}) }
    @Test fun dusk_settings_light() {
        val repo = repo()
        capture("dusk_settings_light", AppTheme.DUSK, ThemeMode.LIGHT) {
            io.lanyin.player.ui.settings.SettingsScreen(UiSettings(theme = AppTheme.DUSK, themeMode = ThemeMode.LIGHT), repo, onBack = {})
        }
    }
    @Test fun dusk_port_cassette_light() = capture("dusk_port_cassette_light", AppTheme.DUSK, ThemeMode.LIGHT) { Player(PlayerSkin.CASSETTE) }
    @Test fun dusk_port_vinyl_dark() = capture("dusk_port_vinyl_dark", AppTheme.DUSK, ThemeMode.DARK) { Player(PlayerSkin.VINYL) }

    @Config(qualifiers = LANDSCAPE)
    @Test fun dusk_land_cover_dark() = capture("dusk_land_cover_dark", AppTheme.DUSK, ThemeMode.DARK) { Player(PlayerSkin.COVER) }

    // Login: the real code in each state, and the signed-in pages.
    private val demoQrUrl = "https://music.163.com/login?codekey=7f2c1d9e-3b4a-4c5d-9e8f-0a1b2c3d4e5f"
    private fun showingQr(scanned: QrStatus.Scanned? = null) = QrPhase.Showing(demoQrUrl, QrMatrix.encode(demoQrUrl), scanned)
    private val demoProfile = UserProfile(1, "潮汐里的鲸", null, 11)

    /** What a phone camera would read off the screenshot. */
    private fun decodeQr(name: String): String {
        val image = android.graphics.BitmapFactory.decodeFile(File("build/outputs/roborazzi/$name.png").absolutePath)
        val pixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(image.width, image.height, pixels)))
        return QRCodeReader().decode(bitmap, mapOf(com.google.zxing.DecodeHintType.TRY_HARDER to true)).text
    }

    // The on-screen code must scan whatever the theme does around it.
    @Test fun login_qr_scans_ink() {
        capture("login_qr_ink", AppTheme.INK, ThemeMode.LIGHT) { LoginScreen(showingQr(), onBack = {}) }
        assertEquals(demoQrUrl, decodeQr("login_qr_ink"))
    }
    @Test fun login_qr_scans_neon() {
        capture("login_qr_neon", AppTheme.NEON, ThemeMode.DARK) { LoginScreen(showingQr(), onBack = {}) }
        assertEquals(demoQrUrl, decodeQr("login_qr_neon"))
    }
    @Test fun login_dusk_dark_scanned() = capture("login_dusk_dark_scanned", AppTheme.DUSK, ThemeMode.DARK) {
        LoginScreen(showingQr(QrStatus.Scanned("潮汐里的鲸", null)), onBack = {})
    }
    @Test fun login_grid_expired() = capture("login_grid_expired", AppTheme.GRID, ThemeMode.LIGHT) {
        LoginScreen(QrPhase.Error("二维码过期了", expired = true), onBack = {})
    }
    @Test fun login_dusk_saved() = capture("login_dusk_saved", AppTheme.DUSK, ThemeMode.LIGHT) {
        LoginScreen(showingQr(), onBack = {}, saved = true, notice = "已存到相册「澜音」。打开网易云 → 扫一扫 → 右上角「相册」选它")
    }
    @Test fun login_ink_success() = capture("login_ink_success", AppTheme.INK, ThemeMode.LIGHT) {
        LoginScreen(QrPhase.Success, onBack = {})
    }
    @Test fun library_ink_logged_in() = capture("library_ink_logged_in", AppTheme.INK, ThemeMode.LIGHT) {
        io.lanyin.player.ui.library.LibraryScreen(onLogin = {}, account = AccountState.LoggedIn(demoProfile))
    }
    @Test fun library_dusk_logged_in() = capture("library_dusk_logged_in", AppTheme.DUSK, ThemeMode.DARK) {
        io.lanyin.player.ui.library.LibraryScreen(onLogin = {}, account = AccountState.LoggedIn(demoProfile))
    }
    @Test fun home_grid_logged_in() {
        val repo = repo()
        capture("home_grid_logged_in", AppTheme.GRID, ThemeMode.LIGHT) {
            LanyinApp(
                UiSettings(theme = AppTheme.GRID, themeMode = ThemeMode.LIGHT), repo, player = playingAt(14_000),
                accountOverride = AccountState.LoggedIn(demoProfile), homePreview = homePreview(signedIn = true),
            )
        }
    }
    @Config(qualifiers = "w392dp-h1700dp-port-xhdpi")
    @Test fun settings_dusk_logged_in() {
        val repo = repo()
        capture("settings_dusk_logged_in", AppTheme.DUSK, ThemeMode.DARK) {
            io.lanyin.player.ui.settings.SettingsScreen(UiSettings(theme = AppTheme.DUSK, themeMode = ThemeMode.DARK), repo, onBack = {}, account = AccountState.LoggedIn(demoProfile))
        }
    }

    // Real content pages, fed with sample data.
    private val sampleSongs = listOf(
        Triple("潮水退去以后", "澜音", "潮汐"), Triple("夜航船", "北岸乐队", "夜航"), Triple("海边的风", "林间 / 小舟", "夏至"),
        Triple("落日磁带", "澜音乐队", "黄昏"), Triple("Midnight Ferry", "Harbor Lights", "Night Lines"), Triple("月光下的鲸", "潮汐里的鲸", "深海"),
        Triple("雨停以后", "北岸乐队", "夜航"), Triple("慢慢来", "林间", "夏至"), Triple("一首名字特别长、用来看看排版会不会被挤坏的歌", "很多很多位合作歌手", "排版测试"),
    ).mapIndexed { i, (name, artist, album) ->
        io.lanyin.player.core.model.Song(
            id = 100L + i, name = name,
            artists = artist.split(" / ").mapIndexed { j, a -> io.lanyin.player.core.model.Artist(j.toLong(), a) },
            album = io.lanyin.player.core.model.Album(i.toLong(), album, null), durationMs = 200_000L + i * 7_000,
            fee = if (i % 4 == 1) 1 else 8, alias = if (i == 4) "午夜渡轮" else null,
        )
    }
    private val samplePlaylists = listOf(
        "我喜欢的音乐" to 5, "深夜写代码", "雨天的歌", "通勤路上", "周末慢慢听", "收藏：城市民谣精选",
    ).mapIndexed { i, entry ->
        val (name, special) = if (entry is Pair<*, *>) entry.first as String to entry.second as Int else entry as String to 0
        io.lanyin.player.core.model.Playlist(
            id = 900L + i, name = name, coverUrl = null, trackCount = 12 + i * 37,
            creatorId = if (i == 5) 2 else 1, creatorName = if (i == 5) "城南" else "潮汐里的鲸", specialType = special, subscribed = i == 5,
        )
    }
    private fun playingSample() = playingAt(14_000)

    @Test fun playlist_dusk_dark() = capture("playlist_dusk_dark", AppTheme.DUSK, ThemeMode.DARK) {
        io.lanyin.player.ui.music.SongListScreen(
            "深夜写代码", "潮汐里的鲸 · 写给夜里还亮着的屏幕",
            io.lanyin.player.ui.music.ListHeader.Cover(null, 7),
            io.lanyin.player.ui.music.Load.Ready(sampleSongs), playingId = 102, isPlaying = true,
            onBack = {}, onPlay = { _, _ -> }, onRetry = {}, likedIds = setOf(100, 105),
        )
    }
    @Test fun daily_grid() = capture("daily_grid", AppTheme.GRID, ThemeMode.LIGHT) {
        io.lanyin.player.ui.music.SongListScreen(
            "每日推荐", "根据你的口味生成，每天 6:00 更新",
            io.lanyin.player.ui.music.ListHeader.Day(15, "10月 · 周四"),
            io.lanyin.player.ui.music.Load.Ready(sampleSongs), playingId = null, isPlaying = false,
            onBack = {}, onPlay = { _, _ -> }, onRetry = {},
        )
    }
    @Test fun playlist_ink_loading() = capture("playlist_ink_failed", AppTheme.INK, ThemeMode.LIGHT) {
        io.lanyin.player.ui.music.SongListScreen(
            "我喜欢的音乐", null, io.lanyin.player.ui.music.ListHeader.Cover(null, 3),
            io.lanyin.player.ui.music.Load.Failed("连不上网易云，检查一下网络"), playingId = null, isPlaying = false,
            onBack = {}, onPlay = { _, _ -> }, onRetry = {},
        )
    }
    @Test fun search_ink_results() = capture("search_ink_results", AppTheme.INK, ThemeMode.LIGHT) {
        io.lanyin.player.ui.search.SearchScreen(playingSample(), initialQuery = "夜", initialResults = sampleSongs.take(6))
    }
    @Test fun library_dusk_playlists() = capture("library_dusk_playlists", AppTheme.DUSK, ThemeMode.LIGHT) {
        io.lanyin.player.ui.library.LibraryScreen(onLogin = {}, account = AccountState.LoggedIn(demoProfile), previewPlaylists = samplePlaylists)
    }
    @Test fun home_neon_nothing_playing() {
        val repo = repo()
        capture("home_neon_nothing_playing", AppTheme.NEON, ThemeMode.DARK) {
            LanyinApp(
                UiSettings(theme = AppTheme.NEON, themeMode = ThemeMode.DARK), repo,
                player = DemoPlayerState(emptyList()), accountOverride = AccountState.LoggedIn(demoProfile),
                homePreview = homePreview(signedIn = true),
            )
        }
    }

    // Sheets over the player, and the pages they lead to.
    @Test fun queue_dusk_light() {
        val repo = repo()
        capture("queue_dusk_light", AppTheme.DUSK, ThemeMode.LIGHT) {
            LanyinApp(UiSettings(theme = AppTheme.DUSK, themeMode = ThemeMode.LIGHT), repo, player = playingAt(14_000), startExpanded = true)
        }
        compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("播放队列")).onFirst().performClick()
        compose.mainClock.advanceTimeBy(800)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/queue_dusk_light.png")
    }
    @Test fun more_grid() {
        val repo = repo()
        val player = DemoPlayerState(io.lanyin.player.ui.player.demoQueue.map { it.copy() }).apply { togglePlay() }
        capture("more_grid_0", AppTheme.GRID, ThemeMode.LIGHT) {
            LanyinApp(UiSettings(theme = AppTheme.GRID, themeMode = ThemeMode.LIGHT), repo, player = player, startExpanded = true)
        }
        compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("更多")).onFirst().performClick()
        compose.mainClock.advanceTimeBy(800)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/more_grid.png")
    }
    @Test fun artist_ink_dark() = capture("artist_ink_dark", AppTheme.INK, ThemeMode.DARK) {
        io.lanyin.player.ui.music.SongListScreen(
            "潮汐里的鲸", "128 首歌 · 9 张专辑\n写海、写夜、写慢慢来的事。",
            io.lanyin.player.ui.music.ListHeader.Cover(null, 21, round = true),
            io.lanyin.player.ui.music.Load.Ready(sampleSongs), playingId = null, isPlaying = false,
            onBack = {}, onPlay = { _, _ -> }, onRetry = {},
        )
    }
    @Test fun recent_dusk_dark() = capture("recent_dusk_dark", AppTheme.DUSK, ThemeMode.DARK) {
        io.lanyin.player.ui.music.RecentPage(playingSample(), onBack = {}, preview = sampleSongs.take(5))
    }

    // M4: cloud drive, comments, playlists of one's own, downloads, play records.
    private val now = java.time.LocalDateTime.of(2026, 10, 15, 14, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test fun cloud_dusk_light() = capture("cloud_dusk_light", AppTheme.DUSK, ThemeMode.LIGHT) {
        io.lanyin.player.ui.music.CloudPage(
            playingSample(), onBack = {},
            preview = io.lanyin.player.core.model.CloudPage(
                sampleSongs.take(7).map { io.lanyin.player.core.model.CloudSong(it.copy(fee = 0), "${it.name}.flac", 31_000_000, 999, 0) },
                count = 128, usedBytes = 23L * (1L shl 30) / 2, maxBytes = 60L shl 30, hasMore = false,
            ),
        )
    }

    @Test fun cloud_upload_ink_light() = capture("cloud_upload_ink_light", AppTheme.INK, ThemeMode.LIGHT) { CloudWithUploads() }
    @Test fun cloud_upload_neon_dark() = capture("cloud_upload_neon_dark", AppTheme.NEON, ThemeMode.DARK) { CloudWithUploads() }

    @Composable
    private fun CloudWithUploads() {
        val mb = 1L shl 20
        io.lanyin.player.ui.music.CloudPage(
            playingSample(), onBack = {},
            preview = io.lanyin.player.core.model.CloudPage(
                sampleSongs.take(5).map { io.lanyin.player.core.model.CloudSong(it.copy(fee = 0), "${it.name}.flac", 31_000_000, 999, 0) },
                count = 129, usedBytes = 23L * (1L shl 30) / 2, maxBytes = 60L shl 30, hasMore = false,
            ),
            previewUploads = listOf(
                io.lanyin.player.data.library.UploadJob(1, "Live at Wembley - 03 Bohemian Rhapsody.flac", 41 * mb, io.lanyin.player.data.library.UploadStage.Done),
                io.lanyin.player.data.library.UploadJob(2, "晴天 (Demo).mp3", 9 * mb, io.lanyin.player.data.library.UploadStage.Done, sent = 9 * mb, instant = true),
                io.lanyin.player.data.library.UploadJob(3, "海边的录音 2024-08.m4a", 27 * mb, io.lanyin.player.data.library.UploadStage.Sending, sent = 12 * mb),
                io.lanyin.player.data.library.UploadJob(4, "untitled.wav", 88 * mb),
            ),
        )
    }

    private fun sampleComments(): io.lanyin.player.core.model.CommentPage {
        fun user(id: Long, name: String) = io.lanyin.player.core.model.CommentUser(id, name, null)
        val c = listOf(
            io.lanyin.player.core.model.Comment(1, user(1, "潮汐里的鲸"), "副歌一出来，整个人就安静下来了。像是有人把海风装进了耳机里。", now - 3 * 60_000, 32_145, true, location = "浙江"),
            io.lanyin.player.core.model.Comment(2, user(2, "北岸"), "高三晚自习偷偷听的歌，现在下班路上还在听。", now - 5 * 3_600_000, 2_870, false, location = "广东"),
            io.lanyin.player.core.model.Comment(
                3, user(3, "城南旧事"), "同，每次听都想起那年夏天。", now - 26 * 3_600_000, 96, false,
                replyTo = io.lanyin.player.core.model.QuotedComment("北岸", "高三晚自习偷偷听的歌，现在下班路上还在听。"), location = "上海",
            ),
            io.lanyin.player.core.model.Comment(4, user(4, "Harbor Lights"), "Found this through a friend, the bridge at 2:40 is unreal.", now - 40L * 86_400_000, 12, false),
        )
        return io.lanyin.player.core.model.CommentPage(c, total = 12_480, hasMore = true, cursor = null)
    }

    @Composable
    private fun Comments() {
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val controller = androidx.compose.runtime.remember {
            io.lanyin.player.ui.comments.CommentsController(100, null, scope, me = null).apply { preview(sampleComments()) }
        }
        io.lanyin.player.ui.comments.CommentsPage(sampleSongs[0], onBack = {}, controller = controller, now = now)
    }

    @Test fun comments_ink_light() = capture("comments_ink_light", AppTheme.INK, ThemeMode.LIGHT) { Comments() }
    @Test fun comments_dusk_dark() = capture("comments_dusk_dark", AppTheme.DUSK, ThemeMode.DARK) { Comments() }
    @Test fun comments_grid() = capture("comments_grid", AppTheme.GRID, ThemeMode.LIGHT) { Comments() }

    @Test fun downloads_dusk_light() = capture("downloads_dusk_light", AppTheme.DUSK, ThemeMode.LIGHT) {
        val states = listOf(
            io.lanyin.player.playback.DownloadState.Downloading to 0.42f, io.lanyin.player.playback.DownloadState.Queued to 0f,
            io.lanyin.player.playback.DownloadState.Failed to 0.1f,
        ) + List(5) { io.lanyin.player.playback.DownloadState.Done to 1f }
        io.lanyin.player.ui.music.DownloadsPage(
            playingSample(), onBack = {},
            preview = sampleSongs.take(states.size).mapIndexed { i, song ->
                io.lanyin.player.playback.DownloadItem(song, states[i].first, states[i].second, 31_400_000L + i * 1_000_000, 100L - i)
            },
        )
    }

    @Test fun ranking_ink_dark() = capture("ranking_ink_dark", AppTheme.INK, ThemeMode.DARK) {
        io.lanyin.player.ui.music.RankingPage(
            playingSample(), onBack = {},
            preview = sampleSongs.mapIndexed { i, song -> io.lanyin.player.core.model.RankedSong(song, 48 - i * 5, 100 - i * 9) },
        )
    }

    @Test fun recent_account_grid() = capture("recent_account_grid", AppTheme.GRID, ThemeMode.LIGHT) {
        io.lanyin.player.ui.music.RecentPage(
            playingSample(), onBack = {}, now = now,
            previewAccount = sampleSongs.take(6).mapIndexed { i, song -> io.lanyin.player.core.model.RecentPlay(song, now - i * 7_000_000L) },
        )
    }

    /** A song list with a sheet up over it. */
    @Composable
    private fun WithSheet(sheet: io.lanyin.player.ui.music.Sheet, content: @Composable (io.lanyin.player.ui.music.Sheet) -> Unit) {
        val state = androidx.compose.runtime.remember { io.lanyin.player.ui.music.SheetState().apply { open(sheet) } }
        androidx.compose.foundation.layout.Box {
            io.lanyin.player.ui.music.SongListScreen(
                "夜里听", "潮汐里的鲸", io.lanyin.player.ui.music.ListHeader.Cover(null, 7),
                io.lanyin.player.ui.music.Load.Ready(sampleSongs), playingId = 102, isPlaying = true,
                onBack = {}, onPlay = { _, _ -> }, onRetry = {}, likedIds = setOf(100),
            )
            io.lanyin.player.ui.music.SheetLayer(state, content)
        }
    }

    @Test fun actions_dusk_dark() = capture("actions_dusk_dark", AppTheme.DUSK, ThemeMode.DARK) {
        val song = sampleSongs[0].copy(album = io.lanyin.player.core.model.Album(9, "潮汐", null))
        WithSheet(io.lanyin.player.ui.music.Sheet.TrackActions(song)) {
            io.lanyin.player.ui.music.TrackActionsSheet(
                song, liked = true, onToggleLike = {}, onOpenArtist = {}, onOpenAlbum = { _, _ -> },
                context = io.lanyin.player.ui.music.SongContext.OwnPlaylist(10, "夜里听"),
                download = io.lanyin.player.playback.DownloadItem(song, io.lanyin.player.playback.DownloadState.Done, 1f, 31_000_000, 0),
            )
        }
    }

    @Test fun effects_neon() = capture("effects_neon", AppTheme.NEON, ThemeMode.DARK) {
        WithSheet(io.lanyin.player.ui.music.Sheet.Effects) {
            io.lanyin.player.ui.effects.EffectsSheet(io.lanyin.player.core.audio.EffectsSettings().choose(io.lanyin.player.core.audio.EffectPreset.LIVE), {})
        }
    }
    @Test fun effects_grid() = capture("effects_grid", AppTheme.GRID, ThemeMode.LIGHT) {
        WithSheet(io.lanyin.player.ui.music.Sheet.Effects) {
            io.lanyin.player.ui.effects.EffectsSheet(io.lanyin.player.core.audio.EffectsSettings().choose(io.lanyin.player.core.audio.EffectPreset.ROCK).withBand(5, 6f), {})
        }
    }
    @Test fun effects_ink_off() = capture("effects_ink_off", AppTheme.INK, ThemeMode.LIGHT) {
        WithSheet(io.lanyin.player.ui.music.Sheet.Effects) { io.lanyin.player.ui.effects.EffectsSheet(io.lanyin.player.core.audio.EffectsSettings(), {}) }
    }
    @Test fun quality_dusk() = capture("quality_dusk", AppTheme.DUSK, ThemeMode.DARK) {
        WithSheet(io.lanyin.player.ui.music.Sheet.Quality) {
            io.lanyin.player.ui.effects.QualitySheet(io.lanyin.player.core.model.Quality.LOSSLESS, "无损 · FLAC · 44.1kHz") {}
        }
    }

    @Test fun add_to_playlist_ink_light() = capture("add_to_playlist_ink_light", AppTheme.INK, ThemeMode.LIGHT) {
        WithSheet(io.lanyin.player.ui.music.Sheet.AddToPlaylist(sampleSongs.take(1))) {
            io.lanyin.player.ui.music.AddToPlaylistSheet(1, samplePlaylists.filter { it.creatorId == 1L && it.specialType != 5 }, onPick = {}, onNew = {})
        }
    }

    @Test fun new_playlist_ink_light() = capture("new_playlist_ink_light", AppTheme.INK, ThemeMode.LIGHT) {
        WithSheet(io.lanyin.player.ui.music.Sheet.NewPlaylist()) {
            io.lanyin.player.ui.music.NameSheet("新建歌单", "周末慢慢听", "创建", onConfirm = { _, _ -> }, askPrivate = true)
        }
    }

    @Test fun playlist_collect_neon() = capture("playlist_collect_neon", AppTheme.NEON, ThemeMode.DARK) {
        io.lanyin.player.ui.music.SongListScreen(
            "城市民谣精选", "城南 · 下班路上的歌", io.lanyin.player.ui.music.ListHeader.Cover(null, 12),
            io.lanyin.player.ui.music.Load.Ready(sampleSongs), playingId = null, isPlaying = false,
            onBack = {}, onPlay = { _, _ -> }, onRetry = {}, downloadedIds = setOf(101, 103),
            actions = {
                io.lanyin.player.ui.design.ThemedChip("收藏", false, {})
                io.lanyin.player.ui.design.IconAction(io.lanyin.player.ui.design.LineIcon.Download, "下载全部", {})
            },
        )
    }

    // 格, the neo-brutalist Swiss theme: light only.
    @Test fun home_grid_light() = app(AppTheme.GRID, ThemeMode.LIGHT, PlayerSkin.COVER)
    @Config(qualifiers = "w392dp-h1500dp-port-xhdpi")
    @Test fun grid_settings_full() {
        val repo = repo()
        capture("grid_settings_full", AppTheme.GRID, ThemeMode.LIGHT) {
            io.lanyin.player.ui.settings.SettingsScreen(UiSettings(theme = AppTheme.GRID, themeMode = ThemeMode.LIGHT), repo, onBack = {})
        }
    }
    @Test fun grid_port_cover() = capture("grid_port_cover", AppTheme.GRID, ThemeMode.LIGHT) { Player(PlayerSkin.COVER, playingAt(9_000, track = 2)) }
    @Test fun grid_port_cassette() = capture("grid_port_cassette", AppTheme.GRID, ThemeMode.LIGHT) { Player(PlayerSkin.CASSETTE) }
    @Test fun grid_port_lyrics() = capture("grid_port_lyrics", AppTheme.GRID, ThemeMode.LIGHT) { Player(PlayerSkin.LYRICS, playingAt(6_000, track = 3)) }

    @Config(qualifiers = LANDSCAPE)
    @Test fun grid_land_cover() = capture("grid_land_cover", AppTheme.GRID, ThemeMode.LIGHT) { Player(PlayerSkin.COVER, playingAt(9_000, track = 2)) }

    @Config(qualifiers = LANDSCAPE)
    @Test fun grid_land_idle() = capture("grid_land_idle", AppTheme.GRID, ThemeMode.LIGHT, settle = 6_500) { Player(PlayerSkin.VINYL) }

    @Config(qualifiers = LANDSCAPE)
    @Test fun grid_land_lyrics() = capture("grid_land_lyrics", AppTheme.GRID, ThemeMode.LIGHT) { Player(PlayerSkin.LYRICS, playingAt(6_000, track = 3)) }

    @Config(qualifiers = LANDSCAPE)
    @Test fun ink_land_vinyl_idle() {
        // After five idle seconds the chrome folds away and the stage takes the room.
        capture("ink_land_vinyl_idle", AppTheme.INK, ThemeMode.LIGHT, settle = 6_500) { Player(PlayerSkin.VINYL) }
    }

    @Config(qualifiers = LANDSCAPE)
    @Test fun ink_land_vinyl_large_lyrics() = capture("ink_land_vinyl_large_lyrics", AppTheme.INK, ThemeMode.LIGHT) {
        PlayerScreen(playingAt(14_000), PlayerSkin.VINYL, rememberSpinState(true), keepScreenOnInLandscape = true, onCollapse = {}, lyricScale = io.lanyin.player.ui.theme.LyricSize.LARGE.scale)
    }

    // Home, inside the real shell, for every theme.
    @Test fun home_ink_light() = app(AppTheme.INK, ThemeMode.LIGHT)

    /** The whole home page, top to bottom, signed in. */
    private fun homeFull(theme: AppTheme, mode: ThemeMode) {
        val repo = repo()
        capture("home_full_${theme.name.lowercase()}_${mode.id}", theme, mode) {
            LanyinApp(
                UiSettings(theme = theme, themeMode = mode), repo, player = playingAt(14_000),
                accountOverride = AccountState.LoggedIn(demoProfile), homePreview = homePreview(signedIn = true),
            )
        }
    }
    @Config(qualifiers = "w392dp-h1560dp-port-xhdpi")
    @Test fun home_full_ink() = homeFull(AppTheme.INK, ThemeMode.LIGHT)
    @Config(qualifiers = "w392dp-h1560dp-port-xhdpi")
    @Test fun home_full_dusk() = homeFull(AppTheme.DUSK, ThemeMode.LIGHT)
    @Config(qualifiers = "w392dp-h1560dp-port-xhdpi")
    @Test fun home_full_neon() = homeFull(AppTheme.NEON, ThemeMode.DARK)
    @Config(qualifiers = "w392dp-h1560dp-port-xhdpi")
    @Test fun home_full_grid() = homeFull(AppTheme.GRID, ThemeMode.LIGHT)
    @Test fun home_ink_dark() = app(AppTheme.INK, ThemeMode.DARK)
    @Test fun home_neon() = app(AppTheme.NEON, ThemeMode.DARK)
    @Test fun home_dusk_light() = app(AppTheme.DUSK, ThemeMode.LIGHT, PlayerSkin.CASSETTE)

    // The landscape stage for every theme.
    @Config(qualifiers = LANDSCAPE)
    @Test fun land_dusk_dark_vinyl() = capture("land_dusk_dark_vinyl", AppTheme.DUSK, ThemeMode.DARK) { Player(PlayerSkin.VINYL) }

    @Config(qualifiers = LANDSCAPE)
    @Test fun land_ink_light_vinyl() = capture("land_ink_light_vinyl", AppTheme.INK, ThemeMode.LIGHT) { Player(PlayerSkin.VINYL) }

    @Config(qualifiers = LANDSCAPE)
    @Test fun land_ink_light_cover() = capture("land_ink_light_cover", AppTheme.INK, ThemeMode.LIGHT) { Player(PlayerSkin.COVER) }

    @Config(qualifiers = LANDSCAPE)
    @Test fun land_neon_lyrics() = capture("land_neon_lyrics", AppTheme.NEON, ThemeMode.DARK) { Player(PlayerSkin.LYRICS) }

    @Config(qualifiers = LANDSCAPE)
    @Test fun land_dusk_light_cassette() = capture("land_dusk_light_cassette", AppTheme.DUSK, ThemeMode.LIGHT) { Player(PlayerSkin.CASSETTE) }

    @Config(qualifiers = LANDSCAPE)
    @Test fun land_dusk_light_paused() =
        capture("land_dusk_light_paused", AppTheme.DUSK, ThemeMode.LIGHT) { Player(PlayerSkin.VINYL, DemoPlayerState()) }

    // Real-data edge cases: an instrumental and a track whose title, artist and lyrics are long.
    @Config(qualifiers = LANDSCAPE)
    @Test fun land_ink_light_instrumental() = capture("land_ink_light_instrumental", AppTheme.INK, ThemeMode.LIGHT) { Player(PlayerSkin.VINYL, playingAt(8_000, track = 1)) }

    @Config(qualifiers = LANDSCAPE)
    @Test fun land_dusk_dark_long() = capture("land_dusk_dark_long", AppTheme.DUSK, ThemeMode.DARK) { Player(PlayerSkin.VINYL, playingAt(6_000, track = 3)) }

    @Test fun port_dusk_dark_long() = capture("port_dusk_dark_long", AppTheme.DUSK, ThemeMode.DARK) { Player(PlayerSkin.VINYL, playingAt(6_000, track = 3)) }

    @Test fun port_ink_light_instrumental() = capture("port_ink_light_instrumental", AppTheme.INK, ThemeMode.LIGHT) { Player(PlayerSkin.COVER, playingAt(8_000, track = 1)) }

    // Player motion, caught mid-flight: the entrance, a skip, and scrubbing.
    @Test fun motion_entrance() = capture("motion_entrance_dusk_dark", AppTheme.DUSK, ThemeMode.DARK, settle = 260) { Player(PlayerSkin.VINYL) }

    @Test fun motion_skip() {
        val state = playingAt(14_000)
        capture("motion_skip_0_ink_light", AppTheme.INK, ThemeMode.LIGHT) { Player(PlayerSkin.VINYL, state) }
        compose.runOnUiThread { state.skipNext() }
        compose.mainClock.advanceTimeBy(140)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/motion_skip_1_ink_light.png")
        compose.mainClock.advanceTimeBy(260)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/motion_skip_2_ink_light.png")
        compose.mainClock.advanceTimeBy(1_200)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/motion_skip_3_ink_light.png")
    }

    // 澜's touch: rings spreading from the finger on a glass card, mid-press.
    @Test fun motion_ripple() {
        val repo = repo()
        capture("motion_ripple_0_dusk_light", AppTheme.DUSK, ThemeMode.LIGHT) {
            LanyinApp(UiSettings(theme = AppTheme.DUSK, themeMode = ThemeMode.LIGHT), repo, player = playingAt(14_000))
        }
        // The 继续播放 card.
        compose.onRoot().performTouchInput { down(androidx.compose.ui.geometry.Offset(width * 0.4f, height * 0.31f)) }
        compose.mainClock.advanceTimeBy(260)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/motion_ripple_1_dusk_light.png")
        compose.onRoot().performTouchInput { cancel() }
    }

    // Portrait player.
    @Test fun port_dusk_dark_vinyl() = capture("port_dusk_dark_vinyl", AppTheme.DUSK, ThemeMode.DARK) { Player(PlayerSkin.VINYL) }
    @Test fun port_neon_vinyl() = capture("port_neon_vinyl", AppTheme.NEON, ThemeMode.DARK) { Player(PlayerSkin.VINYL) }
    @Test fun port_dusk_dark_cover() = capture("port_dusk_dark_cover", AppTheme.DUSK, ThemeMode.DARK) { Player(PlayerSkin.COVER) }
    @Test fun port_ink_dark_lyrics() = capture("port_ink_dark_lyrics", AppTheme.INK, ThemeMode.DARK) { Player(PlayerSkin.LYRICS) }

    // Other pages.
    @Test fun login_ink_dark() = capture("login_ink_dark", AppTheme.INK, ThemeMode.DARK) { LoginScreen(showingQr(), onBack = {}) }

    @Test fun settings_dusk_light() {
        val repo = repo()
        capture("settings_dusk_light", AppTheme.DUSK, ThemeMode.LIGHT) {
            io.lanyin.player.ui.settings.SettingsScreen(UiSettings(), repo, onBack = {})
        }
    }

    @Test fun search_dusk_dark() = capture("search_dusk_dark", AppTheme.DUSK, ThemeMode.DARK) {
        io.lanyin.player.ui.search.SearchScreen()
    }

    @Test fun library_neon() = capture("library_neon", AppTheme.NEON, ThemeMode.DARK) {
        io.lanyin.player.ui.library.LibraryScreen(onLogin = {})
    }

    // The mini player morphing into the full player, caught mid-flight.
    @Test fun transition_dusk_dark() {
        val repo = repo()
        capture("transition_0_dusk_dark", AppTheme.DUSK, ThemeMode.DARK) {
            LanyinApp(UiSettings(theme = AppTheme.DUSK, themeMode = ThemeMode.DARK), repo, player = playingAt(14_000))
        }
        compose.onNodeWithTag("miniPlayer").performClick()
        compose.mainClock.advanceTimeBy(110)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/transition_1_dusk_dark.png")
        compose.mainClock.advanceTimeBy(110)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/transition_2_dusk_dark.png")
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/transition_3_dusk_dark.png")
    }
}

private const val PORTRAIT = "w392dp-h850dp-port-xhdpi"
private const val LANDSCAPE = "w850dp-h392dp-land-xhdpi"
