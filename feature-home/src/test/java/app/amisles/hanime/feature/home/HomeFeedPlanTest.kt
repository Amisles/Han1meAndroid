package app.amisles.hanime.feature.home

import app.amisles.hanime.core.ui.theme.HomeSectionConfig
import app.amisles.hanime.core.ui.theme.HomeSectionLayout
import app.amisles.hanime.core.ui.theme.WindowWidthSizeClass
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.HomeSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedPlanTest {

    @Test
    fun `sections keep configured order and visible count`() {
        val plan = planHomeFeed(input())

        assertEquals(listOf("B", "A"), plan.plans.map { it.section.title })
        assertEquals(listOf(2, 2), plan.plans.map { it.videos.size })
        assertEquals(SectionEntryKind.HEADER, plan.entries.first().kind)
        assertTrue(plan.entries.any { it.kind == SectionEntryKind.ROW })
    }

    @Test
    fun `head keys precede section anchors`() {
        val plan = planHomeFeed(input())

        assertEquals(
            listOf("home-header", "home-long-press-hint", "home-category", "home-section-0-B"),
            plan.itemKeys.take(4)
        )
        assertEquals(3, plan.sectionTitleToIndex["B"])
        assertTrue(plan.itemKeys.indexOf("home-section-1-A") > 3)
    }

    @Test
    fun `hidden sections do not consume anchors`() {
        val plan = planHomeFeed(
            input().copy(homeConfig = HomeSectionConfig(hidden = setOf("A")))
        )

        assertEquals(listOf("B"), plan.plans.map { it.section.title })
        assertTrue(plan.sectionTitleToIndex["A"] == null)
    }

    private fun input(): HomeFeedInput = HomeFeedInput(
        sections = listOf(
            HomeSection("A", "", listOf(video("a1"), video("a2"), video("a3"))),
            HomeSection("B", "", listOf(video("b1"), video("b2"), video("b3")))
        ),
        homeConfig = HomeSectionConfig(
            visibleCount = 2,
            order = listOf("B", "A"),
            layouts = mapOf("A" to HomeSectionLayout.LIST, "B" to HomeSectionLayout.GRID)
        ),
        widthClass = WindowWidthSizeClass.Compact,
        columns = 2,
        twoColumnSections = false,
        showLongPressHint = true,
        showCategoryBar = true,
        showRefreshError = false,
        twoColumnHero = false,
        hasBanner = false,
        hasContinueWatching = false,
        hasWatchLater = false,
        showWeeklyReport = false
    )

    private fun video(id: String) = HanimeVideo(
        id = id,
        title = id,
        thumbnailUrl = "",
        duration = "",
        likeRate = "",
        viewCount = "",
        author = "",
        publishTime = "",
        videoUrl = ""
    )
}
