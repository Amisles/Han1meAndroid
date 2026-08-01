package app.amisles.hanime.data.parser

import app.amisles.hanime.domain.model.AuthorPageData
import app.amisles.hanime.domain.model.DownloadQuality
import app.amisles.hanime.domain.model.HanimeBanner
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.HomePageData
import app.amisles.hanime.domain.model.HomeSection
import app.amisles.hanime.domain.model.PlaylistDetail
import app.amisles.hanime.domain.model.PlaylistInfo
import app.amisles.hanime.domain.model.PlaylistSummary
import app.amisles.hanime.domain.model.SearchResult
import app.amisles.hanime.domain.model.VideoDetail
import app.amisles.hanime.domain.model.VideoSource
import app.amisles.hanime.core.common.util.AppLogger
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

class HanimeParser {

    // 清理点赞率文本，移除图标和标签前缀，只保留数字百分比
    private fun cleanLikeRate(text: String): String {
        return text.replace("👍", "")
            .replace("thumb-up", "")
            .replace("thumb_up", "")
            .replace("thumb up", "")
            .trim()
    }

    private val traditionalToSimplified = mapOf(
        "最新上市" to "最新上市",
        "最新上傳" to "最新上传",
        "他們在看" to "他们在看",
        "裏番" to "里番",
        "泡麵番" to "泡面番",
        "Motion Anime" to "Motion Anime",
        "3DCG" to "3DCG",
        "2.5D" to "2.5D",
        "2D動畫" to "2D动画",
        "AI生成" to "AI生成",
        "MMD" to "MMD",
        "Cosplay" to "Cosplay",
        "H動漫" to "H动漫",
        "影片" to "影片",
        "播放清單" to "播放清单"
    )

    private fun convertToSimplified(text: String): String {
        var result = text
        for ((traditional, simplified) in traditionalToSimplified) {
            result = result.replace(traditional, simplified)
        }
        return result
    }

    fun parseHomePage(html: String, baseUrl: String): HomePageData {
        val parseStartTime = System.currentTimeMillis()
        android.util.Log.i("HanimeParser", "---------- HTML解析开始 ----------")

        AppLogger.log("HanimeParser", "parseHomePage called, baseUrl: $baseUrl")

        // Jsoup解析DOM
        val domStartTime = System.currentTimeMillis()
        val doc: Document = Jsoup.parse(html, baseUrl)
        val domEndTime = System.currentTimeMillis()
        android.util.Log.i("HanimeParser", "  📄 Jsoup DOM解析: ${domEndTime - domStartTime}ms")

        AppLogger.log("HanimeParser", "HTML parsed successfully")

        // 解析sections
        val sectionsStartTime = System.currentTimeMillis()
        val sections = parseHomeSections(doc, baseUrl)
        val sectionsEndTime = System.currentTimeMillis()
        android.util.Log.i("HanimeParser", "  📦 Sections解析: ${sectionsEndTime - sectionsStartTime}ms (${sections.size}个section, ${sections.sumOf { it.videos.size }}个视频)")

        AppLogger.log("HanimeParser", "Found ${sections.size} home sections, video counts: ${sections.joinToString { "${it.title}:${it.videos.size}" }}")

        // 解析banner
        val bannerStartTime = System.currentTimeMillis()
        val banner = parseBanner(doc)
        val bannerEndTime = System.currentTimeMillis()
        android.util.Log.i("HanimeParser", "  🎯 Banner解析: ${bannerEndTime - bannerStartTime}ms")

        AppLogger.log("HanimeParser", "Banner found: ${banner != null}")

        val parseEndTime = System.currentTimeMillis()
        android.util.Log.i("HanimeParser", "  ⏱️ 解析总耗时: ${parseEndTime - parseStartTime}ms")
        android.util.Log.i("HanimeParser", "---------- HTML解析完成 ----------")

        return HomePageData(banner = banner, sections = sections)
    }

    fun parseHomeSections(doc: Document, baseUrl: String): List<HomeSection> {
        val sections = mutableListOf<HomeSection>()
        val titleLinks: Elements = doc.select("a.horizontal-row-title")
        AppLogger.log("HanimeParser", "Found ${titleLinks.size} horizontal-row-title links")
        
        if (titleLinks.isEmpty()) {
            val fallbackVideos = parseVideoList(doc, baseUrl)
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

                val title = convertToSimplified(originalTitle)
                val moreUrl = link.attr("abs:href").ifEmpty { link.attr("href") }

                val sectionVideos = mutableListOf<HanimeVideo>()
                var sibling: Element? = link.nextElementSibling()
                var maxScan = 0
                while (sibling != null && maxScan < 5) {
                    maxScan++
                    val wrappers = sibling.select(".home-rows-videos-wrapper")
                    if (wrappers.isNotEmpty()) {
                        for (wrapper in wrappers) {
                            val containers = wrapper.select(".video-item-container")
                            for (container in containers) {
                                parseSingleVideoContainer(container, baseUrl)?.let { sectionVideos.add(it) }
                            }
                        }
                        if (sectionVideos.isNotEmpty()) break
                    }
                    val containersInSibling = sibling.select(".video-item-container")
                    if (containersInSibling.isNotEmpty() && sectionVideos.isEmpty()) {
                        for (c in containersInSibling) {
                            parseSingleVideoContainer(c, baseUrl)?.let { sectionVideos.add(it) }
                        }
                    }
                    if (sectionVideos.isNotEmpty()) break
                    sibling = sibling.nextElementSibling()
                }

                AppLogger.log("HanimeParser", "Section '$title' found ${sectionVideos.size} videos, moreUrl: $moreUrl")
                if (sectionVideos.isNotEmpty()) {
                    sections.add(HomeSection(title = title, moreUrl = moreUrl, videos = sectionVideos))
                }
            } catch (e: Exception) {
                AppLogger.logError("HanimeParser", "Error parsing section: ${e.message}", e)
                continue
            }
        }
        return sections
    }

    fun parseSearchPage(html: String, baseUrl: String): List<HanimeVideo> {
        AppLogger.log("HanimeParser", "parseSearchPage called, baseUrl: $baseUrl")
        val doc: Document = Jsoup.parse(html, baseUrl)
        val videos = parseVideoList(doc, baseUrl)
        AppLogger.log("HanimeParser", "Search found ${videos.size} videos before filtering")
        val filtered = videos.filter { 
                it.videoUrl.startsWith("https://hanime1.me/watch") || 
                it.videoUrl.startsWith("https://hanimeone.me/watch") || 
                it.videoUrl.startsWith("/watch") 
            }
        AppLogger.log("HanimeParser", "Search found ${filtered.size} videos after filtering")
        return filtered
    }

    fun parseSearchPageWithPagination(html: String, baseUrl: String): SearchResult {
        AppLogger.log("HanimeParser", "parseSearchPageWithPagination called, baseUrl: $baseUrl")
        val doc: Document = Jsoup.parse(html, baseUrl)
        val videos = parseVideoList(doc, baseUrl)
        AppLogger.log("HanimeParser", "Search found ${videos.size} videos before filtering")
        val filtered = videos.filter {
            it.videoUrl.startsWith("https://hanime1.me/watch") ||
            it.videoUrl.startsWith("https://hanimeone.me/watch") ||
            it.videoUrl.startsWith("/watch")
        }
        AppLogger.log("HanimeParser", "Search found ${filtered.size} videos after filtering")

        val (currentPage, totalPages, hasNextPage) = parsePagination(doc)
        AppLogger.log("HanimeParser", "Pagination: currentPage=$currentPage, totalPages=$totalPages, hasNextPage=$hasNextPage")

        return SearchResult(
            videos = filtered,
            currentPage = currentPage,
            totalPages = totalPages,
            hasNextPage = hasNextPage
        )
    }

    private fun parsePagination(doc: Document): Triple<Int, Int, Boolean> {
        var currentPage = 1
        var totalPages = 1
        var hasNextPage = false

        try {
            val activeItem = doc.selectFirst("ul.pagination li.page-item.active span.page-link")
            activeItem?.text()?.trim()?.toIntOrNull()?.let { currentPage = it }

            val pageLinks = doc.select("ul.pagination li.page-item a.page-link")
            for (link in pageLinks) {
                val rel = link.attr("rel")
                if (rel == "next") {
                    hasNextPage = true
                }
                link.text().trim().toIntOrNull()?.let { num ->
                    if (num > totalPages) totalPages = num
                }
            }

            val skipInput = doc.selectFirst("#skip-page-input")
            if (skipInput != null) {
                skipInput.attr("oninput").let { oninput ->
                    val maxMatch = Regex("validateNumberInput\\(this,\\s*\\d+,\\s*(\\d+)").find(oninput)
                    maxMatch?.groupValues?.get(1)?.toIntOrNull()?.let { max ->
                        if (max > totalPages) totalPages = max
                    }
                }
            }

            if (currentPage < totalPages) hasNextPage = true
        } catch (e: Exception) {
            AppLogger.logError("HanimeParser", "Error parsing pagination: ${e.message}", e)
        }

        return Triple(currentPage, totalPages, hasNextPage)
    }

    private fun parseVideoList(doc: Document, baseUrl: String): List<HanimeVideo> {
        val videos = mutableListOf<HanimeVideo>()
        
        val videoContainers: Elements = doc.select(".video-item-container")
        AppLogger.log("HanimeParser", "Found ${videoContainers.size} .video-item-container elements")
        
        if (videoContainers.isEmpty()) {
            AppLogger.log("HanimeParser", "Trying alternative selectors...")
            val alternativeSelectors = listOf(
                ".video-card", ".video-item", ".card", ".horizontal-card", "[class*=video]", "[class*=card]"
            )
            for (selector in alternativeSelectors) {
                val elements = doc.select(selector)
                AppLogger.log("HanimeParser", "Selector '$selector' found ${elements.size} elements")
            }
        }

        for (container in videoContainers) {
            parseSingleVideoContainer(container, baseUrl)?.let { videos.add(it) }
        }
        return videos
    }

    private fun parseSingleVideoContainer(container: Element, baseUrl: String): HanimeVideo? {
        return try {
            val videoLink: Element? = container.selectFirst(".video-link")
                ?: container.selectFirst(".thumb-container a")
            val videoUrl = videoLink?.attr("abs:href") ?: videoLink?.attr("href") ?: return null
            AppLogger.log("HanimeParser", "Video URL: $videoUrl")

            val videoId = extractVideoId(videoUrl)
            if (videoId.isEmpty()) return null

            val title: Element? = container.selectFirst(".title")
                ?: container.selectFirst(".video-title")
            val titleText = title?.text()?.trim() ?: return null
            AppLogger.log("HanimeParser", "Video title: $titleText")

            val thumbnail: Element? = container.selectFirst(".main-thumb")
            val thumbnailUrl = thumbnail?.attr("abs:src") ?: thumbnail?.attr("src") ?: generatePlaceholderThumbnail(videoId)

            val duration: Element? = container.selectFirst(".duration")
            val durationText = duration?.text()?.trim() ?: ""

            val statsContainer: Element? = container.selectFirst(".stats-container")
            var likeRate = ""
            var viewCount = ""
            if (statsContainer != null) {
                val statItems: Elements = statsContainer.select(".stat-item")
                for (stat in statItems) {
                    val text = stat.text().trim()
                    if (text.contains("%")) {
                        likeRate = cleanLikeRate(text)
                    } else {
                        viewCount = text.trim()
                    }
                }
            }

            val subtitle: Element? = container.selectFirst(".subtitle a")
                ?: container.selectFirst(".meta-author a")
            var author = ""
            var publishTime = ""
            if (subtitle != null) {
                val subtitleText = subtitle.text().trim()
                val parts = subtitleText.split("•")
                if (parts.size >= 2) {
                    author = parts[0].trim()
                    publishTime = parts[1].trim()
                } else if (parts.size == 1) {
                    author = parts[0].trim()
                }
            }

            val finalVideoUrl = if (videoUrl.startsWith("/")) "$baseUrl$videoUrl" else videoUrl

            HanimeVideo(
                id = videoId,
                title = titleText,
                thumbnailUrl = thumbnailUrl,
                duration = durationText,
                likeRate = likeRate,
                viewCount = viewCount,
                author = author,
                publishTime = publishTime,
                videoUrl = finalVideoUrl
            )
        } catch (e: Exception) {
            AppLogger.logError("HanimeParser", "Error parsing single video: ${e.message}", e)
            null
        }
    }

    private fun parseBanner(doc: Document): HanimeBanner? {
        try {
            val bannerWrapper: Element? = doc.selectFirst("#home-banner-wrapper")
            AppLogger.log("HanimeParser", "Banner wrapper found: ${bannerWrapper != null}")
            
            if (bannerWrapper == null) {
                AppLogger.log("HanimeParser", "Trying alternative banner selectors...")
                val alternativeSelectors = listOf("#banner", ".banner", ".hero", "[id*=banner]", "[class*=banner]")
                for (selector in alternativeSelectors) {
                    val elements = doc.select(selector)
                    AppLogger.log("HanimeParser", "Banner selector '$selector' found ${elements.size} elements")
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
            AppLogger.log("HanimeParser", "Banner image URL: $imageUrl")

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
            AppLogger.logError("HanimeParser", "Error parsing banner: ${e.message}", e)
            return null
        }
    }

    private fun extractVideoId(url: String): String {
        val regex = Regex("v=(\\d+)")
        val match = regex.find(url)
        return match?.groupValues?.get(1) ?: ""
    }

    private fun generatePlaceholderThumbnail(videoId: String): String {
        return "https://vdownload.hembed.com/image/thumbnail/${videoId}l.jpg"
    }

    fun parseWatchPage(html: String, baseUrl: String): VideoDetail? {
        AppLogger.log("HanimeParser", "parseWatchPage called")
        try {
            val doc: Document = Jsoup.parse(html, baseUrl)

            val videoTag: Element? = doc.selectFirst("video#player")
            val defaultSourceUrl = videoTag?.attr("abs:src") ?: videoTag?.attr("src") ?: ""
            val posterUrl = videoTag?.attr("abs:poster") ?: videoTag?.attr("poster") ?: ""
            AppLogger.log("HanimeParser", "Video src: $defaultSourceUrl")
            AppLogger.log("HanimeParser", "Poster: $posterUrl")

            val sources = mutableListOf<VideoSource>()
            val sourceTags = doc.select("video#player source")
            for (source in sourceTags) {
                val src = source.attr("abs:src") ?: source.attr("src") ?: ""
                val size = source.attr("size")?.toIntOrNull() ?: 0
                val type = source.attr("type") ?: ""
                if (src.isNotEmpty()) {
                    sources.add(VideoSource(url = src, resolution = "${size}p", size = size))
                }
            }
            sources.sortByDescending { it.size }
            AppLogger.log("HanimeParser", "Found ${sources.size} video sources")

            val titleElement = doc.selectFirst("h3#shareBtn-title")
                ?: doc.selectFirst("h3.single-video-title")
                ?: doc.selectFirst("h3")
            val rawTitle = titleElement?.text()?.trim() ?: ""
            val title = if (rawTitle.startsWith("[")) {
                val closingBracket = rawTitle.indexOf(']')
                if (closingBracket > 0 && closingBracket < rawTitle.length - 1) {
                    rawTitle.substring(closingBracket + 1).trim()
                } else rawTitle
            } else rawTitle
            AppLogger.log("HanimeParser", "Title: $title (raw: $rawTitle)")

            val tags = mutableListOf<String>()
            val tagElements = doc.select(".video-tags-wrapper .single-video-tag a")
            val seenTags = mutableSetOf<String>()
            for (link in tagElements) {
                val tagText = link.text().trim()
                if (tagText.isNotEmpty() && !seenTags.contains(tagText) && tagText.length < 30) {
                    tags.add(tagText)
                    seenTags.add(tagText)
                }
            }
            AppLogger.log("HanimeParser", "Found ${tags.size} tags")

            val releaseDate = Regex("(20\\d{2}/\\d{2}/\\d{2})").find(html)?.value ?: ""

            val fileSizeMatch = Regex("([\\d.]+\\s*(?:GB|MB|KB))", RegexOption.IGNORE_CASE).find(html)
            val fileSize = fileSizeMatch?.value ?: ""

            val author = doc.selectFirst("a#video-artist-name")?.text()?.trim() ?: ""
            AppLogger.log("HanimeParser", "Author: $author")
            
            val authorLink = doc.selectFirst("a[href*=\"/user/\"]")
            val authorPageUrl = authorLink?.attr("abs:href") ?: ""
            
            val avatarContainer = doc.selectFirst("div[style*=\"position: relative; display: inline-block;\"]")
            val authorAvatarUrl = avatarContainer?.selectFirst("img[style*=\"position: absolute\"]")?.attr("abs:src") 
                ?: avatarContainer?.selectFirst("img#video-user-avatar")?.attr("abs:src") 
                ?: ""
            AppLogger.log("HanimeParser", "Author avatar: $authorAvatarUrl")
            
            // Description: detailed caption text (Title/Brand/Release/File size/links)
            val description = doc.selectFirst(".video-caption-text")?.wholeText()?.trim() ?: ""
            AppLogger.log("HanimeParser", "Description length: ${description.length}")

            val relatedVideos = parseVideoList(doc, baseUrl)
            AppLogger.log("HanimeParser", "Found ${relatedVideos.size} related videos")

            val playlist = parsePlaylist(doc, baseUrl)
            AppLogger.log("HanimeParser", "Playlist: ${playlist != null}")

            val filteredRelatedVideos = if (playlist != null) {
                val playlistUrls = playlist.videos.map { it.videoUrl }.toSet()
                relatedVideos.filter { it.videoUrl !in playlistUrls }
            } else {
                relatedVideos
            }
            AppLogger.log("HanimeParser", "Filtered related videos: ${filteredRelatedVideos.size} (removed ${relatedVideos.size - filteredRelatedVideos.size} playlist duplicates)")

            if (defaultSourceUrl.isEmpty() && sources.isNotEmpty()) {
                val defaultSource = sources.find { it.size == 720 } ?: sources.first()
                return VideoDetail(
                    title = title,
                    posterUrl = posterUrl,
                    videoSources = sources,
                    defaultSourceUrl = defaultSource.url,
                    tags = tags,
                    releaseDate = releaseDate,
                    fileSize = fileSize,
                    author = author,
                    authorAvatarUrl = authorAvatarUrl,
                    authorPageUrl = authorPageUrl,
                    description = description,
                    relatedVideos = filteredRelatedVideos,
                    playlist = playlist
                )
            }

            return VideoDetail(
                title = title,
                posterUrl = posterUrl,
                videoSources = sources,
                defaultSourceUrl = defaultSourceUrl,
                tags = tags,
                releaseDate = releaseDate,
                fileSize = fileSize,
                author = author,
                authorAvatarUrl = authorAvatarUrl,
                authorPageUrl = authorPageUrl,
                description = description,
                relatedVideos = filteredRelatedVideos,
                playlist = playlist
            )
        } catch (e: Exception) {
            AppLogger.logError("HanimeParser", "Error parsing watch page: ${e.message}", e)
            return null
        }
    }

    fun parseDownloadPage(html: String, baseUrl: String): List<DownloadQuality> {
        AppLogger.log("HanimeParser", "parseDownloadPage called")
        val qualities = mutableListOf<DownloadQuality>()
        try {
            val doc: Document = Jsoup.parse(html, baseUrl)
            val table = doc.selectFirst("table.download-table")
            if (table == null) {
                AppLogger.log("HanimeParser", "No download-table found, trying alternative selectors")
                val altTables = doc.select("table")
                for (t in altTables) {
                    if (t.text().contains("下載") || t.text().contains("畫質")) {
                        parseDownloadTable(t, qualities)
                        if (qualities.isNotEmpty()) break
                    }
                }
            } else {
                parseDownloadTable(table, qualities)
            }
            AppLogger.log("HanimeParser", "Found ${qualities.size} download qualities")
            return qualities
        } catch (e: Exception) {
            AppLogger.logError("HanimeParser", "Error parsing download page: ${e.message}", e)
            return emptyList()
        }
    }

    private fun parseDownloadTable(table: Element, qualities: MutableList<DownloadQuality>) {
        val rows = table.select("tbody tr")
        AppLogger.log("HanimeParser", "Download table has ${rows.size} rows")
        for (row in rows) {
            try {
                val cells = row.select("td")
                if (cells.size < 4) continue

                val qualityText = cells.getOrNull(1)?.text()?.trim() ?: continue
                val fileType = cells.getOrNull(2)?.text()?.trim() ?: "mp4"
                val fileSize = cells.getOrNull(3)?.text()?.trim() ?: "N/A"

                val downloadLink = cells.getOrNull(cells.size - 1)?.selectFirst("a[data-url]")
                val downloadUrl = downloadLink?.attr("data-url")?.trim() ?: continue

                val resolution = Regex("\\((\\d+p)\\)").find(qualityText)?.groupValues?.get(1)
                    ?: if (qualityText.contains("1080")) "1080p"
                    else if (qualityText.contains("720")) "720p"
                    else if (qualityText.contains("480")) "480p"
                    else ""

                qualities.add(
                    DownloadQuality(
                        quality = qualityText,
                        resolution = resolution,
                        fileType = fileType,
                        fileSize = fileSize,
                        downloadUrl = downloadUrl
                    )
                )
                AppLogger.log("HanimeParser", "Quality: $qualityText, URL: ${downloadUrl.take(50)}...")
            } catch (e: Exception) {
                AppLogger.logError("HanimeParser", "Error parsing download row: ${e.message}", e)
                continue
            }
        }
    }

    private fun parsePlaylist(doc: Document, baseUrl: String): PlaylistInfo? {
        try {
            val playlistWrapper = doc.selectFirst(".video-playlist-wrapper")
            if (playlistWrapper == null) {
                AppLogger.log("HanimeParser", "No playlist wrapper found")
                return null
            }

            val topBlock = playlistWrapper.selectFirst("#playlist-top-block")
            
            val titleElement = topBlock?.selectFirst("h4 a")
            val playlistTitle = titleElement?.text()?.trim() ?: ""
            
            val authorLink = topBlock?.selectFirst("div[style*=\"font-size: 12px\"] a")
            val author = authorLink?.text()?.trim() ?: ""
            
            val videoCountText = topBlock?.selectFirst("div[style*=\"font-size: 12px\"] span:last-child")?.text() ?: "0"
            val videoCount = Regex("(\\d+)").find(videoCountText)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            
            val videos = mutableListOf<HanimeVideo>()
            val playlistItems = playlistWrapper.select(".playlist-hover-wrap")
            
            for (item in playlistItems) {
                try {
                    val videoCard = item.selectFirst(".playlist-video-card") ?: continue
                    
                    val videoLink = videoCard.selectFirst("a[href*=\"watch?v=\"]")
                    val videoUrl = videoLink?.attr("abs:href") ?: ""
                    if (videoUrl.isEmpty()) continue
                    
                    val thumbnail = videoCard.selectFirst("img.main-thumb")?.attr("abs:src") ?: ""
                    val duration = videoCard.selectFirst(".duration")?.text()?.trim() ?: ""
                    
                    val statsContainer = videoCard.selectFirst(".stats-container")
                    val likeRate = statsContainer?.selectFirst(".stat-item")?.text()?.trim()?.let { cleanLikeRate(it) } ?: ""
                    val viewCount = statsContainer?.select("div.stat-item")?.getOrNull(1)?.text() ?: ""
                    
                    val title = videoCard.selectFirst(".video-title a")?.text()?.trim() ?: ""
                    
                    val videoAuthor = videoCard.selectFirst(".meta-author a")?.text()?.trim() ?: ""
                    
                    val metaStats = videoCard.selectFirst(".meta-stats")?.text()?.split("•") ?: listOf()
                    val publishTime = metaStats.lastOrNull()?.trim() ?: ""
                    
                    val videoId = extractVideoId(videoUrl)
                    
                    videos.add(
                        HanimeVideo(
                            id = videoId,
                            title = title,
                            thumbnailUrl = thumbnail,
                            duration = duration,
                            likeRate = likeRate,
                            viewCount = viewCount,
                            author = videoAuthor,
                            publishTime = publishTime,
                            videoUrl = videoUrl
                        )
                    )
                } catch (e: Exception) {
                    AppLogger.logError("HanimeParser", "Error parsing playlist item: ${e.message}", e)
                }
            }
            
            if (playlistTitle.isEmpty() || videos.isEmpty()) {
                return null
            }
            
            AppLogger.log("HanimeParser", "Parsed playlist: $playlistTitle by $author, ${videos.size} videos")
            return PlaylistInfo(
                title = playlistTitle,
                author = author,
                videoCount = videoCount,
                videos = videos
            )
        } catch (e: Exception) {
            AppLogger.logError("HanimeParser", "Error parsing playlist: ${e.message}", e)
            return null
        }
    }

    fun parseAuthorPage(html: String, baseUrl: String): AuthorPageData? {
        AppLogger.log("HanimeParser", "parseAuthorPage called")
        try {
            val doc: Document = Jsoup.parse(html, baseUrl)

            val authorName = doc.selectFirst(".profile-display-name")?.text()?.trim() ?: ""
            val authorAvatarUrl = doc.selectFirst(".profile-avatar-wrapper img")?.attr("abs:src") ?: ""

            val authorIdText = doc.selectFirst(".profile-sub-stats-id")?.text()?.trim() ?: ""
            val authorId = authorIdText.replace("@", "").trim()

            val subStatsElement = doc.selectFirst(".profile-sub-stats-new-line")
            val subStats = subStatsElement?.text()?.trim() ?: ""
            AppLogger.log("HanimeParser", "subStats element found: ${subStatsElement != null}, text: '$subStats'")
            AppLogger.log("HanimeParser", "subStats length: ${subStats.length}, bytes: ${subStats.toByteArray(Charsets.UTF_8).joinToString(" ") { "%02x".format(it) }}")
            val (subscriberCount, videoCount) = parseSubscriberStats(subStats)

            AppLogger.log("HanimeParser", "Looking for horizontal-row-title elements...")
            val sectionLinks = doc.select("a.horizontal-row-title")
            AppLogger.log("HanimeParser", "Found ${sectionLinks.size} horizontal-row-title links")
            for ((index, link) in sectionLinks.withIndex()) {
                val h3 = link.selectFirst("h3")
                val h3OwnText = h3?.ownText()?.trim() ?: ""
                val h3FullText = h3?.text()?.trim() ?: ""
                AppLogger.log("HanimeParser", "  [$index] h3 ownText='$h3OwnText', fullText='$h3FullText'")
            }

            val videos = parseSectionVideos(doc, baseUrl, "影片")
            val playlists = parseSectionPlaylists(doc, baseUrl, "播放清单")  // 支持繁简体匹配

            val uploadedLink = doc.select("a.horizontal-row-title").find {
                val h3Text = it.selectFirst("h3")?.ownText()?.trim() ?: ""
                h3Text.startsWith("影片")
            }?.attr("abs:href") ?: ""
            val playlistsLink = doc.select("a.horizontal-row-title").find {
                val h3Text = it.selectFirst("h3")?.ownText()?.trim() ?: ""
                h3Text.startsWith("播放清单") || h3Text.startsWith("播放清單")  // 支持繁简体
            }?.attr("abs:href") ?: ""

            AppLogger.log("HanimeParser", "Parsed author page: $authorName ($authorId), sub=$subscriberCount, vid=$videoCount, ${videos.size} videos, ${playlists.size} playlists")
            AppLogger.log("HanimeParser", "Links: uploaded='$uploadedLink', playlists='$playlistsLink'")

            return AuthorPageData(
                authorId = authorId,
                authorName = authorName,
                authorAvatarUrl = authorAvatarUrl,
                subscriberCount = subscriberCount,
                videoCount = videoCount,
                videos = videos,
                playlists = playlists,
                uploadedPageUrl = uploadedLink,
                playlistsPageUrl = playlistsLink
            )
        } catch (e: Exception) {
            AppLogger.logError("HanimeParser", "Error parsing author page: ${e.message}", e)
            return null
        }
    }

    fun parseVideoListPage(html: String, baseUrl: String): List<HanimeVideo> {
        AppLogger.log("HanimeParser", "parseVideoListPage called")
        try {
            val doc: Document = Jsoup.parse(html, baseUrl)
            return parseAuthorVideos(doc, baseUrl)
        } catch (e: Exception) {
            AppLogger.logError("HanimeParser", "Error parsing video list page: ${e.message}", e)
            return emptyList()
        }
    }

    fun parsePlaylistListPage(html: String, baseUrl: String): List<PlaylistSummary> {
        AppLogger.log("HanimeParser", "parsePlaylistListPage called")
        try {
            val doc: Document = Jsoup.parse(html, baseUrl)
            return parsePlaylistSummaries(doc, baseUrl)
        } catch (e: Exception) {
            AppLogger.logError("HanimeParser", "Error parsing playlist list page: ${e.message}", e)
            return emptyList()
        }
    }

    fun parsePlaylistDetailPage(html: String, baseUrl: String): PlaylistDetail? {
        AppLogger.log("HanimeParser", "parsePlaylistDetailPage called")
        try {
            val doc: Document = Jsoup.parse(html, baseUrl)

            val title = doc.selectFirst(".playlist-title")?.text()?.trim() ?: ""
            val coverUrl = doc.selectFirst(".playlist-main-thumbnail")?.attr("abs:src") ?: ""

            val authorInfo = doc.selectFirst(".playlist-author-info")
            val author = authorInfo?.selectFirst("a")?.text()?.trim() ?: ""
            val authorAvatarUrl = authorInfo?.selectFirst("img.author-avatar")?.attr("abs:src") ?: ""

            val statsText = doc.selectFirst(".playlist-stats")?.text()?.trim() ?: ""
            val videoCountMatch = Regex("(\\d+)").find(statsText)
            val videoCount = videoCountMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

            val viewCountMatch = Regex("觀看次數：([\\d,]+)\\s*次").find(statsText)
            val viewCount = viewCountMatch?.groupValues?.get(1) ?: "0"

            val description = doc.selectFirst(".playlist-description")?.text()?.trim() ?: ""

            val videos = parsePlaylistDetailVideos(doc, baseUrl)

            AppLogger.log("HanimeParser", "Parsed playlist detail: $title, ${videos.size} videos")

            return PlaylistDetail(
                title = title,
                coverUrl = coverUrl,
                author = author,
                authorAvatarUrl = authorAvatarUrl,
                videoCount = videoCount,
                viewCount = viewCount,
                description = description,
                videos = videos
            )
        } catch (e: Exception) {
            AppLogger.logError("HanimeParser", "Error parsing playlist detail page: ${e.message}", e)
            return null
        }
    }

    private fun parseSectionVideos(doc: Document, baseUrl: String, sectionTitle: String): List<HanimeVideo> {
        val videos = mutableListOf<HanimeVideo>()
        val sectionLinks = doc.select("a.horizontal-row-title")
        for (link in sectionLinks) {
            val h3 = link.selectFirst("h3") ?: continue
            val h3Text = h3.ownText().trim()
            AppLogger.log("HanimeParser", "parseSectionVideos h3 text: '$h3Text' (looking for '$sectionTitle')")
            if (h3Text.startsWith(sectionTitle)) {
                var sibling = link.nextElementSibling()
                while (sibling != null) {
                    val wrapper = sibling.selectFirst(".home-rows-videos-wrapper")
                    if (wrapper != null) {
                        val items = wrapper.select(".video-item-container")
                        for (item in items) {
                            val video = parseVideoItem(item, baseUrl) ?: continue
                            videos.add(video)
                        }
                        break
                    }
                    sibling = sibling.nextElementSibling()
                }
                break
            }
        }
        AppLogger.log("HanimeParser", "parseSectionVideos result: ${videos.size} videos for '$sectionTitle'")
        return videos
    }

    private fun parseSectionPlaylists(doc: Document, baseUrl: String, sectionTitle: String): List<PlaylistSummary> {
        val playlists = mutableListOf<PlaylistSummary>()
        val sectionLinks = doc.select("a.horizontal-row-title")
        AppLogger.log("HanimeParser", "parseSectionPlaylists: Found ${sectionLinks.size} section links, looking for '$sectionTitle'")

        for (link in sectionLinks) {
            val h3 = link.selectFirst("h3") ?: continue
            val h3Text = h3.ownText().trim()
            val h3FullText = h3.text().trim()

            AppLogger.log("HanimeParser", "  Checking h3: ownText='$h3Text', fullText='$h3FullText'")

            // 支持繁简体匹配：播放清单(简体) 或 播放清單(繁体)
            val matches = h3Text.startsWith(sectionTitle) ||
                          h3Text.contains(sectionTitle) ||
                          (sectionTitle == "播放清单" && (h3Text.startsWith("播放清單") || h3Text.contains("播放清單"))) ||
                          (sectionTitle == "影片" && (h3Text.startsWith("影片")))

            if (matches) {
                AppLogger.log("HanimeParser", "  Found matching section '$sectionTitle', looking for .home-rows-videos-wrapper")

                var sibling = link.nextElementSibling()
                var siblingIndex = 0
                while (sibling != null && siblingIndex < 10) {
                    siblingIndex++
                    AppLogger.log("HanimeParser", "    Sibling $siblingIndex: tag='${sibling.tagName()}', class='${sibling.className()}'")

                    val wrapper = sibling.selectFirst(".home-rows-videos-wrapper")
                    if (wrapper != null) {
                        AppLogger.log("HanimeParser", "    Found .home-rows-videos-wrapper")
                        val items = wrapper.select(".video-item-container")
                        AppLogger.log("HanimeParser", "    Found ${items.size} .video-item-container elements")

                        for ((idx, item) in items.withIndex()) {
                            try {
                                AppLogger.log("HanimeParser", "      Processing playlist item $idx")

                                val videoLink = item.selectFirst("a.video-link")
                                val playlistUrl = videoLink?.attr("abs:href") ?: ""
                                AppLogger.log("HanimeParser", "      Playlist URL: $playlistUrl")

                                if (playlistUrl.isEmpty()) {
                                    AppLogger.log("HanimeParser", "      Skipped: empty URL")
                                    continue
                                }

                                val thumbnail = item.selectFirst("img.main-thumb")?.attr("abs:src") ?: ""
                                val title = item.selectFirst(".title")?.text()?.trim() ?: ""
                                val author = item.selectFirst(".subtitle a")?.text()?.trim() ?: ""
                                val subtitleTime = item.selectFirst(".subtitle-time")?.text()?.trim()?.replace("•", "")?.trim() ?: ""

                                val statsContainer = item.selectFirst(".stats-container")
                                val videoCount = statsContainer?.selectFirst(".stat-item")?.text()?.trim() ?: ""

                                AppLogger.log("HanimeParser", "      Parsed: title='$title', videoCount='$videoCount', author='$author'")

                                playlists.add(PlaylistSummary(
                                    title = title,
                                    thumbnailUrl = thumbnail,
                                    videoCount = videoCount,
                                    author = author,
                                    publishTime = subtitleTime,
                                    playlistUrl = playlistUrl
                                ))
                            } catch (e: Exception) {
                                AppLogger.logError("HanimeParser", "Error parsing section playlist item: ${e.message}", e)
                            }
                        }
                        break
                    }
                    sibling = sibling.nextElementSibling()
                }
                break
            }
        }
        AppLogger.log("HanimeParser", "parseSectionPlaylists result: ${playlists.size} playlists for '$sectionTitle'")
        return playlists
    }

    private fun parsePlaylistSummaries(doc: Document, baseUrl: String): List<PlaylistSummary> {
        val playlists = mutableListOf<PlaylistSummary>()
        val items = doc.select(".video-item-container")
        for (item in items) {
            try {
                val link = item.selectFirst("a.video-link")
                val playlistUrl = link?.attr("abs:href") ?: ""
                if (playlistUrl.isEmpty()) continue

                val thumbnail = item.selectFirst("img.main-thumb")?.attr("abs:src") ?: ""
                val title = item.selectFirst(".title")?.text()?.trim() ?: ""
                val author = item.selectFirst(".subtitle a")?.text()?.trim() ?: ""
                val subtitleTime = item.selectFirst(".subtitle-time")?.text()?.trim()?.replace("•", "")?.trim() ?: ""
                val statsContainer = item.selectFirst(".stats-container")
                val videoCount = statsContainer?.selectFirst(".stat-item")?.text()?.trim() ?: ""

                playlists.add(PlaylistSummary(
                    title = title,
                    thumbnailUrl = thumbnail,
                    videoCount = videoCount,
                    author = author,
                    publishTime = subtitleTime,
                    playlistUrl = playlistUrl
                ))
            } catch (e: Exception) {
                AppLogger.logError("HanimeParser", "Error parsing playlist summary: ${e.message}", e)
            }
        }
        return playlists
    }

    private fun parsePlaylistDetailVideos(doc: Document, baseUrl: String): List<HanimeVideo> {
        val videos = mutableListOf<HanimeVideo>()
        val items = doc.select(".playlist-video-card")
        for (item in items) {
            try {
                val videoLink = item.selectFirst("a[href*=\"watch?v=\"]")
                val videoUrl = videoLink?.attr("abs:href") ?: ""
                if (videoUrl.isEmpty()) continue

                val thumbnail = item.selectFirst("img.main-thumb")?.attr("abs:src") ?: ""
                val duration = item.selectFirst(".duration")?.text()?.trim() ?: ""

                val statsContainer = item.selectFirst(".stats-container")
                val likeRate = statsContainer?.selectFirst(".stat-item")?.text()?.trim()?.let { cleanLikeRate(it) } ?: ""
                val viewCount = statsContainer?.select("div.stat-item")?.getOrNull(1)?.text() ?: ""

                val title = item.selectFirst(".video-title a")?.text()?.trim() ?: ""
                val author = item.selectFirst(".meta-author a")?.text()?.trim() ?: ""
                val publishTime = item.selectFirst(".meta-stats span")?.text()?.trim() ?: ""

                val videoId = extractVideoId(videoUrl)

                videos.add(HanimeVideo(
                    id = videoId,
                    title = title,
                    thumbnailUrl = thumbnail,
                    duration = duration,
                    likeRate = likeRate,
                    viewCount = viewCount,
                    author = author,
                    publishTime = publishTime,
                    videoUrl = videoUrl
                ))
            } catch (e: Exception) {
                AppLogger.logError("HanimeParser", "Error parsing playlist detail video: ${e.message}", e)
            }
        }
        return videos
    }

    private fun parseVideoItem(item: org.jsoup.nodes.Element, baseUrl: String): HanimeVideo? {
        try {
            val videoLink = item.selectFirst("a.video-link")
            val videoUrl = videoLink?.attr("abs:href") ?: ""
            if (videoUrl.isEmpty()) return null

            val thumbnail = item.selectFirst("img.main-thumb")?.attr("abs:src") ?: ""
            val duration = item.selectFirst(".duration")?.text()?.trim() ?: ""

            val statsContainer = item.selectFirst(".stats-container")
            val likeRate = statsContainer?.selectFirst(".stat-item")?.text()?.trim()?.let { cleanLikeRate(it) } ?: ""
            val viewCount = statsContainer?.select("div.stat-item")?.getOrNull(1)?.text() ?: ""

            val title = item.selectFirst(".title")?.text()?.trim() ?: ""
            val author = item.selectFirst(".subtitle a")?.text()?.trim() ?: ""
            val subtitleTime = item.selectFirst(".subtitle-time")?.text()?.trim() ?: ""
            val publishTime = subtitleTime.replace("•", "").trim()

            val videoId = extractVideoId(videoUrl)

            return HanimeVideo(
                id = videoId,
                title = title,
                thumbnailUrl = thumbnail,
                duration = duration,
                likeRate = likeRate,
                viewCount = viewCount,
                author = author,
                publishTime = publishTime,
                videoUrl = videoUrl
            )
        } catch (e: Exception) {
            AppLogger.logError("HanimeParser", "Error parsing video item: ${e.message}", e)
            return null
        }
    }

    private fun parseSubscriberStats(stats: String): Pair<String, String> {
        AppLogger.log("HanimeParser", "parseSubscriberStats input: '$stats'")

        // 尝试匹配订阅者数 - 支持繁简体和多种空格格式
        // 繁体：位訂閱者，简体：位订阅者
        val subscriberPatterns = listOf(
            Regex("(\\d[\\d,]*)\\s+位訂閱者"),
            Regex("(\\d[\\d,]*)\\s+位订阅者"),
            Regex("(\\d[\\d,]*)\\s*位訂閱者"),
            Regex("(\\d[\\d,]*)\\s*位订阅者"),
            Regex("(\\d[\\d,]*)位訂閱者"),
            Regex("(\\d[\\d,]*)位订阅者")
        )
        var subscriberCount = ""
        for (pattern in subscriberPatterns) {
            val match = pattern.find(stats)
            if (match != null) {
                subscriberCount = match.groupValues[1]
                AppLogger.log("HanimeParser", "Matched subscriber with pattern: $pattern")
                break
            }
        }

        // 尝试匹配视频数 - 支持繁简体和多种空格格式
        // 繁体：部影片，简体：个视频
        val videoPatterns = listOf(
            Regex("(\\d[\\d,]*)\\s+部影片"),
            Regex("(\\d[\\d,]*)\\s+个视频"),
            Regex("(\\d[\\d,]*)\\s*部影片"),
            Regex("(\\d[\\d,]*)\\s*个视频"),
            Regex("(\\d[\\d,]*)部影片"),
            Regex("(\\d[\\d,]*)个视频")
        )
        var videoCount = ""
        for (pattern in videoPatterns) {
            val match = pattern.find(stats)
            if (match != null) {
                videoCount = match.groupValues[1]
                AppLogger.log("HanimeParser", "Matched video count with pattern: $pattern")
                break
            }
        }

        AppLogger.log("HanimeParser", "parseSubscriberStats result: sub='$subscriberCount', video='$videoCount'")
        return Pair(subscriberCount, videoCount)
    }

    private fun parseAuthorVideos(doc: Document, baseUrl: String): List<HanimeVideo> {
        val videos = mutableListOf<HanimeVideo>()
        val videoItems = doc.select(".video-item-container")
        for (item in videoItems) {
            val video = parseVideoItem(item, baseUrl) ?: continue
            videos.add(video)
        }
        return videos
    }

    fun parseUserVideoList(html: String, baseUrl: String): app.amisles.hanime.domain.model.UserVideoListResult {
        AppLogger.log("HanimeParser", "parseUserVideoList called")
        val doc: Document = Jsoup.parse(html, baseUrl)

        val authorName = doc.selectFirst(".profile-display-name")?.text()?.trim() ?: ""
        val authorIdMatch = Regex("user/(\\d+)").find(baseUrl)
        val authorId = authorIdMatch?.groupValues?.get(1) ?: ""

        val videos = mutableListOf<HanimeVideo>()

        val userTabItems = doc.select(".user-tab-item-wrapper")
        AppLogger.log("HanimeParser", "Found ${userTabItems.size} user-tab-item-wrapper elements")

        for (item in userTabItems) {
            val video = parseUserTabVideoItem(item, baseUrl)
            if (video != null) {
                videos.add(video)
            }
        }

        if (videos.isEmpty()) {
            val videoContainers = doc.select(".video-item-container")
            AppLogger.log("HanimeParser", "Fallback: Found ${videoContainers.size} video-item-container elements")
            for (container in videoContainers) {
                val video = parseVideoItem(container, baseUrl)
                if (video != null) {
                    videos.add(video)
                }
            }
        }

        val (currentPage, totalPages, hasNextPage) = parsePagination(doc)

        AppLogger.log("HanimeParser", "Parsed user video list: ${videos.size} videos, page $currentPage/$totalPages, hasNext=$hasNextPage")

        return app.amisles.hanime.domain.model.UserVideoListResult(
            videos = videos,
            currentPage = currentPage,
            totalPages = totalPages,
            hasNextPage = hasNextPage,
            authorName = authorName,
            authorId = authorId
        )
    }

    private fun parseUserTabVideoItem(item: Element, baseUrl: String): HanimeVideo? {
        try {
            val videoLink = item.selectFirst("a[href*=\"watch?v=\"]")
            val videoUrl = videoLink?.attr("abs:href") ?: return null

            val thumbnail = item.selectFirst("img.main-thumb")?.attr("abs:src") ?: ""
            val duration = item.selectFirst(".duration")?.text()?.trim() ?: ""

            val title = item.selectFirst(".title")?.text()?.trim() ?: ""

            val videoId = extractVideoId(videoUrl)

            return HanimeVideo(
                id = videoId,
                title = title,
                thumbnailUrl = thumbnail,
                duration = duration,
                likeRate = "",
                viewCount = "",
                author = "",
                publishTime = "",
                videoUrl = videoUrl
            )
        } catch (e: Exception) {
            AppLogger.logError("HanimeParser", "Error parsing user tab video item: ${e.message}", e)
            return null
        }
    }
}