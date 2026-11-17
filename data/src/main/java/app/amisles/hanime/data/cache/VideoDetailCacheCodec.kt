package app.amisles.hanime.data.cache

import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.PlaylistInfo
import app.amisles.hanime.domain.model.PlaylistSaveOption
import app.amisles.hanime.domain.model.VideoDetail
import app.amisles.hanime.domain.model.VideoSaveOptions
import app.amisles.hanime.domain.model.VideoSource
import org.json.JSONArray
import org.json.JSONObject

/** 一条详情缓存：所属键 + 落盘时间 + 详情本体。 */
internal data class VideoDetailCacheEntry(
    val key: String,
    val savedAt: Long,
    val detail: VideoDetail
)

/**
 * 详情缓存条目的编解码。
 *
 * 缓存读的是磁盘上的旧文件，字段只增不减：解码一律走 opt* 并在缺字段时回退空值，
 * 一次结构变化不能让整条缓存变成「读不出来」再被当成未命中。
 */
internal object VideoDetailCacheCodec {

    private const val VERSION = 1

    fun encodeEntry(
        key: String,
        detail: VideoDetail,
        savedAt: Long = System.currentTimeMillis()
    ): String = JSONObject()
        .put("v", VERSION)
        .put("key", key)
        .put("savedAt", savedAt)
        .put("detail", encodeDetail(detail))
        .toString()

    fun decodeEntry(raw: String): VideoDetailCacheEntry? = runCatching {
        val json = JSONObject(raw)
        if (json.optInt("v") != VERSION) return null
        val detailJson = json.optJSONObject("detail") ?: return null
        VideoDetailCacheEntry(
            key = json.optString("key"),
            savedAt = json.optLong("savedAt"),
            detail = decodeDetail(detailJson)
        )
    }.getOrNull()

    private fun encodeDetail(detail: VideoDetail): JSONObject = JSONObject()
        .put("title", detail.title)
        .put("posterUrl", detail.posterUrl)
        .put("defaultSourceUrl", detail.defaultSourceUrl)
        .put("tags", JSONArray(detail.tags))
        .put("releaseDate", detail.releaseDate)
        .put("fileSize", detail.fileSize)
        .put("author", detail.author)
        .put("authorAvatarUrl", detail.authorAvatarUrl)
        .put("authorPageUrl", detail.authorPageUrl)
        .put("description", detail.description)
        .put("videoSources", JSONArray().apply { detail.videoSources.forEach { put(encodeSource(it)) } })
        .put("relatedVideos", encodeVideos(detail.relatedVideos))
        .put("playlist", detail.playlist?.let { encodePlaylist(it) } ?: JSONObject.NULL)
        .put("csrfToken", detail.csrfToken)
        .put("commentCount", detail.commentCount)
        .put("currentUserId", detail.currentUserId)
        .put("subscribeArtistId", detail.subscribeArtistId)
        .put("subscribeUserId", detail.subscribeUserId)
        .put("subscribeStatus", detail.subscribeStatus)
        .put("saveOptions", encodeSaveOptions(detail.saveOptions))

    private fun decodeDetail(json: JSONObject): VideoDetail = VideoDetail(
        title = json.optString("title"),
        posterUrl = json.optString("posterUrl"),
        videoSources = decodeArray(json.optJSONArray("videoSources")) { decodeSource(it) },
        defaultSourceUrl = json.optString("defaultSourceUrl"),
        tags = decodeStrings(json.optJSONArray("tags")),
        releaseDate = json.optString("releaseDate"),
        fileSize = json.optString("fileSize"),
        author = json.optString("author"),
        authorAvatarUrl = json.optString("authorAvatarUrl"),
        authorPageUrl = json.optString("authorPageUrl"),
        description = json.optString("description"),
        relatedVideos = decodeArray(json.optJSONArray("relatedVideos")) { decodeVideo(it) },
        playlist = json.optJSONObject("playlist")?.let { decodePlaylist(it) },
        csrfToken = json.optString("csrfToken"),
        commentCount = json.optInt("commentCount"),
        currentUserId = json.optString("currentUserId"),
        subscribeArtistId = json.optString("subscribeArtistId"),
        subscribeUserId = json.optString("subscribeUserId"),
        subscribeStatus = json.optString("subscribeStatus"),
        saveOptions = json.optJSONObject("saveOptions")?.let { decodeSaveOptions(it) }
            ?: VideoSaveOptions()
    )

    private fun encodeSource(source: VideoSource): JSONObject = JSONObject()
        .put("url", source.url)
        .put("resolution", source.resolution)
        .put("size", source.size)
        .put("mimeType", source.mimeType)

    private fun decodeSource(json: JSONObject): VideoSource = VideoSource(
        url = json.optString("url"),
        resolution = json.optString("resolution"),
        size = json.optInt("size"),
        mimeType = json.optString("mimeType")
    )

    private fun encodeVideos(videos: List<HanimeVideo>): JSONArray = JSONArray().apply {
        videos.forEach { video ->
            put(
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
    }

    private fun decodeVideo(json: JSONObject): HanimeVideo = HanimeVideo(
        id = json.optString("id"),
        title = json.optString("title"),
        thumbnailUrl = json.optString("thumbnailUrl"),
        duration = json.optString("duration"),
        likeRate = json.optString("likeRate"),
        viewCount = json.optString("viewCount"),
        author = json.optString("author"),
        publishTime = json.optString("publishTime"),
        videoUrl = json.optString("videoUrl"),
        isPortraitCover = json.optBoolean("isPortraitCover")
    )

    private fun encodePlaylist(playlist: PlaylistInfo): JSONObject = JSONObject()
        .put("title", playlist.title)
        .put("author", playlist.author)
        .put("videoCount", playlist.videoCount)
        .put("videos", encodeVideos(playlist.videos))

    private fun decodePlaylist(json: JSONObject): PlaylistInfo = PlaylistInfo(
        title = json.optString("title"),
        author = json.optString("author"),
        videoCount = json.optInt("videoCount"),
        videos = decodeArray(json.optJSONArray("videos")) { decodeVideo(it) }
    )

    private fun encodeSaveOptions(options: VideoSaveOptions): JSONObject = JSONObject()
        .put("videoId", options.videoId)
        .put("csrfToken", options.csrfToken)
        .put(
            "options",
            JSONArray().apply {
                options.options.forEach { option ->
                    put(
                        JSONObject()
                            .put("target", option.target)
                            .put("title", option.title)
                            .put("isPrivate", option.isPrivate)
                            .put("selected", option.selected)
                    )
                }
            }
        )

    private fun decodeSaveOptions(json: JSONObject): VideoSaveOptions = VideoSaveOptions(
        videoId = json.optString("videoId"),
        csrfToken = json.optString("csrfToken"),
        options = decodeArray(json.optJSONArray("options")) { option ->
            PlaylistSaveOption(
                target = option.optString("target"),
                title = option.optString("title"),
                isPrivate = option.optBoolean("isPrivate"),
                selected = option.optBoolean("selected")
            )
        }
    )

    private fun decodeStrings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                add(array.optString(index))
            }
        }
    }

    private fun <T> decodeArray(array: JSONArray?, mapper: (JSONObject) -> T): List<T> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let { add(mapper(it)) }
            }
        }
    }
}
