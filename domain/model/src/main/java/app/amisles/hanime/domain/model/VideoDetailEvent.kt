package app.amisles.hanime.domain.model

/**
 * 视频详情页流式解析事件。
 * 解析器按「主信息 → 播放列表 → 相关视频」的顺序逐块 emit，
 * 使播放器区不必等待相关视频/播放列表解析完成即可先渲染。
 */
sealed interface VideoDetailEvent {
    /** 主信息就绪（标题/播放器/封面/标签/作者/订阅态/CSRF 等），相关视频与播放列表暂为空。 */
    data class MainInfo(val detail: VideoDetail) : VideoDetailEvent

    /** 相关视频列表解析完成。 */
    data class RelatedVideos(val videos: List<HanimeVideo>) : VideoDetailEvent

    /** 播放列表解析完成（可能为 null）。 */
    data class Playlist(val playlist: PlaylistInfo?) : VideoDetailEvent

    /** 解析/网络失败。 */
    data class Error(val message: String) : VideoDetailEvent
}
