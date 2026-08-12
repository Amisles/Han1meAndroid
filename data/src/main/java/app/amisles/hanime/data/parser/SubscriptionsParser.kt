package app.amisles.hanime.data.parser

import android.util.Log
import app.amisles.hanime.domain.model.SubscribedArtist
import app.amisles.hanime.domain.model.SubscriptionsContent
import app.amisles.hanime.core.common.util.AppLogger
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 订阅内容页解析器
 *
 * 页面结构（来自官网 https://hanimeone.me/subscriptions 或 ?query=<作者名>）：
 *  - 顶部两份横向滚动的已订阅作者条（移动端导航条 + 桌面端筛选条），二者内容相同，
 *    每张作者卡片 class 含 `subscriptions-artist-card artist-option`，当前选中的额外带
 *    `subscriptions-active-artist`（仅 ?query= 视图出现）。解析时按作者名去重（LinkedHashSet 保序）。
 *    头像：卡片内两张 img，第一张是 `card_artist_background.jpg`（背景，非头像），
 *    第二张带 `position: absolute` 的才是作者头像；优先取该张，缺失时回退卡片内最后一张 img。
 *    名称取 `.card-mobile-title.search-artist-title`。
 *  - 主体视频网格：与首页/搜索一致，使用 `.video-item-container`，直接复用 [VideoListParser]。
 */
@Singleton
class SubscriptionsParser @Inject constructor(
    private val videoListParser: VideoListParser
) {

    fun parse(html: String, baseUrl: String): SubscriptionsContent {
        val doc: Document = Jsoup.parse(html, baseUrl)
        val artists = parseArtists(doc)
        val videos = videoListParser.parseVideoList(doc, baseUrl)
        Log.i("SubscriptionsDebug", "<<< Parsed ${artists.size} subscribed artists (active=${artists.count { it.isActive }}), ${videos.size} videos")
        return SubscriptionsContent(artists = artists, videos = videos)
    }

    private fun parseArtists(doc: Document): List<SubscribedArtist> {
        val cards = doc.select(".subscriptions-artist-card.artist-option")
        if (cards.isEmpty()) {
            AppLogger.log("SubscriptionsParser", "No subscribed-artist cards found")
            return emptyList()
        }

        val activeNames = mutableSetOf<String>()
        val seenNames = LinkedHashSet<String>() // 保序去重：页面存在移动端/桌面端两份相同作者条
        val builder = mutableListOf<SubscribedArtist>()

        for (card in cards) {
            val name = card.selectFirst(".card-mobile-title.search-artist-title")?.text()?.trim()
                ?: card.selectFirst(".search-artist-title")?.text()?.trim()
                ?: continue
            if (!seenNames.add(name)) continue // 已收录，跳过重复条

            if (card.classNames().contains("subscriptions-active-artist")) {
                activeNames.add(name)
            }

            // 头像：优先取绝对定位覆盖在头像卡片上的那张（作者头像），否则回退卡片内最后一张 img
            val avatarUrl = card.selectFirst("img[style*=absolute]")?.attr("abs:src")
                ?: card.select("img").lastOrNull()?.attr("abs:src")
                ?: ""
            builder.add(SubscribedArtist(name = name, avatarUrl = avatarUrl, isActive = false))
        }

        // 回填 active 标记（来自 subscriptions-active-artist 类）
        val result = builder.map { artist ->
            if (artist.name in activeNames) artist.copy(isActive = true) else artist
        }
        AppLogger.log("SubscriptionsParser", "Parsed ${result.size} subscribed artists (active=${activeNames.size})")
        return result
    }
}
