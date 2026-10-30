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
    val hidden: Set<String> = emptySet(),
    val pinned: List<String> = emptyList(),
    val visibleCounts: Map<String, Int> = emptyMap(),
) {
    fun layoutFor(title: String): HomeSectionLayout = layouts[title] ?: HomeSectionLayout.AUTO

    fun countFor(title: String): Int = visibleCounts[title] ?: visibleCount

    fun isHidden(title: String): Boolean = title in hidden

    /** 置顶 + 其余按用户顺序重排；隐藏的分区不参与。 */
    fun arrange(sections: List<HomeSection>): List<HomeSection> {
        val visible = sections.filterNot { isHidden(it.title) }
        if (pinned.isEmpty() && order.isEmpty()) return visible
        val ordered = applyOrderWithin(visible)
        val head = pinned.mapNotNull { title -> ordered.firstOrNull { it.title == title } }
        val headTitles = head.mapTo(mutableSetOf()) { it.title }
        return head + ordered.filterNot { it.title in headTitles }
    }

    private fun applyOrderWithin(sections: List<HomeSection>): List<HomeSection> {
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
        hidden = hidden.filter { it.isNotBlank() }.toSet(),
        pinned = pinned.distinct(),
        visibleCounts = visibleCounts.filterKeys { it.isNotBlank() }
            .mapValues { it.value.coerceIn(MIN_VISIBLE_COUNT, MAX_VISIBLE_COUNT) },
    )

    companion object {
        const val DEFAULT_VISIBLE_COUNT = 6
        const val MIN_VISIBLE_COUNT = 3
        const val MAX_VISIBLE_COUNT = 12
        val Default = HomeSectionConfig()
    }
}

val LocalHomeSectionConfig = staticCompositionLocalOf { HomeSectionConfig.Default }
