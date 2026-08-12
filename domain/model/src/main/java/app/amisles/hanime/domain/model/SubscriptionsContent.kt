package app.amisles.hanime.domain.model

/**
 * 订阅内容页：已订阅作者列表 + 当前筛选下的视频列表。
 */
data class SubscriptionsContent(
    val artists: List<SubscribedArtist> = emptyList(),
    val videos: List<HanimeVideo> = emptyList()
)

/**
 * 已订阅作者（订阅内容页顶部横向列表项）。
 *
 * @param name      作者名（同时作为切换筛选时的 query 参数）
 * @param avatarUrl 作者头像 URL（优先取覆盖在头像卡片上的绝对定位图）
 * @param isActive  是否为当前页面选中的作者（官网用 .subscriptions-active-artist 标记）
 */
data class SubscribedArtist(
    val name: String = "",
    val avatarUrl: String = "",
    val isActive: Boolean = false
)
