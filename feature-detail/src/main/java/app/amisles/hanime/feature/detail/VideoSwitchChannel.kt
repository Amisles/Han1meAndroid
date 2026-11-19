package app.amisles.hanime.feature.detail

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class VideoSwitchChannel {

    private val state = MutableStateFlow("")
    val videoId: StateFlow<String> = state.asStateFlow()

    fun switchTo(videoId: String) {
        state.value = videoId
    }
}
