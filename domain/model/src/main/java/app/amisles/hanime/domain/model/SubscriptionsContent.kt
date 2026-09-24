package app.amisles.hanime.domain.model

/** 订阅内容页：已订阅作者列表 + 当前筛选下的视频列表。 */
data class SubscriptionsContent(
    val artists: List<SubscribedArtist> = emptyList(),
    val videos: List<HanimeVideo> = emptyList()
)

/** 已订阅作者（订阅内容页顶部横向列表项）。 */
data class SubscribedArtist(
    val name: String = "",
    val avatarUrl: String = "",
    val isActive: Boolean = false
)
