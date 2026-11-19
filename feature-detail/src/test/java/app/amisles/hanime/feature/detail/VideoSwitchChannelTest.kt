package app.amisles.hanime.feature.detail

import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoSwitchChannelTest {

    @Test
    fun lateSubscriberStillSeesTheCurrentVideo() {
        val channel = VideoSwitchChannel()
        channel.switchTo("video-42")

        assertEquals("video-42", channel.videoId.value)
    }

    @Test
    fun switchingToTheSameVideoKeepsTheValue() {
        val channel = VideoSwitchChannel()
        channel.switchTo("video-1")
        channel.switchTo("video-1")

        assertEquals("video-1", channel.videoId.value)
    }

    @Test
    fun videoIdIsExposedAsStateFlow() {
        val channel = VideoSwitchChannel()
        val exposed: StateFlow<String> = channel.videoId

        assertEquals("", exposed.value)
    }
}
