package app.amisles.hanime.data.parser

import app.amisles.hanime.domain.model.HanimeBanner
import app.amisles.hanime.domain.model.HomePageData
import app.amisles.hanime.domain.model.HomeSection
import app.amisles.hanime.core.common.util.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import app.amisles.hanime.domain.model.HomeDataEvent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomePageParser @Inject constructor(private val videoListParser: VideoListParser) {

    fun parse(html: String, baseUrl: String): HomePageData {
        val doc: Document = Jsoup.parse(html, baseUrl)
        val sections = parseHomeSections(doc, baseUrl)
        val banner = parseBanner(doc)
        return HomePageData(banner = banner, sections = sections)
    }

    fun parseHomeSections(doc: Document, baseUrl: String): List<HomeSection> {
        val sections = mutableListOf<HomeSection>()
        val titleLinks: Elements = doc.select("a.horizontal-row-title")

        if (titleLinks.isEmpty()) {
            val fallbackVideos = videoListParser.parseVideoList(doc, baseUrl)
            if (fallbackVideos.isNotEmpty()) {
                sections.add(HomeSection(title = "最新上市", moreUrl = "", videos = fallbackVideos.take(8)))
                sections.add(HomeSection(title = "最新上传", moreUrl = "", videos = fallbackVideos.drop(8).take(8)))
            }
            return sections
        }

        for (link in titleLinks) {
            parseSingleSection(link, baseUrl)?.let { sections.add(it) }
        }
        return sections
    }

    /**
     * 流式解析
     */
    fun parseStreaming(html: String, baseUrl: String): Flow<HomeDataEvent> = flow {
        val doc: Document = Jsoup.parse(html, baseUrl)
        emit(HomeDataEvent.Banner(parseBanner(doc)))
        val titleLinks: Elements = doc.select("a.horizontal-row-title")
        if (titleLinks.isEmpty()) {
            val fallbackVideos = videoListParser.parseVideoList(doc, baseUrl)
            if (fallbackVideos.isNotEmpty()) {
                emit(HomeDataEvent.Section(HomeSection(title = "最新上市", moreUrl = "", videos = fallbackVideos.take(8))))
                emit(HomeDataEvent.Section(HomeSection(title = "最新上传", moreUrl = "", videos = fallbackVideos.drop(8).take(8))))
            }
            return@flow
        }
        for (link in titleLinks) {
            parseSingleSection(link, baseUrl)?.let { emit(HomeDataEvent.Section(it)) }
        }
    }

    private fun parseSingleSection(link: Element, baseUrl: String): HomeSection? {
        return try {
            val h3 = link.selectFirst("h3") ?: return null
            val originalTitle = h3.ownText().trim().ifEmpty {
                h3.textNodes().firstOrNull()?.text()?.trim() ?: ""
            }
            if (originalTitle.isEmpty()) return null

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

            if (sectionVideos.isNotEmpty()) {
                HomeSection(title = title, moreUrl = moreUrl, videos = sectionVideos)
            } else {
                null
            }
        } catch (e: IndexOutOfBoundsException) {
            AppLogger.logError("HomePageParser", "Error parsing section: ${e.message}", e)
            null
        } catch (e: NullPointerException) {
            AppLogger.logError("HomePageParser", "Error parsing section: ${e.message}", e)
            null
        }
    }

    private fun parseBanner(doc: Document): HanimeBanner? {
        try {
            val bannerWrapper: Element? = doc.selectFirst("#home-banner-wrapper")

            if (bannerWrapper == null) {
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
            val videoUrl = playButton?.let { it.attr("abs:href").ifEmpty { it.attr("href") } } ?: ""

            val imageUrl = doc.selectFirst("div[style*=aspect-ratio] img")
                ?.let { it.attr("abs:src").ifEmpty { it.attr("src") } }
                ?.takeIf { it.isNotEmpty() }
                ?: doc.selectFirst("img[src*=thumbnail]")
                    ?.let { it.attr("abs:src").ifEmpty { it.attr("src") } }
                ?: ""

            return HanimeBanner(
                title = titleText,
                author = author,
                viewCount = viewCount,
                publishTime = publishTime,
                tags = tags,
                videoUrl = videoUrl,
                imageUrl = imageUrl
            )
        } catch (e: IndexOutOfBoundsException) {
            AppLogger.logError("HomePageParser", "Error parsing banner: ${e.message}", e)
            return null
        } catch (e: NullPointerException) {
            AppLogger.logError("HomePageParser", "Error parsing banner: ${e.message}", e)
            return null
        }
    }
}
