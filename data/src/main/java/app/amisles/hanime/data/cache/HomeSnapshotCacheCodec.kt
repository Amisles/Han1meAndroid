package app.amisles.hanime.data.cache

import app.amisles.hanime.domain.model.HanimeBanner
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.HomeSection
import org.json.JSONArray
import org.json.JSONObject

data class HomeSnapshot(
    val siteUrl: String,
    val banner: HanimeBanner?,
    val sections: List<HomeSection>
)

internal object HomeSnapshotCacheCodec {

    fun encode(siteUrl: String, banner: HanimeBanner?, sections: List<HomeSection>): String {
        val bannerJson = banner?.let {
            JSONObject()
                .put("title", it.title)
                .put("author", it.author)
                .put("viewCount", it.viewCount)
                .put("publishTime", it.publishTime)
                .put("tags", JSONArray(it.tags))
                .put("videoUrl", it.videoUrl)
                .put("imageUrl", it.imageUrl)
        }
        val sectionsJson = JSONArray()
        sections.forEach { section ->
            val videosJson = JSONArray()
            section.videos.forEach { video ->
                videosJson.put(
                    JSONObject()
                        .put("id", video.id)
                        .put("title", video.title)
                        .put("thumbnailUrl", video.thumbnailUrl)
                        .put("duration", video.duration)
                        .put("likeRate", video.likeRate)
                        .put("viewCount", video.viewCount)
                        .put("author", video.author)
                        .put("publishTime", video.publishTime)
                        .put("videoUrl", video.videoUrl)
                        .put("isPortraitCover", video.isPortraitCover)
                )
            }
            sectionsJson.put(
                JSONObject()
                    .put("title", section.title)
                    .put("moreUrl", section.moreUrl)
                    .put("videos", videosJson)
            )
        }
        return JSONObject()
            .put("siteUrl", siteUrl)
            .put("banner", bannerJson ?: JSONObject.NULL)
            .put("sections", sectionsJson)
            .toString()
    }

    fun decode(raw: String): HomeSnapshot? = runCatching {
        val json = JSONObject(raw)
        val bannerJson = json.optJSONObject("banner")
        val banner = bannerJson?.let {
            val tagsJson = it.optJSONArray("tags")
            HanimeBanner(
                title = it.optString("title"),
                author = it.optString("author"),
                viewCount = it.optString("viewCount"),
                publishTime = it.optString("publishTime"),
                tags = List(tagsJson?.length() ?: 0) { index ->
                    tagsJson?.optString(index).orEmpty()
                },
                videoUrl = it.optString("videoUrl"),
                imageUrl = it.optString("imageUrl")
            )
        }
        val sectionsJson = json.optJSONArray("sections")
        val sections = buildList {
            for (sectionIndex in 0 until (sectionsJson?.length() ?: 0)) {
                val sectionJson = sectionsJson?.optJSONObject(sectionIndex) ?: continue
                val videosJson = sectionJson.optJSONArray("videos")
                val videos = buildList {
                    for (videoIndex in 0 until (videosJson?.length() ?: 0)) {
                        val videoJson = videosJson?.optJSONObject(videoIndex) ?: continue
                        add(
                            HanimeVideo(
                                id = videoJson.optString("id"),
                                title = videoJson.optString("title"),
                                thumbnailUrl = videoJson.optString("thumbnailUrl"),
                                duration = videoJson.optString("duration"),
                                likeRate = videoJson.optString("likeRate"),
                                viewCount = videoJson.optString("viewCount"),
                                author = videoJson.optString("author"),
                                publishTime = videoJson.optString("publishTime"),
                                videoUrl = videoJson.optString("videoUrl"),
                                isPortraitCover = videoJson.optBoolean("isPortraitCover")
                            )
                        )
                    }
                }
                add(
                    HomeSection(
                        title = sectionJson.optString("title"),
                        moreUrl = sectionJson.optString("moreUrl"),
                        videos = videos
                    )
                )
            }
        }
        HomeSnapshot(siteUrl = json.optString("siteUrl"), banner = banner, sections = sections)
    }.getOrNull()
}
