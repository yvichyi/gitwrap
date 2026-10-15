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
    val lightOnly: Boolean = false,
) {
    INK("mo", "墨", "黑白留白，只留文字"),
    TIDE("lan", "澜", "流动的深海与毛玻璃"),
    SAKURA("ying", "樱", "花瓣飘落，软糯回弹"),
    NEON("ni", "霓", "赛博霓虹，夜色限定", darkOnly = true),
    DUSK("mu", "暮", "七十年代的暖色黄昏"),
    GRID("ge", "格", "瑞士网格，粗边硬影", lightOnly = true);

    companion object {
        /** 墨 is the flagship: the most restrained theme sets the bar for the rest. */
        val Default = INK
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

/**
 * Lyric text size on the now-playing page, as a multiple of [BASE_SP]. Chinese glyphs fill their
 * em square, so 18sp already reads like a 20–22sp Latin line; the active line is enlarged a
 * little further by the theme's [LyricStyle.activeScale].
 */
enum class LyricSize(val id: String, val label: String, val scale: Float) {
    SMALL("small", "小", 0.86f),
    MEDIUM("medium", "中", 1f),
    LARGE("large", "大", 1.22f);

    val sp: Float get() = BASE_SP * scale

    companion object {
        const val BASE_SP = 18f
        val Default = MEDIUM
        fun fromId(id: String?): LyricSize = entries.firstOrNull { it.id == id } ?: Default
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
