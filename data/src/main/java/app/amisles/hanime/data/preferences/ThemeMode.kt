package app.amisles.hanime.data.preferences

/**
 * 主题模式：浅色 / 深色 / 跟随系统
 */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM;

    companion object {
        fun fromName(name: String?): ThemeMode =
            runCatching { valueOf(name ?: "") }.getOrDefault(SYSTEM)
    }
}
