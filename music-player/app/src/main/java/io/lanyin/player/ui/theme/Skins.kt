package io.lanyin.player.ui.theme

/** Colour skin for the whole app. */
enum class AppSkin(val id: String, val label: String, val description: String) {
    LAN("lan", "澜", "深海蓝 · 默认"),
    MO("mo", "墨", "黑白水墨 · 深色纯黑省电"),
    YING("ying", "樱", "樱花粉"),
    ZHU("zhu", "竹", "青竹绿"),
    MU("mu", "暮", "暮色琥珀"),
    SYSTEM("system", "随系统", "跟随壁纸取色 · Android 12+");

    companion object {
        val Default = LAN
        fun fromId(id: String?): AppSkin = entries.firstOrNull { it.id == id } ?: Default
    }
}

/** Layout and artwork style of the now-playing page. */
enum class PlayerSkin(val id: String, val label: String, val description: String) {
    VINYL("vinyl", "旋转黑胶", "唱片随播放旋转，唱臂落下"),
    COVER("cover", "方形封面", "圆角大封面，干净利落"),
    LYRICS("lyrics", "沉浸歌词", "隐藏封面，整屏都是歌词"),
    CASSETTE("cassette", "复古卡带", "磁带转轮随播放转动");

    companion object {
        val Default = VINYL
        fun fromId(id: String?): PlayerSkin = entries.firstOrNull { it.id == id } ?: Default
    }
}

enum class ThemeMode(val id: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色");

    companion object {
        val Default = SYSTEM
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: Default
    }
}
