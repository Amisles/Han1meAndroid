package app.amisles.hanime.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoDetailTest {

    @Test
    fun `test VideoSource creation`() {
        val source = VideoSource(
            url = "https://example.com/video.m3u8",
            resolution = "1080p",
            size = 1024
        )

        assertEquals("https://example.com/video.m3u8", source.url)
        assertEquals("1080p", source.resolution)
        assertEquals(1024, source.size)
    }

    @Test
    fun `test PlaylistInfo creation`() {
        val videos = listOf(
            HanimeVideo(
                id = "1",
                title = "Video 1",
                thumbnailUrl = "",
                duration = "5:00",
                likeRate = "90%",
                viewCount = "1K",
                author = "Author",
                publishTime = "2024-01-01",
                videoUrl = "url1"
            )
        )

        val playlist = PlaylistInfo(
            title = "Test Playlist",
            author = "Playlist Author",
            videoCount = 1,
            videos = videos
        )

        assertEquals("Test Playlist", playlist.title)
        assertEquals("Playlist Author", playlist.author)
        assertEquals(1, playlist.videoCount)
        assertEquals(1, playlist.videos.size)
    }

    @Test
    fun `test VideoDetail creation`() {
        val sources = listOf(
            VideoSource(
                url = "https://example.com/video.m3u8",
                resolution = "1080p",
                size = 1024
            )
        )

        val detail = VideoDetail(
            title = "Test Video",
            posterUrl = "https://example.com/poster.jpg",
            videoSources = sources,
            defaultSourceUrl = "https://example.com/video.m3u8",
            tags = listOf("tag1", "tag2"),
            releaseDate = "2024-01-01",
            fileSize = "1GB",
            author = "Test Author",
            authorAvatarUrl = "https://example.com/avatar.jpg",
            authorPageUrl = "https://example.com/author",
            description = "Test description"
        )

        assertEquals("Test Video", detail.title)
        assertEquals("https://example.com/poster.jpg", detail.posterUrl)
        assertEquals(1, detail.videoSources.size)
        assertEquals("https://example.com/video.m3u8", detail.defaultSourceUrl)
        assertEquals(2, detail.tags.size)
        assertEquals("2024-01-01", detail.releaseDate)
        assertEquals("1GB", detail.fileSize)
        assertEquals("Test Author", detail.author)
        assertEquals("Test description", detail.description)
        assertEquals(emptyList<HanimeVideo>(), detail.relatedVideos)
        assertNull(detail.playlist)
    }

    @Test
    fun `test VideoDetail with optional fields`() {
        val detail = VideoDetail(
            title = "Test Video",
            posterUrl = "https://example.com/poster.jpg",
            videoSources = emptyList(),
            defaultSourceUrl = "",
            tags = emptyList(),
            releaseDate = "",
            fileSize = "",
            author = "",
            description = ""
        )

        assertEquals("", detail.authorAvatarUrl)
        assertEquals("", detail.authorPageUrl)
        assertEquals(emptyList<HanimeVideo>(), detail.relatedVideos)
        assertNull(detail.playlist)
    }
}