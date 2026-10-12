package app.amisles.hanime.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import app.amisles.hanime.domain.model.HomeSection

enum class HomeSectionLayout {
    AUTO,
    LIST,
    GRID,
    CAROUSEL,
}

@Immutable
data class HomeSectionConfig(
    val visibleCount: Int = DEFAULT_VISIBLE_COUNT,
    val layouts: Map<String, HomeSectionLayout> = emptyMap(),
    val order: List<String> = emptyList(),
) {
    fun layoutFor(title: String): HomeSectionLayout = layouts[title] ?: HomeSectionLayout.AUTO

    /** 按用户顺序重排；未列入 [order] 的分区（含服务端新增）保持原相对顺序追加在后。 */
    fun applyOrder(sections: List<HomeSection>): List<HomeSection> {
        if (order.isEmpty()) return sections
        val byTitle = sections.associateBy { it.title }
        val head = order.mapNotNull { byTitle[it] }
        val headTitles = head.mapTo(mutableSetOf()) { it.title }
        return head + sections.filterNot { it.title in headTitles }
    }

    fun sanitized(): HomeSectionConfig = copy(
        visibleCount = visibleCount.coerceIn(MIN_VISIBLE_COUNT, MAX_VISIBLE_COUNT),
        layouts = layouts.filterValues { it != HomeSectionLayout.AUTO },
        order = order.distinct(),
    )

    companion object {
        const val DEFAULT_VISIBLE_COUNT = 6
        const val MIN_VISIBLE_COUNT = 3
        const val MAX_VISIBLE_COUNT = 12
        val Default = HomeSectionConfig()
    }
}

val LocalHomeSectionConfig = staticCompositionLocalOf { HomeSectionConfig.Default }
