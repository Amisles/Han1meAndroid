package app.amisles.hanime.domain.repository

import app.amisles.hanime.domain.model.ComicReadMode
import app.amisles.hanime.domain.preferences.PageLayoutKey
import app.amisles.hanime.domain.preferences.PageLayoutMode
import app.amisles.hanime.domain.preferences.PageLayoutModes
import app.amisles.hanime.domain.preferences.RelatedLayoutMode
import app.amisles.hanime.domain.preferences.SearchLayoutMode
import kotlinx.coroutines.flow.StateFlow

/** 阅读与列表设置：布局记忆、漫画阅读方式、统计与订阅页的展示开关。 */
interface ReadingSettings {
    /** null 表示用户从未显式选择过，界面按窗口宽度档位取默认值。 */
    val searchLayoutMode: SearchLayoutMode?
    val searchLayoutModeFlow: StateFlow<SearchLayoutMode?>
    fun setSearchLayoutMode(mode: SearchLayoutMode)

    /** 各列表页的布局记忆；搜索页缺省时回落到旧字段，均为空按宽度档位取默认。 */
    val pageLayoutModes: PageLayoutModes
    val pageLayoutModesFlow: StateFlow<PageLayoutModes>
    fun setPageLayoutMode(page: PageLayoutKey, mode: PageLayoutMode)
    fun clearPageLayoutMode(page: PageLayoutKey)

    /** 漫画阅读方式；null 表示用户从未选择，阅读器按单页翻页起步。 */
    val comicReadMode: ComicReadMode?
    val comicReadModeFlow: StateFlow<ComicReadMode?>
    fun setComicReadMode(mode: ComicReadMode)

    /** 漫画阅读器纯黑底色。 */
    val comicReaderBlack: Boolean
    val comicReaderBlackFlow: StateFlow<Boolean>
    fun setComicReaderBlack(enabled: Boolean)

    /** null 表示未选择，详情页按推荐卡片形态取默认值。 */
    val relatedLayoutMode: RelatedLayoutMode?
    val relatedLayoutModeFlow: StateFlow<RelatedLayoutMode?>
    fun setRelatedLayoutMode(mode: RelatedLayoutMode)
    fun clearRelatedLayoutMode()

    /** 观看统计开关。关闭后不展示入口，也不做任何统计计算。 */
    val watchStatsEnabled: Boolean
    val watchStatsEnabledFlow: StateFlow<Boolean>
    fun setWatchStatsEnabled(enabled: Boolean)

    /** 订阅页隐藏已看完条目。 */
    val subscriptionsHideWatched: Boolean
    val subscriptionsHideWatchedFlow: StateFlow<Boolean>
    fun setSubscriptionsHideWatched(enabled: Boolean)
}
