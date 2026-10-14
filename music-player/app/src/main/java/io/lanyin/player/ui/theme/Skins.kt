package io.lanyin.player.ui.theme

/**
 * A whole visual language, not just a palette: each theme brings its own shapes, type,
 * backdrop, controls, lyric layout and motion (see [LanyinStyle]).
 */
enum class AppTheme(
    val id: String,
    val label: String,
    val tagline: String,
    val darkOnly: Boolean = false,
) {
    TIDE("lan", "澜", "流动的深海与毛玻璃"),
    INK("mo", "墨", "黑白留白，只留文字"),
    SAKURA("ying", "樱", "花瓣飘落，软糯回弹"),
    NEON("ni", "霓", "赛博霓虹，夜色限定", darkOnly = true),
    DUSK("mu", "暮", "七十年代的暖色黄昏");

    companion object {
        val Default = TIDE
        fun fromId(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: Default
    }
}

/** Artwork style of the now-playing page; combines freely with any [AppTheme]. */
enum class PlayerSkin(val id: String, val label: String, val description: String) {
    VINYL("vinyl", "旋转黑胶", "唱片带惯性转动，唱臂随播放落下"),
    COVER("cover", "方形封面", "大封面，暂停时轻轻收起"),
    LYRICS("lyrics", "沉浸歌词", "不要封面，整屏都是歌词"),
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
