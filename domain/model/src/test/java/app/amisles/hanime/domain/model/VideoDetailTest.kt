package app.amisles.hanime.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoDetailTest {

    @Test
    fun optionalFieldsDefaultToEmpty() {
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

    @Test
    fun normalizedMimeTypeNormalizesHlsVariants() {
        assertEquals("application/x-mpegURL", source("application/x-mpegurl").normalizedMimeType)
        assertEquals("application/x-mpegURL", source("application/x-MPEGURL").normalizedMimeType)
        assertEquals("application/x-mpegURL", source("application/vnd.apple.mpegurl").normalizedMimeType)
    }

    @Test
    fun normalizedMimeTypeRecognizesMp4WithCodecParameters() {
        assertEquals("video/mp4", source("video/mp4; codecs=\"avc1.42E01E\"").normalizedMimeType)
        assertEquals("video/mp4", source("VIDEO/MP4").normalizedMimeType)
    }

    @Test
    fun normalizedMimeTypeIsEmptyForBlankOrUnknownTypes() {
        assertEquals("", source("").normalizedMimeType)
        assertEquals("", source("audio/aac").normalizedMimeType)
    }

    private fun source(mimeType: String) = VideoSource(
        url = "https://example.com/video.mp4",
        resolution = "1080p",
        size = 1024,
        mimeType = mimeType
    )
}
