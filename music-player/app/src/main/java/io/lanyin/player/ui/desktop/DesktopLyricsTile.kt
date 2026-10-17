package io.lanyin.player.ui.desktop

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.lanyin.player.LanyinApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The 桌面歌词 switch among the quick settings: on and off, and, when the lyrics are locked (so
 * they can't be touched), a tap unlocks them. Without the permission to float over other apps, it
 * opens the page that grants it.
 */
class DesktopLyricsTile : TileService() {
    private val container get() = (application as LanyinApplication).container

    override fun onStartListening() {
        refresh()
    }

    override fun onClick() {
        val c = container
        c.scope.launch {
            val s = c.settings.settings.first()
            when {
                !DesktopLyricsWindow.canDraw(this@DesktopLyricsTile) -> openPermission()
                s.desktopLyrics && s.desktopLyricsLocked -> c.settings.setDesktopLyricsLocked(false)
                else -> c.settings.setDesktopLyrics(!s.desktopLyrics)
            }
            refresh()
        }
    }

    private fun refresh() {
        val c = container
        c.scope.launch {
            val s = c.settings.settings.first()
            val tile = qsTile ?: return@launch
            val on = s.desktopLyrics && DesktopLyricsWindow.canDraw(this@DesktopLyricsTile)
            tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = when {
                    !on -> "关"
                    s.desktopLyricsLocked -> "已锁定 · 点按解锁"
                    else -> "开"
                }
            }
            tile.updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
