package app.amisles.hanime.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HanimeVideoTest {

    @Test
    fun `test HanimeVideo data class creation`() {
        val video = HanimeVideo(
            id = "123",
            title = "Test Video",
            thumbnailUrl = "https://example.com/thumb.jpg",
            duration = "10:30",
            likeRate = "95%",
            viewCount = "10K",
            author = "Test Author",
            publishTime = "2024-01-01",
            videoUrl = "https://example.com/video"
        )

        assertEquals("123", video.id)
        assertEquals("Test Video", video.title)
        assertEquals("https://example.com/thumb.jpg", video.thumbnailUrl)
        assertEquals("10:30", video.duration)
        assertEquals("95%", video.likeRate)
        assertEquals("10K", video.viewCount)
        assertEquals("Test Author", video.author)
        assertEquals("2024-01-01", video.publishTime)
        assertEquals("https://example.com/video", video.videoUrl)
    }

    @Test
    fun `test HanimeVideo equality`() {
        val video1 = HanimeVideo(
            id = "123",
            title = "Test Video",
            thumbnailUrl = "https://example.com/thumb.jpg",
            duration = "10:30",
            likeRate = "95%",
            viewCount = "10K",
            author = "Test Author",
            publishTime = "2024-01-01",
            videoUrl = "https://example.com/video"
        )

        val video2 = HanimeVideo(
            id = "123",
            title = "Test Video",
            thumbnailUrl = "https://example.com/thumb.jpg",
            duration = "10:30",
            likeRate = "95%",
            viewCount = "10K",
            author = "Test Author",
            publishTime = "2024-01-01",
            videoUrl = "https://example.com/video"
        )

        assertEquals(video1, video2)
    }

    @Test
    fun `test HanimeVideo copy`() {
        val video = HanimeVideo(
            id = "123",
            title = "Test Video",
            thumbnailUrl = "https://example.com/thumb.jpg",
            duration = "10:30",
            likeRate = "95%",
            viewCount = "10K",
            author = "Test Author",
            publishTime = "2024-01-01",
            videoUrl = "https://example.com/video"
        )

        val copiedVideo = video.copy(title = "Updated Title")

        assertEquals("Updated Title", copiedVideo.title)
        assertEquals(video.id, copiedVideo.id)
        assertEquals(video.thumbnailUrl, copiedVideo.thumbnailUrl)
    }
}