package app.amisles.hanime.data.cache

import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.PlaylistInfo
import app.amisles.hanime.domain.model.PlaylistSaveOption
import app.amisles.hanime.domain.model.VideoDetail
import app.amisles.hanime.domain.model.VideoSaveOptions
import app.amisles.hanime.domain.model.VideoSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoDetailCacheCodecTest {

    private val related = HanimeVideo(
        id = "42",
        title = "相关视频",
        thumbnailUrl = "https://cdn.example.com/42.jpg",
        duration = "12:34",
        likeRate = "95%",
        viewCount = "1.2万",
        author = "作者",
        publishTime = "2026-11-12",
        videoUrl = "https://hanime1.me/watch?v=42",
        isPortraitCover = true
    )

    private val detail = VideoDetail(
        title = "标题",
        posterUrl = "https://cdn.example.com/p.jpg",
        videoSources = listOf(
            VideoSource("https://cdn.example.com/720.mp4", "720p", 720, "video/mp4"),
            VideoSource("https://cdn.example.com/1080.mp4", "1080p", 1080, "application/x-mpegURL")
        ),
        defaultSourceUrl = "https://cdn.example.com/720.mp4",
        tags = listOf("里番", "原创"),
        releaseDate = "2026/11/10",
        fileSize = "320 MB",
        author = "作者",
        authorAvatarUrl = "https://cdn.example.com/a.jpg",
        authorPageUrl = "https://hanime1.me/user/7",
        description = "简介文本",
        relatedVideos = listOf(related),
        playlist = PlaylistInfo(
            title = "播放列表",
            author = "作者",
            videoCount = 1,
            videos = listOf(related)
        ),
        csrfToken = "token",
        commentCount = 12,
        currentUserId = "99",
        subscribeArtistId = "7",
        subscribeUserId = "99",
        subscribeStatus = "1",
        saveOptions = VideoSaveOptions(
            videoId = "42",
            csrfToken = "save-token",
            options = listOf(PlaylistSaveOption("save", "稍后观看", isPrivate = false, selected = true))
        ),
        subtitleLang = "zht"
    )

    @Test
    fun `round trip keeps every field`() {
        val raw = VideoDetailCacheCodec.encodeEntry("99|42", detail, savedAt = 123L)

        val entry = VideoDetailCacheCodec.decodeEntry(raw)

        assertEquals("99|42", entry?.key)
        assertEquals(123L, entry?.savedAt)
        assertEquals(detail, entry?.detail)
    }

    @Test
    fun `malformed payload decodes to null`() {
        assertNull(VideoDetailCacheCodec.decodeEntry(""))
        assertNull(VideoDetailCacheCodec.decodeEntry("not json"))
        assertNull(VideoDetailCacheCodec.decodeEntry("""{"v":1,"key":"a"}"""))
    }

    @Test
    fun `unknown version decodes to null`() {
        assertNull(VideoDetailCacheCodec.decodeEntry("""{"v":99,"key":"a","detail":{}}"""))
    }

    @Test
    fun `missing members fall back to empty values`() {
        val detail = VideoDetailCacheCodec.decodeEntry("""{"v":1,"key":"k","detail":{}}""")?.detail

        assertEquals("", detail?.title)
        assertEquals(emptyList<VideoSource>(), detail?.videoSources)
        assertEquals(emptyList<HanimeVideo>(), detail?.relatedVideos)
        assertNull(detail?.playlist)
        assertEquals(VideoSaveOptions(), detail?.saveOptions)
        assertEquals("", detail?.subtitleLang)
    }
}
