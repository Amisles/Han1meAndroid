package app.amisles.hanime.domain.preferences

/** 支持布局记忆的页面。 */
enum class PageLayoutKey { HOME, SEARCH, FAVORITE, HISTORY, SUBSCRIPTIONS, AUTHOR }

/** 各页面的布局快照；缺失的键回落到搜索页的旧设置或宽度档位默认。 */
data class PageLayoutModes(
    val home: PageLayoutMode? = null,
    val search: PageLayoutMode? = null,
    val favorite: PageLayoutMode? = null,
    val history: PageLayoutMode? = null,
    val subscriptions: PageLayoutMode? = null,
    val author: PageLayoutMode? = null,
) {
    fun of(key: PageLayoutKey): PageLayoutMode? = when (key) {
        PageLayoutKey.HOME -> home
        PageLayoutKey.SEARCH -> search
        PageLayoutKey.FAVORITE -> favorite
        PageLayoutKey.HISTORY -> history
        PageLayoutKey.SUBSCRIPTIONS -> subscriptions
        PageLayoutKey.AUTHOR -> author
    }
}
