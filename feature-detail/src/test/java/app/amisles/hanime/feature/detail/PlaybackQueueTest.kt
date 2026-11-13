package app.amisles.hanime.feature.detail

import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.PlaylistInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackQueueTest {

    private fun video(id: String, url: String = "https://hanime1.me/watch?v=$id") = HanimeVideo(
        id = id,
        title = "v$id",
        thumbnailUrl = "",
        duration = "",
        likeRate = "",
        viewCount = "",
        author = "",
        publishTime = "",
        videoUrl = url
    )

    private fun playlist(vararg videos: HanimeVideo) = PlaylistInfo(
        title = "p",
        author = "",
        videoCount = videos.size,
        videos = videos.toList()
    )

    @Test
    fun `returns next item inside playlist`() {
        val second = video("2")
        val next = nextEpisodeOf(
            playlist = playlist(video("1"), second, video("3")),
            relatedVideos = listOf(video("9")),
            currentVideoId = "2",
            currentVideoUrl = second.videoUrl,
            allowRelated = false
        )
        assertEquals("3", next?.video?.id)
        assertEquals(NextEpisodeSource.PLAYLIST, next?.source)
    }

    @Test
    fun `returns null at the end of the playlist when related fallback is off`() {
        val last = video("3")
        val next = nextEpisodeOf(
            playlist = playlist(video("1"), video("2"), last),
            relatedVideos = listOf(video("9")),
            currentVideoId = "3",
            currentVideoUrl = last.videoUrl,
            allowRelated = false
        )
        assertNull(next)
    }

    @Test
    fun `falls back to related videos at the end of the playlist when allowed`() {
        val last = video("3")
        val next = nextEpisodeOf(
            playlist = playlist(video("1"), video("2"), last),
            relatedVideos = listOf(video("9")),
            currentVideoId = "3",
            currentVideoUrl = last.videoUrl,
            allowRelated = true
        )
        assertEquals("9", next?.video?.id)
        assertEquals(NextEpisodeSource.RELATED, next?.source)
    }

    @Test
    fun `matches by url when ids differ`() {
        val second = video("2", "https://hanime1.me/watch?v=2")
        val next = nextEpisodeOf(
            playlist = playlist(video("1"), second, video("3")),
            relatedVideos = emptyList(),
            currentVideoId = "",
            currentVideoUrl = "https://hanime1.me/watch?v=2",
            allowRelated = true
        )
        assertEquals("3", next?.video?.id)
    }

    @Test
    fun `returns null without playlist when related fallback is off`() {
        val next = nextEpisodeOf(
            playlist = null,
            relatedVideos = listOf(video("8")),
            currentVideoId = "1",
            currentVideoUrl = "https://hanime1.me/watch?v=1",
            allowRelated = false
        )
        assertNull(next)
    }

    @Test
    fun `falls back to related videos without playlist when allowed`() {
        val next = nextEpisodeOf(
            playlist = null,
            relatedVideos = listOf(video("9", ""), video("8")),
            currentVideoId = "1",
            currentVideoUrl = "https://hanime1.me/watch?v=1",
            allowRelated = true
        )
        assertEquals("8", next?.video?.id)
        assertEquals(NextEpisodeSource.RELATED, next?.source)
    }

    @Test
    fun `falls back to related videos when current is absent from playlist`() {
        val next = nextEpisodeOf(
            playlist = playlist(video("1"), video("2")),
            relatedVideos = listOf(video("8")),
            currentVideoId = "7",
            currentVideoUrl = "https://hanime1.me/watch?v=7",
            allowRelated = true
        )
        assertEquals("8", next?.video?.id)
        assertEquals(NextEpisodeSource.RELATED, next?.source)
    }

    @Test
    fun `returns null when nothing is playable`() {
        val next = nextEpisodeOf(
            playlist = null,
            relatedVideos = listOf(video("", "")),
            currentVideoId = "1",
            currentVideoUrl = "https://hanime1.me/watch?v=1",
            allowRelated = true
        )
        assertNull(next)
    }
}
