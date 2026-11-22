package app.amisles.hanime.feature.home

import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import app.amisles.hanime.core.ui.theme.HomeSectionConfig
import app.amisles.hanime.core.ui.theme.HomeSectionLayout
import app.amisles.hanime.core.ui.theme.WindowWidthSizeClass
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.HomeSection

/** AUTO 解析后的渲染形态：由宽度档位与是否分栏共同决定，故需与用户可选的 [HomeSectionLayout] 区分。 */
internal enum class SectionRenderMode { LIST_COLUMN, LIST_ROWS, GRID_ROWS, CAROUSEL }

internal data class SectionPlan(
    val section: HomeSection,
    val mode: SectionRenderMode,
    val videos: List<HanimeVideo>,
    val rowVideos: List<List<HanimeVideo>>
)

internal enum class SectionEntryKind { BLOCK, HEADER, VIDEO, ROW, CAROUSEL }

/**
 * 分区拆成独立 item 后的最小单元。
 *
 * 一个分区整体作为一个 item 时，它第一次进入视口要在同一帧里组合十来个卡片；拆开之后由
 * LazyVerticalStaggeredGrid 决定先组合哪几行。[sectionKey] 与分区块的 item key 相同，锚点
 * 在「整块/拆行」两种结构之间切换时仍能落回同一个分区。
 */
internal data class SectionEntry(
    val key: String,
    val sectionKey: String,
    val plan: SectionPlan,
    val kind: SectionEntryKind,
    val videos: List<HanimeVideo> = emptyList(),
    val bottomGap: Boolean = false
)

internal data class HomeFeedInput(
    val sections: List<HomeSection>,
    val homeConfig: HomeSectionConfig,
    val widthClass: WindowWidthSizeClass,
    val columns: Int,
    val twoColumnSections: Boolean,
    val showLongPressHint: Boolean,
    val showCategoryBar: Boolean,
    val showRefreshError: Boolean,
    val twoColumnHero: Boolean,
    val hasBanner: Boolean,
    val hasContinueWatching: Boolean,
    val hasWatchLater: Boolean,
    val showWeeklyReport: Boolean
)

internal data class HomeFeedPlan(
    val plans: List<SectionPlan>,
    val entries: List<SectionEntry>,
    val itemKeys: List<String>,
    val sectionKeyByItemKey: Map<String, String>,
    val sectionTitleToIndex: Map<String, Int>
)

internal fun planHomeFeed(input: HomeFeedInput): HomeFeedPlan {
    val plans = input.homeConfig.arrange(input.sections).map { section ->
        val mode = renderMode(
            input.homeConfig.layoutFor(section.title),
            input.widthClass,
            input.twoColumnSections
        )
        val videos = section.videos.take(input.homeConfig.countFor(section.title))
        val rowVideos = when (mode) {
            SectionRenderMode.LIST_ROWS, SectionRenderMode.GRID_ROWS -> videos.chunked(input.columns)
            SectionRenderMode.LIST_COLUMN, SectionRenderMode.CAROUSEL -> emptyList()
        }
        SectionPlan(section = section, mode = mode, videos = videos, rowVideos = rowVideos)
    }
    val headKeys = buildList {
        add("home-header")
        if (input.showLongPressHint) add("home-long-press-hint")
        if (input.showCategoryBar) add("home-category")
        if (input.showRefreshError) add("home-refresh-error")
        if (input.twoColumnHero) {
            add("home-hero")
        } else {
            if (input.hasBanner) add("home-banner")
            if (input.hasContinueWatching) add("home-continue-watching")
            if (input.hasWatchLater) add("home-watch-later")
        }
        if (input.showWeeklyReport) add("home-weekly-report")
    }
    val entries = buildSectionEntries(plans, !input.twoColumnSections)
    val sectionTitleToIndex = buildMap {
        entries.forEachIndexed { index, entry ->
            if (entry.kind == SectionEntryKind.HEADER || entry.kind == SectionEntryKind.BLOCK) {
                put(entry.plan.section.title, headKeys.size + index)
            }
        }
    }
    return HomeFeedPlan(
        plans = plans,
        entries = entries,
        itemKeys = headKeys + entries.map { it.key },
        sectionKeyByItemKey = entries.associate { it.key to it.sectionKey },
        sectionTitleToIndex = sectionTitleToIndex
    )
}

internal fun renderMode(
    configured: HomeSectionLayout,
    widthClass: WindowWidthSizeClass,
    twoColumnSections: Boolean
): SectionRenderMode =
    when (configured) {
        HomeSectionLayout.LIST -> SectionRenderMode.LIST_COLUMN
        HomeSectionLayout.GRID -> SectionRenderMode.GRID_ROWS
        HomeSectionLayout.CAROUSEL -> SectionRenderMode.CAROUSEL
        HomeSectionLayout.AUTO -> when {
            twoColumnSections -> SectionRenderMode.GRID_ROWS
            widthClass == WindowWidthSizeClass.Compact -> SectionRenderMode.LIST_COLUMN
            widthClass == WindowWidthSizeClass.Medium -> SectionRenderMode.LIST_ROWS
            else -> SectionRenderMode.GRID_ROWS
        }
    }

private fun buildSectionEntries(
    plans: List<SectionPlan>,
    splitRows: Boolean
): List<SectionEntry> = buildList {
    plans.forEachIndexed { index, plan ->
        val sectionKey = sectionItemKey(plan, index)
        if (!splitRows) {
            add(SectionEntry(sectionKey, sectionKey, plan, SectionEntryKind.BLOCK))
            return@forEachIndexed
        }
        add(SectionEntry(sectionKey, sectionKey, plan, SectionEntryKind.HEADER))
        val rows = when (plan.mode) {
            SectionRenderMode.LIST_COLUMN -> plan.videos.mapIndexed { i, video ->
                SectionEntry("$sectionKey-video-$i", sectionKey, plan, SectionEntryKind.VIDEO, listOf(video))
            }
            SectionRenderMode.LIST_ROWS, SectionRenderMode.GRID_ROWS -> plan.rowVideos.mapIndexed { i, row ->
                SectionEntry("$sectionKey-row-$i", sectionKey, plan, SectionEntryKind.ROW, row)
            }
            SectionRenderMode.CAROUSEL -> listOf(
                SectionEntry("$sectionKey-carousel", sectionKey, plan, SectionEntryKind.CAROUSEL)
            )
        }
        rows.dropLast(1).forEach(::add)
        rows.lastOrNull()?.let { add(it.copy(bottomGap = true)) }
    }
}

private fun sectionItemKey(plan: SectionPlan, index: Int): String =
    "home-section-$index-" + plan.section.title

/**
 * 转屏或分屏改变档位时，item 结构与行高都会变，LazyList 只保留下标，画面会跳到别的分区。
 * 旧布局仍可读时记下首个可见 item 的 key 与裁切高度，新布局按 key 找回同一位置。
 */
@Stable
internal class HomeFeedAnchorState(private val listState: LazyStaggeredGridState) {
    private var pendingKey: String? = null
    private var pendingSectionKey: String? = null
    private var pendingOffset: Int = 0

    fun capture(feed: HomeFeedPlan) {
        val info = listState.layoutInfo.visibleItemsInfo.minByOrNull { it.index } ?: return
        val key = info.key as? String ?: return
        pendingKey = key
        pendingSectionKey = feed.sectionKeyByItemKey[key]
        pendingOffset = info.offset.y
    }

    suspend fun restore(feed: HomeFeedPlan) {
        val key = pendingKey ?: return
        val fallback = pendingSectionKey
        val offset = pendingOffset
        pendingKey = null
        pendingSectionKey = null
        pendingOffset = 0
        val index = feed.itemKeys.indexOf(key).takeIf { it >= 0 }
            ?: fallback?.let { feed.itemKeys.indexOf(it) }?.takeIf { it >= 0 }
        if (index != null) listState.scrollToItem(index, offset)
    }
}

@Composable
internal fun rememberHomeFeedAnchorState(listState: LazyStaggeredGridState): HomeFeedAnchorState =
    remember(listState) { HomeFeedAnchorState(listState) }
