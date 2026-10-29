package app.amisles.hanime.core.ui.theme

import app.amisles.hanime.domain.preferences.PageLayoutKey
import app.amisles.hanime.domain.preferences.PageLayoutMode
import app.amisles.hanime.domain.preferences.PageLayoutModes

/** 各页面布局的统一解析：显式选择 > 搜索页旧设置（仅搜索页） > 宽度档位默认。 */
object PageLayoutResolver {

    fun isGrid(
        key: PageLayoutKey,
        modes: PageLayoutModes,
        legacySearchMode: PageLayoutMode?,
        compactWidth: Boolean
    ): Boolean = when (modes.of(key) ?: legacyOf(key, legacySearchMode)) {
        PageLayoutMode.GRID -> true
        PageLayoutMode.LIST -> false
        null -> !compactWidth
    }

    private fun legacyOf(key: PageLayoutKey, legacySearchMode: PageLayoutMode?): PageLayoutMode? =
        if (key == PageLayoutKey.SEARCH) legacySearchMode else null
}
