package app.amisles.hanime.data.parser

import app.amisles.hanime.domain.model.HanimeBanner
import app.amisles.hanime.domain.model.HomePageData
import app.amisles.hanime.domain.model.HomeSection
import app.amisles.hanime.core.common.util.AppLogger
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

/**
 * 首页解析器
 */
class HomePageParser(private val videoListParser: VideoListParser = VideoListParser()) {

    fun parse(html: String, baseUrl: String): HomePageData {

        AppLogger.log("HomePageParser", "parse called, baseUrl: $baseUrl")

        val doc: Document = Jsoup.parse(html, baseUrl)
        val sections = parseHomeSections(doc, baseUrl)

        AppLogger.log("HomePageParser", "Found ${sections.size} home sections, video counts: ${sections.joinToString { "${it.title}:${it.videos.size}" }}")

        val banner = parseBanner(doc)

        AppLogger.log("HomePageParser", "Banner found: ${banner != null}")

        return HomePageData(banner = banner, sections = sections)
    }

    fun parseHomeSections(doc: Document, baseUrl: String): List<HomeSection> {
        val sections = mutableListOf<HomeSection>()
        val titleLinks: Elements = doc.select("a.horizontal-row-title")
        AppLogger.log("HomePageParser", "Found ${titleLinks.size} horizontal-row-title links")

        if (titleLinks.isEmpty()) {
            val fallbackVideos = videoListParser.parseVideoList(doc, baseUrl)
            if (fallbackVideos.isNotEmpty()) {
                sections.add(HomeSection(title = "最新上市", moreUrl = "", videos = fallbackVideos.take(8)))
                sections.add(HomeSection(title = "最新上传", moreUrl = "", videos = fallbackVideos.drop(8).take(8)))
            }
            return sections
        }

        for (link in titleLinks) {
            try {
                val h3 = link.selectFirst("h3") ?: continue
                val originalTitle = h3.ownText().trim().ifEmpty {
                    h3.textNodes()?.firstOrNull()?.text()?.trim() ?: ""
                }
                if (originalTitle.isEmpty()) continue

                val title = ParserUtils.convertToSimplified(originalTitle)
                val moreUrl = link.attr("abs:href").ifEmpty { link.attr("href") }

                val sectionVideos = mutableListOf<app.amisles.hanime.domain.model.HanimeVideo>()
                var sibling: Element? = link.nextElementSibling()
                var maxScan = 0
                while (sibling != null && maxScan < 5) {
                    maxScan++
                    val wrappers = sibling.select(".home-rows-videos-wrapper")
                    if (wrappers.isNotEmpty()) {
                        for (wrapper in wrappers) {
                            val containers = wrapper.select(".video-item-container")
                            for (container in containers) {
                                videoListParser.parseSingleVideoContainer(container, baseUrl)?.let { sectionVideos.add(it) }
                            }
                        }
                        if (sectionVideos.isNotEmpty()) break
                    }
                    val containersInSibling = sibling.select(".video-item-container")
                    if (containersInSibling.isNotEmpty() && sectionVideos.isEmpty()) {
                        for (c in containersInSibling) {
                            videoListParser.parseSingleVideoContainer(c, baseUrl)?.let { sectionVideos.add(it) }
                        }
                    }
                    if (sectionVideos.isNotEmpty()) break
                    sibling = sibling.nextElementSibling()
                }

                AppLogger.log("HomePageParser", "Section '$title' found ${sectionVideos.size} videos, moreUrl: $moreUrl")
                if (sectionVideos.isNotEmpty()) {
                    sections.add(HomeSection(title = title, moreUrl = moreUrl, videos = sectionVideos))
                }
            } catch (e: Exception) {
                AppLogger.logError("HomePageParser", "Error parsing section: ${e.message}", e)
                continue
            }
        }
        return sections
    }

    private fun parseBanner(doc: Document): HanimeBanner? {
        try {
            val bannerWrapper: Element? = doc.selectFirst("#home-banner-wrapper")
            AppLogger.log("HomePageParser", "Banner wrapper found: ${bannerWrapper != null}")

            if (bannerWrapper == null) {
                AppLogger.log("HomePageParser", "Trying alternative banner selectors...")
                val alternativeSelectors = listOf("#banner", ".banner", ".hero", "[id*=banner]", "[class*=banner]")
                for (selector in alternativeSelectors) {
                    val elements = doc.select(selector)
                    AppLogger.log("HomePageParser", "Banner selector '$selector' found ${elements.size} elements")
                }
                return null
            }

            val title: Element? = bannerWrapper.selectFirst("h1")
            val titleText = title?.text()?.trim() ?: return null

            val meta: Element? = bannerWrapper.selectFirst("h4")
            var author = ""
            var viewCount = ""
            var publishTime = ""
            if (meta != null) {
                val metaText = meta.text().trim()
                val parts = metaText.split("•")
                if (parts.size >= 3) {
                    author = parts[0].trim()
                    viewCount = parts[1].trim()
                    publishTime = parts[2].trim()
                }
            }

            val tags = mutableListOf<String>()
            val tagSpans: Elements = bannerWrapper.select("span[style*=border-radius]")
            for (span in tagSpans) {
                val tagText = span.text().trim()
                if (tagText.isNotEmpty()) {
                    tags.add(tagText)
                }
            }

            val playButton: Element? = bannerWrapper.selectFirst("a.home-banner-play-btn")
            val videoUrl = playButton?.attr("abs:href") ?: playButton?.attr("href") ?: ""

            val imageUrl = doc.selectFirst("div[style*=aspect-ratio] img")?.attr("abs:src")
                ?: doc.selectFirst("div[style*=aspect-ratio] img")?.attr("src")
                ?: doc.selectFirst("img[src*=thumbnail]")?.attr("abs:src")
                ?: doc.selectFirst("img[src*=thumbnail]")?.attr("src")
                ?: ""
            AppLogger.log("HomePageParser", "Banner image URL: $imageUrl")

            return HanimeBanner(
                title = titleText,
                author = author,
                viewCount = viewCount,
                publishTime = publishTime,
                tags = tags,
                videoUrl = videoUrl,
                imageUrl = imageUrl
            )
        } catch (e: Exception) {
            AppLogger.logError("HomePageParser", "Error parsing banner: ${e.message}", e)
            return null
        }
    }
}
